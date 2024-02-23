#!/bin/bash

#
# The script is responsible for creating an index.
# A new name will be generated based on the prefix and the current timestamp.
#

HOST="$1"           # Host name in the url format
INDEX_PREFIX="$2"   # ES index prefix
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index

>&2 echo -e "\n\n Start of create-index.sh"

TIMESTAMP=$(date +%Y%m%d%H%M%S)
INDEX_TIMESTAMP=${INDEX_PREFIX}_${TIMESTAMP}

>&2 echo "Creating index $INDEX_TIMESTAMP"

>&2 echo "Mapping definition:"
>&2 cat "$SETTINGS_FILE"
curl -s -o /dev/null -X PUT "$HOST/$INDEX_TIMESTAMP/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"

echo $INDEX_TIMESTAMP
