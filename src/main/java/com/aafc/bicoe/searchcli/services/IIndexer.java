package com.aafc.bicoe.searchcli.services;

import java.io.IOException;

import com.aafc.bicoe.searchcli.jsonapi.model.DinaType;

public interface IIndexer {

    void indexDocument(DinaType dinaType, String rawPayload) throws IOException;

    void releaseResources();

}
