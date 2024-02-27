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
  >&2 echo "Index alias is not created for: ${!indexName}"

  #Create new index, re-index, delete old, add alias
  NEW_INDEX=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL ./create-index.sh $ELASTIC_SERVER_URL ${!indexName} ${!indexFile})

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
    #get total number of documents in old_index
    num_docs_old=$(get_document_count "$ELASTIC_SERVER_URL" "${!indexName}")

    #get total number of documents in new_index
    num_docs_new=$(get_document_count "$ELASTIC_SERVER_URL" "$NEW_INDEX")

    >&2 echo -e "Old index doc count: $num_docs_old \nNew index doc count: $num_docs_new"

    #only when old index is deleted add-alias is evoked
    if [ "$num_docs_old" -eq "$num_docs_new" ]; then
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
