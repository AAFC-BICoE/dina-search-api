#!/bin/bash

HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index

index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX/?pretty")"
echo $index_exist
if [ "$index_exist" = '200' ]
then
  echo "Index $INDEX already created, nothing to do"
else
  echo "Creating index $INDEX"
  curl -X PUT "$HOST/$INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @$SETTINGS_FILE
fi
