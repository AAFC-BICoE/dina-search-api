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
#     T:Evokes check-mapping-version
#       Checks if script returned 1 or 0
#        1: Creates new index
#           Evokes update script on new index provided mapping file
#           Perform read-only op on current index.
#           Evokes re-index on new index
#           Checks if number of documents in both indices match
#            T: Deletes old index and evokes add-alias
#            F: 1) Deletes newly created index
#               2) Reverses read-only op on current index.
#     F: 1) Evokes create-index
#        2) Updates index provided optional mapping file
#        3) Evokes add-alias

source es_functions.sh

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
        
      >&2 echo "Checking if migration is required ..."
        
      ./check-mapping-version.sh "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "${!indexFile}"
      exit_status=$?  # get the exit status of the script
        
      if [[ $exit_status -eq 1 ]]; then
          
        #versions are different
        #create index
        >&2 echo "Running create script"
        NEW_INDEX=$(./wait-for-elasticsearch.sh ./create-index.sh $ELASTIC_SERVER_URL ${!indexPrefixName} ${!indexFile})

        if [[ -n "$NEW_INDEX" ]]; then
          if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
            # If updateFile is set and not empty, run the script with it
            >&2 echo "Running update script for optional mapping"
            ./update-index.sh "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!optionalMappingFile}"
          fi 
        fi

        STATUS_CODE_READ_ONLY=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME/_settings?pretty" -H "Content-Type: application/json" -d'{
            "index.blocks.read_only_allow_delete": true
        }')
          
        #re-index
        ./re-index.sh "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "$NEW_INDEX"
        exit_status=$?  # get the exit status of the script

        #if re-index successful
        if [[ $exit_status -eq 0 ]]; then
          #get total number of documents in old_index
          num_docs_old=$(get_document_count "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" )

          #get total number of documents in new_index

          num_docs_new=$(get_document_count "$ELASTIC_SERVER_URL" "$NEW_INDEX")

          >&2 echo -e "Old index doc count: $num_docs_old \nNew index doc count: $num_docs_new"

          #only when old index is deleted add-alias is evoked
          if [ "$num_docs_old" -eq "$num_docs_new" ]; then
            # Deleting old index..."
            >&2 echo "Document counts match. Proceeding with deletion of old index and setting alias..."
            while true; do
              response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME" -H 'Content-Type:application/json' -H 'Accept: application/json')
              if [ "$response" -eq 200 ]; then
                  ./add-alias.sh $ELASTIC_SERVER_URL $NEW_INDEX ${!indexPrefixName}
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

            DELETE_NEW_INDEX_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$NEW_INDEX" -H 'Content-Type:application/json' -H 'Accept: application/json')

            >&2 echo "The delete request status for index is: $DELETE_NEW_INDEX_RESPONSE"
          fi

        fi

      else
      >&2 echo "No need for re-indexing remote schema"
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
      exit_status=$?  # get the exit status of the script
      if [[ $exit_status -eq 1 ]]; then
        >&2 echo "Deleting new index since alias could not be created"
        DELETE_NEW_INDEX_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$ELASTIC_SERVER_URL/$NEW_INDEX" -H 'Content-Type:application/json' -H 'Accept: application/json')

      fi

    fi
  done
fi
