#!/bin/bash

# Checks version of the current ES schema and the one from the file.
# If the version on the file is larger, the migration will be applied.
# Responsibilities: create a new index (with timestamp in the name) and reindex the data from the source in the new index.

HOST="$1"                 # Host name in the url format
SOURCE_INDEX_NAME="$2"    # ES index name (source)
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index

>&2 echo -e "\n\n Checking mapping version"

remote_schema="$(curl -X GET "$HOST/$SOURCE_INDEX_NAME/_mapping?pretty")"

>&2 echo "Remote schema: $remote_schema"

remote_version=$(echo "$remote_schema" | jq -r ".$SOURCE_INDEX_NAME.mappings._meta.version.number // \"0\"" | bc -l)

local_mappings=$(cat $SETTINGS_FILE | jq '.mappings')
local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

>&2 echo "Local version: $local_version"
>&2 echo "Remote version: $remote_version"

if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then
  
  >&2 echo "Versions are different."
  exit 1
else
  >&2 echo "Remote version is higher than or equal to local version , no need for update"
  exit 0
fi
