#! /bin/bash

# Wait for elasticsearch to be up and running...
# - Passing:
#     Elastic search server endpoint
#     Script create_index that will evaluate if the index creation is needed or not
#     Index name to be created
#     Initial configuration for the index
#
# Script flow:
#  If PREPARE_ENV not declared
#   Iterates through different index array
#   Checks if alias and index exists
#     T:Evokes migrate-index script
#       Checks if new index was created and re-indexed:
#       T: 1) Updates index provided optional mapping file
#          2) Checks if number of documents in both indices match
#          T: Deletes old index and evokes add-alias
#          F: 1) Deletes newly created index
#             2) Reverses read-only op on current index.
#       F: Nothing to do, migrate script does not create new index
#     F: 1) Evokes create-index
#        2) Updates index provided optional mapping file
#        3) Evokes add-alias

if [[ -v PREPARE_ENV && ! -z ${PREPARE_ENV} ]]; then
    echo "PREPARE_ENV is declared and not null. Running prepare-env.sh"
    ./init-prepare-env.sh 
else
  index_array=($DINA_INDEX_DECLARATIONS)
  for currIndex in ${index_array[@]}; do

    indexPrefixName=DINA_${currIndex}_INDEX_NAME
    indexFile=DINA_${currIndex}_INDEX_SETTINGS_FILE
    optionalMappingFile=DINA_${currIndex}_OPTIONAL_INDEX_SETTINGS_FILE
    
    >&2 echo -e "\n\n\n\n"
    >&2 echo "Index alias is : ${!indexPrefixName}"
    CURRENT_INDEX_NAME=$(curl -X GET "$ELASTIC_SERVER_URL/_alias/${!indexPrefixName}" | jq -r 'keys[0]')
    >&2 echo "Checking if index exists..."
    index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME/?pretty")"

    >&2 echo "HTTP Code returned by ElasticSearch: $index_exist"

    if [ "$index_exist" = '200' ]; then
        >&2 echo "Index ${!indexPrefixName} already created"

        >&2 echo "Checking if migration is required"
        NEW_MIGRATE_INDEX=$(./migrate-index.sh "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "${!indexPrefixName}" "${!indexFile}" "${!optionalMappingFile}")
        exit_status=$?  # get the exit status of the script

        # Deleting old index..."

        if [[ $exit_status -eq 0 && -n "$NEW_MIGRATE_INDEX" ]]; then

          if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
            # If updateFile is set and not empty, run the script with it
            >&2 echo "Running update script for optional mapping"
            ./update-index.sh "$ELASTIC_SERVER_URL" "$NEW_MIGRATE_INDEX" "${!optionalMappingFile}"
          fi 

          #get total number of documents in old_index
            num_docs_old=$(curl -s -X GET "$ELASTIC_SERVER_URL/$NEW_INDEX/_search" -H 'Content-Type: application/json' -d'
          {
            "query": {
              "match_all": {}
            }
          }' | jq -r '.hits.total.value')

          >&2 echo "migrate-script created new index, deleting old index: $NEW_INDEX"
          #get total number of documents in new_index

            num_docs_new=$(curl -s -X GET "$ELASTIC_SERVER_URL/$NEW_MIGRATE_INDEX/_search" -H 'Content-Type: application/json' -d'
          {
            "query": {
              "match_all": {}
            }
          }' | jq -r '.hits.total.value')

          >&2 echo -e "Old index doc count: $num_docs_old \nNew index doc count: $num_docs_new"

          #only when old index is deleted add-alias is evoked
          if [ "$num_docs_old" -eq "$num_docs_new" ]; then
            >&2 echo "Document counts match. Proceeding with deletion of old index and setting alias..."
            while true; do
                response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME" -H 'Content-Type:application/json' -H 'Accept: application/json')
                if [ "$response" -eq 200 ]; then
                    ./add-alias.sh $ELASTIC_SERVER_URL $NEW_MIGRATE_INDEX ${!indexPrefixName}
                    break
                fi
                sleep 1
            done
          else
            >&2 echo "Document counts do not match. Not proceeding with deletion and reversing read-only on index..."
            STATUS_CODE_READ_ONLY=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME/_settings?pretty" -H "Content-Type: application/json" -d'{
              "index.blocks.read_only_allow_delete": null
              }')
            >&2 echo "The read only operation status is: $STATUS_CODE_READ_ONLY"

            DELETE_NEW_MIGRATE_INDEX_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$NEW_MIGRATE_INDEX" -H 'Content-Type:application/json' -H 'Accept: application/json')

            >&2 echo "The delete request status for migrate index is: $DELETE_NEW_MIGRATE_INDEX_RESPONSE"

          fi

        else
        >&2 echo "Nothing else to do, migrate-script did not create new index"

        fi

    #Index did not exist. Creating index
    else

      NEW_INDEX=$(./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexPrefixName} ${!indexFile})
      if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
        # If updateFile is set and not empty, run the script with it
        >&2 echo "Running update script for optional mapping"
        ./update-index.sh "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!optionalMappingFile}"
      fi
      >&2 echo "New Index created. Calling add-alias script"
      ./add-alias.sh $ELASTIC_SERVER_URL $NEW_INDEX ${!indexPrefixName}

    fi

  done
fi

