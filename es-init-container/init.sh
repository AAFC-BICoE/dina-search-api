#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
#

index_array=($DINA_INDEX_DECLARATIONS)
for currIndex in ${index_array[@]}; do

  indexName=DINA_${currIndex}_INDEX_NAME
  indexFile=DINA_${currIndex}_INDEX_SETTINGS_FILE
  updateFile=DINA_${currIndex}_INDEX_UPDATE_FILE

  if [ -z "${!updateFile}" ]
  then
    # If updateFile is not set, run the script without it
    ./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL ${!indexName} ${!indexFile}
  else
    # If updateFile is set, run the script with it
    ./wait-for-elasticsearch.sh $INDEX_CREATE_CMD $ELASTIC_SERVER_URL ${!indexName} ${!indexFile} ${!updateFile}
  fi
  
done
