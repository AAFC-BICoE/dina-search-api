#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
#
# Script flow:
# Iterates through different index array
# Evokes create-index script
#   Evokes add-alias if create-index returns new index
# Evokes migrate-index script
#   Deletes old index and evokes add-alias if migrate-script
#   created and re-indexed new index

index_array=($DINA_INDEX_DECLARATIONS)
for currIndex in ${index_array[@]}; do

  indexName=DINA_${currIndex}_INDEX_NAME
  indexFile=DINA_${currIndex}_INDEX_SETTINGS_FILE
  optionalMappingFile=DINA_${currIndex}_OPTIONAL_INDEX_SETTINGS_FILE
  
  >&2 echo -e "\n\n\n\n"

if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]
then
  # If updateFile is set and not empty, run the script with it
  NEW_INDEX=$(./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile} ${!optionalMappingFile})

else
  # If updateFile is not set or is empty, run the script without it
  NEW_INDEX=$(./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile})
fi


if [[ -z "$NEW_INDEX" ]]; then
 >&2 echo "Index already existed. Fetching the existing index..."
 NEW_INDEX=$(curl -X GET "$ELASTIC_SERVER_URL/_alias/${!indexName}" | jq -r 'keys[0]')
else
  ./add-alias.sh $ELASTIC_SERVER_URL $NEW_INDEX ${!indexName}
fi

>&2 echo "Index is: $NEW_INDEX"

>&2 echo "Checking if migration is required"


NEW_MIGRATE_INDEX=$(./migrate-index.sh "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!indexName}" "${!indexFile}" "${!optionalMappingFile}")

exit_status=$?  # get the exit status of the script

# Deleting old index..."

if [[ $exit_status -eq 0 && -n "$NEW_MIGRATE_INDEX" ]]; then

  INDEX_NAME=$(curl -s -X GET "$ELASTIC_SERVER_URL/_alias/${!indexName}" | jq -r 'keys[0]')

  >&2 echo "migrate-script created new index, deleting old index: $INDEX_NAME"

  while true; do
      response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$INDEX_NAME" -H 'Content-Type:application/json' -H 'Accept: application/json')
      if [ "$response" -eq 200 ]; then
          ./add-alias.sh $ELASTIC_SERVER_URL $NEW_MIGRATE_INDEX ${!indexName}
          break
      fi
      sleep 1
  done

else
  >&2 echo "Nothing else to do, migrate-script did not create new index"
fi

done
