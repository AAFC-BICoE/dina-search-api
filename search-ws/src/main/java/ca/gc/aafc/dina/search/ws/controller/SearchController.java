package ca.gc.aafc.dina.search.ws.controller;

import ca.gc.aafc.dina.security.TextHtmlSanitizer;
import org.apache.commons.lang3.StringUtils;
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

import java.util.regex.Pattern;


@Log4j2
@RestController
@RequestMapping(value = "/search-ws", produces = "application/json")
public class SearchController {

  private static final Pattern ALPHA_NUM_PATTERN = Pattern.compile("^[-.\\w]*$");

  private final SearchService searchService;

  public SearchController(@Autowired SearchService searchService) {
    this.searchService = searchService;
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
      return new ResponseEntity<>(searchService.search(indexName, query), HttpStatus.ACCEPTED);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping(path = "/count", consumes = "application/json")
  public ResponseEntity<?> count(@RequestBody String query, @RequestParam String indexName) {
    log.info("indexName={}, query={}", indexName, query);
    try {
      validateHtmlSafe(query);
      validateAlphanumericInputs(indexName);
      return new ResponseEntity<>(searchService.count(indexName, query), HttpStatus.ACCEPTED);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  private static void validateAlphanumericInputs(String ... inputs) throws SearchApiException {
    for (String input : inputs) {
      if (StringUtils.isNotBlank(input) && !ALPHA_NUM_PATTERN.matcher(input).matches()) {
        throw new SearchApiException("invalid input");
      }
    }
  }

  public static void validateHtmlSafe(String input) throws SearchApiException {
    if (!TextHtmlSanitizer.isSafeText(input)) {
      throw new SearchApiException("invalid input");
    }
  }

}
