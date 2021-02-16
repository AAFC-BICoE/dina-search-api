#!/bin/bash

HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name

echo "Deleting index $INDEX"

# Delete Index
curl -X DELETE "$HOST/$INDEX/?pretty"
