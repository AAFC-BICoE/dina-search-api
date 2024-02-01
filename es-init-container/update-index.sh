#!/bin/bash

#
# The script is testing the presence of the index name. If the index does not
# exist the index is created with the passed index json configuration otherwise
# the script simply exit.
#
HOST="$1"           # Host name in the url format
INDEX="$2"          # ES index name
JSON_PAYLOAD="$3"   # ES update file name

index_exist="$(curl -s -o /dev/null -I -w "%{http_code}" "$HOST/$INDEX/?pretty")"
echo "HTTP Code returned by ElasticSearch: $index_exist"

if [ "$index_exist" = '200' ]
then
  echo "Index $INDEX is present. Ready to update."
  echo "Updating index $INDEX"

STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$HOST/$INDEX/_mapping" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$JSON_PAYLOAD")

# Check if the update was successful
if [ "$STATUS_CODE" = '200' ]

then
  echo "Mapping update successful!"
else
  echo "Mapping update failed!"
fi

else
  echo "Index $INDEX is not present. Cannot update."
fi
