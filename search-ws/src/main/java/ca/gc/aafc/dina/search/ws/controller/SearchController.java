package ca.gc.aafc.dina.search.ws.controller;

import ca.gc.aafc.dina.security.DinaAuthenticatedUser;
import ca.gc.aafc.dina.security.TextHtmlSanitizer;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.SearchService;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;


@Log4j2
@RestController
@RequestMapping(value = "/search-ws", produces = "application/json")
public class SearchController {

  private static final Pattern ALPHA_NUM_PATTERN = Pattern.compile("^[-.,\\w]*$");

  private final SearchService searchService;

  /**
   * dina-base-api's {@code currentUser()} bean (from {@code KeycloakSecurityConfig}) only exists
   * when {@code keycloak.enabled=true} - with Keycloak disabled (local dev without auth, some
   * tests), there's no such bean at all. {@link ObjectProvider} defers the lookup to request
   * time and tolerates that absence instead of failing application startup, which a direct
   * {@code DinaAuthenticatedUser} constructor/field injection would not.
   */
  private final ObjectProvider<DinaAuthenticatedUser> currentUserProvider;

  public SearchController(@Autowired SearchService searchService,
                          ObjectProvider<DinaAuthenticatedUser> currentUserProvider) {
    this.searchService = searchService;
    this.currentUserProvider = currentUserProvider;
  }

  @GetMapping(path = "/auto-complete")
  public ResponseEntity<?> autocomplete(@RequestParam String prefix, @RequestParam String indexName,
      @RequestParam String autoCompleteField,
      @RequestParam(required = false) String additionalField,
      @RequestParam(required = false) String group,
      @RequestParam(required = false) String restrictedField,
      @RequestParam(required = false) String restrictedFieldValue) {

    log.info(
        "prefix={}, indexName={}, autoCompleteField={}, additionalField={}, restrictedField={}, restrictedFieldValue={}",
        prefix, indexName, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);
    try {
      validateHtmlSafe(prefix);
      validateAlphanumericInputs(indexName, autoCompleteField, additionalField, group, restrictedField, restrictedFieldValue);
      return new ResponseEntity<>(searchService.autoComplete(prefix, indexName,
          autoCompleteField, additionalField, group, restrictedField, restrictedFieldValue), HttpStatus.OK);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @GetMapping(path = "/mapping")
  public ResponseEntity<?> mapping(@RequestParam String indexName) {
    try {
      validateAlphanumericInputs(indexName);
      log.info("indexName={}", indexName);
      return new ResponseEntity<>(searchService.getIndexMapping(indexName), HttpStatus.OK);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
    
  @PostMapping(path = "/search", consumes = "application/json")
  public ResponseEntity<String> search(@RequestBody String query, @RequestParam String indexName) {

    log.info("indexName={}, query={}", indexName, query);
    try {
      validateHtmlSafe(query);
      validateAlphanumericInputs(indexName);

      String[] indices = StringUtils.split(indexName, ',');
      return new ResponseEntity<>(
          searchService.search(Arrays.asList(indices), query, currentUserGroups()), HttpStatus.ACCEPTED);
    } catch (SearchApiException e) {
      log.error("SearchApiException cause {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping(path = "/count", consumes = "application/json")
  public ResponseEntity<?> count(@RequestBody String query, @RequestParam String indexName) {
    log.info("indexName={}, query={}", indexName, query);

    try {
      validateHtmlSafe(query);
      validateAlphanumericInputs(indexName);
      return new ResponseEntity<>(searchService.count(indexName, query, currentUserGroups()), HttpStatus.ACCEPTED);
    } catch (SearchApiException e) {
      log.error("SearchApiException cause {}", e.getCause().getMessage());
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Groups the currently authenticated caller belongs to, as already parsed by dina-base-api
   * (same {@code DinaAuthenticatedUser} every other DINA API authorizes against - see
   * {@code KeycloakSecurityConfig#currentUser()}), so this stays consistent with the rest of
   * DINA by construction rather than a convention re-implemented here.
   *
   * @return groups the caller belongs to; never null. Empty when there's no authenticated user
   *         (Keycloak disabled, or request genuinely unauthenticated) or they belong to no
   *         group - either way, searches against group-scoped indices will then match no
   *         document (fail closed).
   */
  private List<String> currentUserGroups() {
    DinaAuthenticatedUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getGroups() == null) {
      return List.of();
    }
    return List.copyOf(currentUser.getGroups());
  }

  private static void validateAlphanumericInputs(String ... inputs) throws SearchApiException {
    for (String input : inputs) {
      if (StringUtils.isNotBlank(input) && !ALPHA_NUM_PATTERN.matcher(input).matches()) {
        throw new SearchApiException("invalid input");
      }
    }
  }

  public static void validateHtmlSafe(String input) throws SearchApiException {
    if (!TextHtmlSanitizer.isSafeText(input, Safelist.basic(), false)) {
      throw new SearchApiException("invalid input");
    }
  }

}
