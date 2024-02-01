#!/bin/bash

#
# The script is testing the presence of the index name. If the index does not
# exist the index is created with the passed index json configuration otherwise
# the script simply exit.
#
HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index
JSON_PAYLOAD="$4"   # JSON file path name containing the update for the index

index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX/?pretty")"
echo "HTTP Code returned by ElasticSearch: $index_exist"
if [ "$index_exist" = '200' ]
then
  echo "Index $INDEX already created, nothing to do"
else
  echo "Creating index $INDEX"
  echo "Mapping definition:"
  cat "$SETTINGS_FILE"
  curl -X PUT "$HOST/$INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"
fi

# Check if UPDATE_SCRIPT has been passed
if [ -n "$UPDATE_SCRIPT" ]
then
  echo "Running update script"
  exec "$UPDATE_SCRIPT" "$HOST" "$INDEX" "$JSON_PAYLOAD"
fi