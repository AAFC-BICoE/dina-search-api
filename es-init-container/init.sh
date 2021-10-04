#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
#

db_array=($INDEX_DECLARATIONS)
for currIndex in ${db_array[@]}; do

  indexName=DINA_${currIndex}_INDEX_NAME
  indexFile=DINA_${currIndex}_INDEX_SETTINGS_FILE

  ./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL ${!indexName} ${!indexFile}

done
