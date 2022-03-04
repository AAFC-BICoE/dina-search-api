package ca.gc.aafc.dina.search.ws.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.SearchService;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping(value = "/search-ws", produces = "application/json")
public class SearchController {
  
  private final SearchService searchService;
  
  public SearchController(@Autowired SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping(path = "/auto-complete")
  public ResponseEntity<?> autocomplete(@RequestParam String prefix, @RequestParam String indexName,
      @RequestParam String autoCompleteField, @RequestParam(required = false) String additionalField,
      @RequestParam(required = false) String restrictedField,
      @RequestParam(required = false) String restrictedFieldValue) {

    log.info(
        "prefix={}, indexName={}, autoCompleteField={}, additionalField={}, restrictedField={}, restrictedFieldValue={}",
        prefix, indexName, autoCompleteField, additionalField, restrictedField, restrictedFieldValue);
    try {
      return new ResponseEntity<>(searchService.autoComplete(prefix, indexName,
          autoCompleteField, additionalField, restrictedField, restrictedFieldValue), HttpStatus.OK);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @GetMapping(path = "/mapping")
  public ResponseEntity<?> mapping(@RequestParam String indexName) {
    log.info("indexName={}", indexName);
    try {
      return new ResponseEntity<>(searchService.getIndexMapping(indexName), HttpStatus.OK);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
    
  @PostMapping(path = "/search", consumes = "application/json")
  public ResponseEntity<String> search(@RequestBody String query, @RequestParam String indexName) {

    log.info("indexName={}, query={}", indexName, query);

    try {
      return new ResponseEntity<>(searchService.search(indexName, query), HttpStatus.ACCEPTED);
    } catch (SearchApiException e) {
      return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
