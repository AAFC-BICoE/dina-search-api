#!/bin/bash

#
# The script is testing the presence of the index name. If the index does not
# exist the index is created with the passed index json configuration otherwise
# the script simply exit.
#
HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index
OPTIONAL_MAPPING_FILE="$4"   # JSON file path name containing the update for the index

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


if [ -n "$4" ]
then
  echo "Running update script"
  exec ./update-index.sh "$HOST" "$INDEX" "$OPTIONAL_MAPPING_FILE"
fi
