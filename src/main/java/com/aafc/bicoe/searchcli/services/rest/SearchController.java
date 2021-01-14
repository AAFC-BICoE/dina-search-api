package com.aafc.bicoe.searchcli.services.rest;

import com.aafc.bicoe.searchcli.services.ISearchService;

import org.elasticsearch.action.search.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/search")
public class SearchController {
  
    
    private ISearchService searchService;

    @Autowired
    public SearchController(ISearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/auto-complete")
    public ResponseEntity<SearchResponse> autocomplete(
        @RequestParam String prefix, 
        @RequestParam String indexName,
        @RequestParam String field) throws Exception {

        log.info("prefix={}, indexName={}, field={}",
            prefix, indexName, field);

        searchService.autoComplete(prefix, indexName, field);
                
        return new ResponseEntity(searchService.autoComplete(prefix, indexName, field), HttpStatus.ACCEPTED);
    }
}
