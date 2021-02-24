package ca.gc.aafc.dina.search.ws.services.rest;

import org.elasticsearch.action.search.SearchResponse;
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
import ca.gc.aafc.dina.search.ws.services.ISearchService;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping("/search")
public class SearchController {

  private ISearchService searchService;

  @Autowired
  public SearchController(ISearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/auto-complete")
  public ResponseEntity<SearchResponse> autocomplete(@RequestParam String prefix, @RequestParam String indexName,
      @RequestParam String field) {

    log.info("prefix={}, indexName={}, field={}", prefix, indexName, field);

    searchService.autoComplete(prefix, indexName, field);

    return new ResponseEntity<>(searchService.autoComplete(prefix, indexName, field), HttpStatus.ACCEPTED);
  }

  @PostMapping(path = "/text", consumes = "application/json", produces = "application/json")
  public ResponseEntity<String> query(@RequestBody String query, @RequestParam String indexName)
      throws SearchApiException {

    log.info("indexName={}, query={}", indexName, query);

    return new ResponseEntity<>(searchService.search(indexName, query), HttpStatus.ACCEPTED);
  }
}
