#!/bin/bash

#
# The script is responsible for creating an index.
# It handles the following scenarios:
# The index does not exist.
# The index exists but the script was invoked from migrate-index.
# In both cases the index is created with passed
# index json configuration and optional mapping.
#

HOST="$1"           # Host name in the url format
INDEX_ALIAS="$2"          # ES index alias name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index
MIGRATE_FLAG="$4"   # Flag that identifies this script is being executed from migrate script
OPTIONAL_MAPPING_FILE="$5"   # JSON file path name containing the update for the index

>&2 echo -e "\n\n Start of create-index.sh"

INDEX_NAME=$(curl -X GET "$HOST/_alias/$INDEX_ALIAS" | jq -r 'keys[0]')

>&2 echo "Checking if index exists..."
index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX_NAME/?pretty")"
>&2 echo "HTTP Code returned by ElasticSearch: $index_exist"

INDEX_ALIAS=${INDEX_ALIAS//\"}

if [ "$index_exist" = '200' ]; then
  if [ "$4" = true ]; then
    >&2 echo "Index $INDEX_ALIAS already created, but migrate flag was provided"
  else
    >&2 echo "Index $INDEX_ALIAS already created, nothing to do"
    exit 0
  fi
fi

TIMESTAMP=$(date +%Y%m%d%H%M%S)
INDEX_TIMESTAMP=${INDEX_ALIAS}_${TIMESTAMP}

>&2 echo "Creating index $INDEX_TIMESTAMP"

>&2 echo "Mapping definition:"
>&2 cat "$SETTINGS_FILE"
curl -s -o /dev/null -X PUT "$HOST/$INDEX_TIMESTAMP/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"

if [ -n "$5" ]; then
  >&2 echo "Running update script for optional mapping"
  ./update-index.sh "$HOST" "$INDEX_TIMESTAMP" "$OPTIONAL_MAPPING_FILE"
fi

echo $INDEX_TIMESTAMP

