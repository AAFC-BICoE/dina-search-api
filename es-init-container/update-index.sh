#!/bin/bash

#
# The script is handles the optional mapping update
# for a given index.
#
HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
OPTIONAL_MAPPING_FILE="$3"   # ES update file name

>&2 echo "Checking if index exists..."
index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX/?pretty")"
>&2 echo "HTTP Code returned by ElasticSearch: $index_exist"

if [ "$index_exist" = '200' ]
then
  >&2 echo "Index $INDEX is present. Ready to update."
  >&2 echo "Updating index $INDEX"
  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$HOST/$INDEX/_mapping" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$OPTIONAL_MAPPING_FILE")

# Check if the update was successful
if [ "$STATUS_CODE" = '200' ]

then
    >&2 echo "Success: Status code is 200"
else
    >&2 echo "Error: Status code is not 200, it is $STATUS_CODE"
fi

else
  >&2 echo "Index $INDEX is not present. Cannot update."
fi
