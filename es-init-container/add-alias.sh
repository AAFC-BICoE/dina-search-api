#!/bin/bash

#Responsible to add an alias to an index

HOST="$1"          # Host name in the url format
INDEX_NAME="$2"    # ES index name
INDEX_ALIAS="$3"   # ES index alias to add

echo "Adding alias..."

STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST $HOST/_aliases?pretty -d'{
  "actions" : [
      { "add" : { "index" : "'$INDEX_NAME'", "alias" : "'$INDEX_ALIAS'" } }
  ]
}')

echo "Alias update response code is: $STATUS_CODE"
