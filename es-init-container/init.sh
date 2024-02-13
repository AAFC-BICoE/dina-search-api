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
  optionalMappingFile=DINA_${currIndex}_OPTIONAL_INDEX_SETTINGS_FILE
  
if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]
then
  # If updateFile is set and not empty, run the script with it
  ./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile} ${!optionalMappingFile}
else
  # If updateFile is not set or is empty, run the script without it
  ./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile}
fi

# TODO get the name of the index returned by create-index
# call add-alias.sh

INDEX_NAME=$(curl -X GET "$HOST/_alias/${!indexName}" | jq -r 'keys[0]')
# TODO handle case when there is no alias

echo "Checking if migration is required"
./migrate-index.sh "$ELASTIC_SERVER_URL" "$INDEX_NAME" "${!indexName}" "${!indexFile}" "${!optionalMappingFile}"


  # Delete old index
  # Deleting old index..."
  # curl -o /dev/null -X DELETE "$HOST/$INDEX" -H 'Content-Type:application/json' -H 'Accept: application/json'

# TODO get the name of the index returned by migrate-index
# call add-alias

done
