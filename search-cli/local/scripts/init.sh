#! /bin/bash

# Wait for elasticsearch to be up and running...
./wait-for-elasticsearch.sh $ELASTIC_SERVER_URL $HEALTH_CMD

# Create the default index, if not already created...
./create-index.sh $CREATE_INDEX_HOST $INDEX_NAME $INDEX_SETTINGS_FILE
