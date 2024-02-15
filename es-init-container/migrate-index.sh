#!/bin/bash

# Checks version of the current ES schema and the one from the file.
# If the version on the file is larger, the migration will be applied.
# Responsibilities: create a new index (with timestamp in the name) and reindex the data from the source in the new index.

HOST="$1"                 # Host name in the url format
SOURCE_INDEX_NAME="$2"    # ES index name (source)
INDEX_PREFIX="$3"         # prefix to use to create the new index name prefix + timestamp
SETTINGS_FILE="$4"  # JSON file path name containing the settings for the index

>&2 echo -e "\n\n Start of migrate-index.sh"

remote_schema="$(curl -X GET "$HOST/$SOURCE_INDEX_NAME/_mapping?pretty")"

>&2 echo "Remote schema: $remote_schema"

remote_version=$(echo "$remote_schema" | jq -r ".$SOURCE_INDEX_NAME.mappings._meta.version.number // \"0\"" | bc -l)

local_mappings=$(cat $SETTINGS_FILE | jq '.mappings')
local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

>&2 echo "Local version: $local_version"
>&2 echo "Remote version: $remote_version"

if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then
  
  >&2 echo "Versions are different. Creating new index."

  #make source index read-only

  STATUS_CODE_READ_ONLY=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$HOST/$INDEX_PREFIX/_settings?pretty" -H "Content-Type: application/json" -d'{
  "index.blocks.write": false
  }')
  
  >&2 echo "The read only operation status is: $STATUS_CODE_READ_ONLY"

  >&2 echo "Running create script"
  NEW_INDEX=$(./create-index.sh $HOST $INDEX_PREFIX $SETTINGS_FILE)
  
  #Re-index documents
  >&2 echo "Index created. Re-indexing documents."

  >&2 echo "Source index is: $SOURCE_INDEX_NAME and destination index is: $NEW_INDEX ."

  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$HOST/_reindex?pretty" -d'{
    "source": {
      "index": "'$SOURCE_INDEX_NAME'"
    },
    "dest": {
      "index": "'$NEW_INDEX'"
    }
  }')

  >&2 echo "Status code of reindexing op is: $STATUS_CODE."

  if [ "$STATUS_CODE" = '200' ]
  then
    >&2 echo "Created new index and re-indexed documents successfully"
    echo $NEW_INDEX
    exit 0
  else
    >&2 echo "Could not reindex, do not delete old index or alias"
    exit 1
  fi
else
  >&2 echo "Remote version is higher than or equal to local version , no need for update"
  exit 0
fi
