#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
# 

# Wait for and create DINA_DOCUMENT_INDEX
./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL $DINA_DOCUMENT_INDEX_NAME $DINA_DOCUMENT_INDEX_SETTINGS_FILE

# Wait for and create DINA_AGENT_INDEX
./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL $DINA_AGENT_INDEX_NAME $DINA_AGENT_INDEX_SETTINGS_FILE

# Wait for and create DINA_MATERIAL_SAMPLE_INDEX
./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL DINA_MATERIAL_SAMPLE_INDEX_NAME DINA_MATERIAL_SAMPLE_INDEX_SETTINGS_FILE