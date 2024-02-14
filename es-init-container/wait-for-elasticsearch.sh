#!/bin/bash

# Code from: https://github.com/elastic/elasticsearch-py/issues/778

#
# The script is evaluating the health of the elasticsearch cluster before
# proceeding with the execution of the passed command to be executed. In our
# scenario the command to be executed is the create_index.sh script.
#
# The health status is set to yellow in our configuration becaue we have no
# redundancy in our deployment (only one node). The status will have to be adjusted
# if the cluster is a multi-node one.
#
# Once the health status is equal to 'yellow' or 'green' the script will invoke the
# create_index to perform the initial index creation (if necessary).
#
set -e

host="$2"

if [ -n "$5" ]
then
  cmd="$1 $2 $3 $4 $5"
else
  cmd="$1 $2 $3 $4"
fi

>&2 echo -e "\n\n Start of wait-for-elasticsearch.sh"

>&2 echo $cmd

until $(curl --output /dev/null --silent --head --fail "$host"); do
    printf '.'
    sleep 1
done

# First wait for ES to start...
response=$(curl $host)

#echo $response

until [ "$response" = "200" ]; do
    response=$(curl --write-out %{http_code} --silent --output /dev/null "$host")
    >&2 echo "Elastic Search is unavailable - sleeping"
    sleep 1
done


# next wait for ES status to turn to Green
health="$(curl -fsSL "$host/_cat/health?h=status")"
health="$(echo "$health" | sed -r 's/^[[:space:]]+|[[:space:]]+$//g')" # trim whitespace (otherwise we'll have "green ")

until [ "$health" = 'yellow' ] || [ "$health" = 'green' ]; do
    health="$(curl -fsSL "$host/_cat/health?h=status")"
    health="$(echo "$health" | sed -r 's/^[[:space:]]+|[[:space:]]+$//g')" # trim whitespace (otherwise we'll have "green ")
    >&2 echo "Elastic Search is unavailable - sleeping"
    sleep 1
done

>&2 echo "Elastic Search is up"
exec $cmd
