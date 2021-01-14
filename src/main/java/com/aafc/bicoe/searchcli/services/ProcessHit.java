package com.aafc.bicoe.searchcli.services;

import org.elasticsearch.search.SearchHit;

@FunctionalInterface
public interface ProcessHit {
    void process(SearchHit hit);
}
