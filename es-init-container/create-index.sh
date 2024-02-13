#!/bin/bash

#
# The script is testing the presence of the index name. If the index does not
# exist the index is created with the passed index json configuration otherwise
# the script simply exit.
#
HOST="$1"           # Host name in the url format
INDEX_ALIAS="$2"          # ES index alias name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index
OPTIONAL_MAPPING_FILE="$4"   # JSON file path name containing the update for the index

INDEX_NAME=$(curl -X GET "$HOST/_alias/$INDEX_ALIAS" | jq -r 'keys[0]')

index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX_NAME/?pretty")"
echo "HTTP Code returned by ElasticSearch: $index_exist"

INDEX_ALIAS=${INDEX_ALIAS//\"}

if [ "$index_exist" = '200' ]
then
  echo "Index $INDEX_ALIAS already created, nothing to do"
else

  TIMESTAMP=$(date +%Y%m%d%H%M%S)

  INDEX_TIMESTAMP=${INDEX_ALIAS}_${TIMESTAMP}

  echo "Creating index $INDEX_TIMESTAMP"

  echo "Mapping definition:"
  cat "$SETTINGS_FILE"
  curl -X PUT "$HOST/$INDEX_TIMESTAMP/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"

  if [ -n "$4" ]
  then
    echo "Running update script for optional mapping"
    exec ./update-index.sh "$HOST" "$INDEX_TIMESTAMP" "$OPTIONAL_MAPPING_FILE"
  fi

fi
