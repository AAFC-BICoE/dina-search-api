#!/bin/bash

HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
SETTINGS_FILE="$3"  # JSON file path name containing the settings for the index

echo "Creating index $INDEX"

# Create Index with Setting
curl -X PUT "$HOST/$INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @$SETTINGS_FILE

echo "Done"