#!/bin/bash

###Temporary changes to variables for testing purposes###

HOST="http://192.168.49.2:32439"           # Host name in the url format
INDEX="dina_material_sample_index"          # ES index name
SETTINGS_FILE="../local/elastic-configurator-settings/collection-index/dina_material_sample_index_settings.json"  # JSON file path name containing the settings for the index
OPTIONAL_MAPPING_FILE="$4"   # JSON file path name containing the update for the index

remote_schema="$(curl -XGET "$HOST/$INDEX/_mapping?pretty")"

remote_mappings=$(echo "$remote_schema" | jq -r ".$INDEX.mappings")

remote_version=$(echo "$remote_schema" | jq -r ".$INDEX.mappings._meta.version.number // \"0\"" | bc -l)

local_mappings=$(cat $SETTINGS_FILE | jq '.mappings')

local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

TIMESTAMP=$(date +%Y%m%d%H%M%S)
echo "Local version: $local_version"
echo "Remote version: $remote_version"

if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then

  NEW_INDEX=$INDEX'_'$TIMESTAMP

  #Create new index as 'old_index_name_timestamp'
  curl -X PUT "$HOST/$NEW_INDEX/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$SETTINGS_FILE"

  #Re-index documents

  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$HOST/$INDEX/_reindex?pretty" -d'{
    "source": {
      "index": "'$INDEX'"
    },
    "dest": {
      "index": "'$NEW_INDEX'"
    }
}')

if [ "$STATUS_CODE" = '200' ]
then
  #Add aliases from old to new index

    echo "Documents reindexed. Updating alias..."
    
    STATUS_CODE_2=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST $HOST/$INDEX/_aliases?pretty -d'{
    "actions" : [
        { "add" : { "index" : "'$NEW_INDEX'", "alias" : "'$INDEX'" } }
    ]
  }')

  echo $STATUS_CODE_2
else
    echo "Error: Reindexing failed. Status code is $STATUS_CODE"
fi
else
  echo "Nothing to update, remote version is the same as local."
fi
