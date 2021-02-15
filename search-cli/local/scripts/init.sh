#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
# 
./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL $INDEX_NAME $INDEX_SETTINGS_FILE
