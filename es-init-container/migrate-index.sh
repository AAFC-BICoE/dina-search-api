#!/bin/bash

# Checks version of the current ES schema and the one from the file.
# If the version on the file is larger, the migration will be applied.
# Responsibilities: create a new index (with timestamp in the name) and reindex the data from the source in the new index.

HOST="$1"                 # Host name in the url format
SOURCE_INDEX_NAME="$2"    # ES index name (source)
INDEX_PREFIX="$3"         # prefix to use to create the new index name prefix + timestamp
SETTINGS_FILE="$4"  # JSON file path name containing the settings for the index
OPTIONAL_MAPPING_FILE="$5"   # JSON file path name containing the update for the index

remote_schema="$(curl -XGET "$HOST/$SOURCE_INDEX_NAME/_mapping?pretty")"
remote_version=$(echo "$remote_schema" | jq -r ".$SOURCE_INDEX_NAME.mappings._meta.version.number // \"0\"" | bc -l)

local_mappings=$(cat $SETTINGS_FILE | jq '.mappings')
local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

echo "Local version: $local_version"
echo "Remote version: $remote_version"

if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then

  TIMESTAMP=$(date +%Y%m%d%H%M%S)
  NEW_INDEX=${INDEX_PREFIX}_${TIMESTAMP}
  
  echo "Versions are different. Creating new index."
  #Create new index as 'old_index_name_timestamp'
  echo "$NEW_INDEX"
  curl -X PUT "$HOST/$NEW_INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"
  
  #update new index provided optional mapping file

  if [ -n "$5" ]
  then
    echo "Running update script for optional mapping"
    ./update-index.sh "$HOST" "$NEW_INDEX" "$OPTIONAL_MAPPING_FILE"
  fi
  
  #Re-index documents
  echo "Index created. Re-indexing documents."

  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$HOST/_reindex?pretty" -d'{
    "source": {
      "index": "'SOURCE_INDEX_NAME'"
    },
    "dest": {
      "index": "'$NEW_INDEX'"
    }
  }')

  if [ "$STATUS_CODE" != '200' ]
  then
      echo "Error: Reindexing failed. Status code is $STATUS_CODE"
  fi
else
  echo "Nothing to update, remote version is the same as local."
fi
