package com.aafc.bicoe.searchcli.services;

import com.aafc.bicoe.searchcli.jsonapi.model.DinaType;

public interface IIndexer {

    void indexDocument(DinaType dinaType, String rawPayload);

    void releaseResources();

}
