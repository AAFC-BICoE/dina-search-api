#!/bin/bash

HOST="$1"           # Host name in the url format
INDEX_ALIAS="$2"    # ES index alias name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index
OPTIONAL_MAPPING_FILE="$4"   # JSON file path name containing the update for the index

INDEX=$(curl -X GET "$HOST/_alias/$INDEX_ALIAS" | jq -r 'keys[0]')

remote_schema="$(curl -XGET "$HOST/$INDEX/_mapping?pretty")"

remote_mappings=$(echo "$remote_schema" | jq -r ".$INDEX.mappings")

remote_version=$(echo "$remote_schema" | jq -r ".$INDEX.mappings._meta.version.number // \"0\"" | bc -l)

local_mappings=$(cat $SETTINGS_FILE | jq '.mappings')

local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

TIMESTAMP=$(date +%Y%m%d%H%M%S)
echo "Local version: $local_version"
echo "Remote version: $remote_version"

if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then

  NEW_INDEX=${INDEX_ALIAS}_${TIMESTAMP}
  
  echo "Versions are different. Creating new index."
  #Create new index as 'old_index_name_timestamp'
  echo $NEW_INDEX
  curl -X PUT "$HOST/$NEW_INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"
  
  #update new index provided optional mapping file

  if [ -n "$4" ]
  then
    echo "Running update script for optional mapping"
    ./update-index.sh "$HOST" "$NEW_INDEX" "$OPTIONAL_MAPPING_FILE"
  fi
  
  #Re-index documents
  echo "Index created. Re-indexing documents."

  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$HOST/_reindex?pretty" -d'{
    "source": {
      "index": "'$INDEX'"
    },
    "dest": {
      "index": "'$NEW_INDEX'"
    }
}')

if [ "$STATUS_CODE" = '200' ]
then
  #Delete old index
  echo "Documents re-indexed.Deleting old index..."
    
  curl -o /dev/null -X DELETE "$HOST/$INDEX" -H 'Content-Type:application/json' -H 'Accept: application/json'

  #Add aliases from old to new index

  echo "Updating alias..."
    
  STATUS_CODE_2=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST $HOST/_aliases?pretty -d'{
    "actions" : [
        { "add" : { "index" : "'$NEW_INDEX'", "alias" : "'$INDEX_ALIAS'" } }
    ]
  }')

else
    echo "Error: Reindexing failed. Status code is $STATUS_CODE"
fi
else
  echo "Nothing to update, remote version is the same as local."
fi
