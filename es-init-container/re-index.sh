#!/bin/bash

# Checks version of the current ES schema and the one from the file.
# If the version on the file is larger, the migration will be applied.
# Responsibilities: create a new index (with timestamp in the name) and reindex the data from the source in the new index.

HOST="$1"                 # Host name in the url format
SOURCE_INDEX_NAME="$2"    # ES index name (source)
DEST_INDEX_NAME="$3"         # prefix to use to create the new index name prefix + timestamp

  >&2 echo -e "\n\n Start of re-index.sh"
 #Re-index documents
  >&2 echo "Re-indexing documents."

  >&2 echo "Source index is: $SOURCE_INDEX_NAME and destination index is: $DEST_INDEX_NAME ."

  STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$HOST/_reindex?pretty" -d'{
    "source": {
      "index": "'$SOURCE_INDEX_NAME'"
    },
    "dest": {
      "index": "'$DEST_INDEX_NAME'"
    }
  }')

  >&2 echo "Status code of reindexing op is: $STATUS_CODE."

  if [ "$STATUS_CODE" = '200' ]
  then
    >&2 echo "Re-indexed documents successfully"
    sleep 2
    exit 0
  else
    >&2 echo "Could not reindex, do not delete old index or alias"
    exit 1
  fi

