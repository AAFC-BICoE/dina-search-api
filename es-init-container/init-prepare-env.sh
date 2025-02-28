# This script runs if PREPARE_ENV is declared.
# This means the env currently has indices but no aliases
#
# Script flow:
#   Iterates through different index array
#     Evokes create-index script
#       Checks if new index was created:
#       T: 1) Updates index provided optional mapping file
#          2) Calls re-index script
#          3) Checks if re-index is successful
#             T: Checks if number of documents in both indices match
#                T: Deletes old index and evokes add-alias
#                F: 1) Deletes newly created index
#                   2) Reverses read-only op on current index.
#             F: Deletes new index
#               

source es_functions.sh

index_array=($DINA_INDEX_DECLARATIONS)
for currIndex in ${index_array[@]}; do

  indexName=DINA_${currIndex}_INDEX_NAME
  indexFile=DINA_${currIndex}_INDEX_SETTINGS_FILE
  optionalMappingFile=DINA_${currIndex}_OPTIONAL_INDEX_SETTINGS_FILE

  >&2 echo -e "\n\n\n\n"
  >&2 echo "Checking if index exists but has no alias..."

  #Create new index, re-index, delete old, add alias
  
  CURRENT_INDEX_NAME=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL "curl -s -X GET "$ELASTIC_SERVER_URL/_alias/${!indexName}" | jq -r 'keys[0]'")

  if [[ "$CURRENT_INDEX_NAME" != "${!indexName}"* ]]; then
    response="$(curl -s -o /dev/null -I -w "%{http_code}" "$ELASTIC_SERVER_URL/${!indexName}")"
    >&2 echo "Response code when checking index using prefix as name: $response"
    if [ "$response" == '200' ]; then
      >&2 echo "Index exists without alias, creating index."
      NEW_INDEX=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile})
    else
      >&2 echo "No index or alias exists with: ${!indexName}. This scenario is not suitable for prepare-env script. Please re-run container without PREPARE_ENV"
      continue
    fi
  else
    >&2 echo "Index already has an alias. This scenario is not suitable for prepare-env script. Please re-run container without PREPARE_ENV"
    continue
  fi

  if [[ -n "$NEW_INDEX" ]]; then
    if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
      # If updateFile is set and not empty, run the script with it
      >&2 echo "Running update script for optional mapping"
      response=$(update_request "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!optionalMappingFile}")
    fi
  fi

  STATUS_CODE_READ_ONLY=$(set_read_only_allow_delete "$ELASTIC_SERVER_URL" "${!indexName}" "true")

  response=$(reindex_request "$ELASTIC_SERVER_URL" "${!indexName}" "$NEW_INDEX")

  #if re-index successful
  if [[ $response == '200' ]]; then
    # get total number of documents in old_index
    num_docs_old=$(get_document_count "$ELASTIC_SERVER_URL" "${!indexName}")

    # Make sure we reach the right number of document
    if wait_for_document_count "$ELASTIC_SERVER_URL" "$NEW_INDEX" "$num_docs_old"; then
      # Deleting old index..."
      >&2 echo "Document counts match. Proceeding with deletion of old index and setting alias..."
      while true; do

        response=$(delete_index_request "$ELASTIC_SERVER_URL" "${!indexName}")

        if [ "$response" == '200' ]; then
            response=$(add_index_alias $ELASTIC_SERVER_URL $NEW_INDEX ${!indexName})
            break
        fi
        sleep 1
      done
    else
      >&2 echo "Document counts do not match. Not proceeding with deletion and reversing read-only on index..."
      
      STATUS_CODE_READ_ONLY=$(set_read_only_allow_delete "$ELASTIC_SERVER_URL" "${!indexName}" "null")

      >&2 echo "The read only operation status is: $STATUS_CODE_READ_ONLY"

      DELETE_NEW_INDEX_RESPONSE=$(delete_index_request "$ELASTIC_SERVER_URL" "$NEW_INDEX")

      >&2 echo "The delete request status for index is: $DELETE_NEW_INDEX_RESPONSE"
    fi
  
  else
    >&2 echo "Deleting new index since reindexing was not successful"
    DELETE_NEW_INDEX_RESPONSE=$(delete_index_request "$ELASTIC_SERVER_URL" "$NEW_INDEX")

  fi

done