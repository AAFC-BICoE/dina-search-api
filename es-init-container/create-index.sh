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
INDEX_PREFIX="$2"          # ES index alias name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index

>&2 echo -e "\n\n Start of create-index.sh"

TIMESTAMP=$(date +%Y%m%d%H%M%S)
INDEX_TIMESTAMP=${INDEX_PREFIX}_${TIMESTAMP}

>&2 echo "Creating index $INDEX_TIMESTAMP"

>&2 echo "Mapping definition:"
>&2 cat "$SETTINGS_FILE"
curl -s -o /dev/null -X PUT "$HOST/$INDEX_TIMESTAMP/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"

echo $INDEX_TIMESTAMP

