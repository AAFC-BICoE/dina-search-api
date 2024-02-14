#!/bin/bash

#Responsible to add an alias to an index

HOST="$1"          # Host name in the url format
INDEX_NAME="$2"    # ES index name
INDEX_ALIAS="$3"   # ES index alias to add

>&2 echo -e "\n\n Start of add-alias.sh"

>&2 echo "Adding alias..."

>&2 echo "Updating index $INDEX_NAME with alias $INDEX_ALIAS"

STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST $HOST/_aliases?pretty -d'{
  "actions" : [
      { "add" : { "index" : "'$INDEX_NAME'", "alias" : "'$INDEX_ALIAS'" } }
  ]
}')

>&2 echo "Alias update response code is: $STATUS_CODE"
