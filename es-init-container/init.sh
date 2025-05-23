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
#           Checks if re-index is successful
#           T: Checks if number of documents in both indices match
#              T: Deletes old index and evokes add-alias
#              F: 1) Deletes newly created index
#                 2) Reverses read-only op on current index.
#           F: Deletes new index
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
    >&2 echo "Index prefix name is : ${!indexPrefixName}"
    
    CURRENT_INDEX_NAME=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL "curl -s -X GET "$ELASTIC_SERVER_URL/_alias/${!indexPrefixName}" | jq -r 'keys[0]'")
    
    if [[ "$CURRENT_INDEX_NAME" != "${!indexPrefixName}"* ]]; then
      response="$(curl -s -o /dev/null -I -w "%{http_code}" "$ELASTIC_SERVER_URL/${!indexPrefixName}")"
      >&2 echo "Response code when checking index using prefix as name: $response"
      if [ "$response" == '200' ]; then
        >&2 echo "The index does exist but has no alias. Run script with PREPARE_ENV to create index/alias pair."
        >&2 echo "Skipping index creation."
        continue
      else
        >&2 echo "No index or alias exists with: ${!indexPrefixName}. Proceeding with script"
      fi
    fi

    index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$ELASTIC_SERVER_URL/$CURRENT_INDEX_NAME/?pretty")"


    if [ "$index_exist" == '200' ]; then
      >&2 echo "Index ${!indexPrefixName} already created"
        
      >&2 echo "Checking if migration is required ..."
      check_mapping_version "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "${!indexFile}"
      exit_status=$?

      if [[ $exit_status -eq 1 ]]; then
          
        #versions are different
        #create index
        >&2 echo "Running create script"
        NEW_INDEX=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL ./create-index.sh $ELASTIC_SERVER_URL ${!indexPrefixName} ${!indexFile})

        if [[ -n "$NEW_INDEX" ]]; then
          if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
            # If updateFile is set and not empty, run the script with it
            >&2 echo "Running update script for optional mapping"

            if update_request "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!optionalMappingFile}"; then
                echo "The update request was successful"
            else
                echo "The update request failed with status"
            fi
          fi 
        fi

        STATUS_CODE_READ_ONLY=$(set_read_only_allow_delete "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "true")

        >&2 echo "The read only operation status before reindex is: $STATUS_CODE_READ_ONLY"

        #m re-index ---
        # reindex script is optional
        reindex_script_var="DINA_${currIndex}_REINDEX_SCRIPT"
        reindex_script="${!reindex_script_var}"
        response=$(reindex_request "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "$NEW_INDEX" "$reindex_script")

        #if re-index successful
        if [[ $response == '200' ]]; then
          # Get total number of documents in old_index
          num_docs_old=$(get_document_count "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" )

          # Make sure we reach the right number of document
          if wait_for_document_count "$ELASTIC_SERVER_URL" "$NEW_INDEX" "$num_docs_old"; then
            # Deleting old index..."
            >&2 echo "Document counts match. Proceeding with deletion of old index and setting alias..."
            while true; do
              response=$(delete_index_request "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME")
              if [ "$response" == '200' ]; then
                  add_alias_response=$(add_index_alias $ELASTIC_SERVER_URL $NEW_INDEX ${!indexPrefixName})
                  break
              fi
              sleep 1
            done
          else
            >&2 echo "Document counts do not match. Not proceeding with deletion and reversing read-only on index..."

            STATUS_CODE_READ_ONLY=$(set_read_only_allow_delete "$ELASTIC_SERVER_URL" "$CURRENT_INDEX_NAME" "null")

            >&2 echo "The read only operation status is: $STATUS_CODE_READ_ONLY"

            DELETE_NEW_INDEX_RESPONSE=$(delete_index_request "$ELASTIC_SERVER_URL" "$NEW_INDEX")
            >&2 echo "The delete request status for index is: $DELETE_NEW_INDEX_RESPONSE"
          fi

        else
          >&2 echo "Deleting new index since reindexing was not successful"
          DELETE_NEW_INDEX_RESPONSE=$(delete_index_request "$ELASTIC_SERVER_URL" "$NEW_INDEX")

        fi

      else
      >&2 echo "No need for re-indexing remote schema"
      fi
    
    #Index did not exist. Creating index
    else
      NEW_INDEX=$(./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL ./create-index.sh $ELASTIC_SERVER_URL ${!indexPrefixName} ${!indexFile})
      if [ -v "$optionalMappingFile" ] && [ -n "${!optionalMappingFile}" ]; then
        # If updateFile is set and not empty, run the script with it
        >&2 echo "Running update script for optional mapping"
        if update_request "$ELASTIC_SERVER_URL" "$NEW_INDEX" "${!optionalMappingFile}"; then
            echo "The update request was successful"
        else
            echo "The update request failed with status"
        fi
      fi
      >&2 echo "New Index created. Calling add-alias script"
      add_alias_response=$(add_index_alias $ELASTIC_SERVER_URL $NEW_INDEX ${!indexPrefixName})
      if [[ "$add_alias_response" != '200' ]]; then
        >&2 echo "Deleting new index since alias could not be created"

        DELETE_NEW_INDEX_RESPONSE=$(delete_index_request "$ELASTIC_SERVER_URL" "$NEW_INDEX")

      fi

    fi
  done
fi
