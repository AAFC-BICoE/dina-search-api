
#function for retrieving document count from index
get_document_count() {
    local elastic_server_url="$1"
    local index_name="$2"

    # Query Elasticsearch to get the document count
    local num_docs
    num_docs=$(curl -s -X GET "$elastic_server_url/$index_name/_count" -H 'Content-Type: application/json' -d '
    {
        "query": {
            "match_all": {}
        }
    }' | jq -r '.count')

    echo "$num_docs"  # Print the document count
}

set_read_only_allow_delete() {
    local elastic_server_url="$1"
    local index_name="$2"
    local valueToSet="$3"

    local returnedCode
    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$elastic_server_url/$index_name/_settings?pretty" -H "Content-Type: application/json" -d "{\"index.blocks.read_only_allow_delete\": \"$valueToSet\"}")
    echo "$returnedCode"
}

delete_index_request() {
    local elastic_server_url="$1"
    local index_name="$2"

    local returnedCode
    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$elastic_server_url/$index_name" -H 'Content-Type:application/json' -H 'Accept: application/json')
    echo "$returnedCode"
}

add_index_alias() {
    local elastic_server_url="$1"
    local index_name="$2"
    local index_alias="$3"

    local returnedCode

    >&2 echo "Updating index $index_name with alias $index_alias"

    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST $elastic_server_url/_aliases?pretty -d'{
    "actions" : [
        { "add" : { "index" : "'$index_name'", "alias" : "'$index_alias'" } }
    ]
    }')

    echo "$returnedCode"

}

reindex_request() {
    local elastic_server_url="$1"    # Host name in the URL format
    local source_index_name="$2"     # ES index name (source)
    local dest_index_name="$3"       # Prefix to use to create the new index name prefix + timestamp

    # Validate parameters
    if [[ -z "$elastic_server_url" || -z "$source_index_name" || -z "$dest_index_name" ]]; then
        >&2 echo "Error: Missing required parameters."
        return 1
    fi

    >&2 echo "Re-indexing documents."
    >&2 echo "Source index is: $source_index_name and destination index is: $dest_index_name"

    # Perform the reindex request and capture the response and HTTP status code
    local response
    response=$(curl -s -w "\n%{http_code}" -H "Content-Type: application/json" -X POST "$elastic_server_url/_reindex" -d'{
        "source": {
            "index": "'"$source_index_name"'"
        },
        "dest": {
            "index": "'"$dest_index_name"'"
        }
    }')

    # Extract the HTTP status code from the response
    local http_code
    http_code=$(echo "$response" | tail -n1)
    returnedCode="$http_code"

    # Print the response (excluding the HTTP status code)
    echo "$response" | sed '$d'

    # Check if the request was successful
    if [[ "$returnedCode" -ne 200 ]]; then
        >&2 echo "Error: Reindex request failed with response code $returnedCode"
        return 1
    fi

    sleep 2
    >&2 echo "Response code is: $returnedCode"

    echo "$returnedCode"
}

check_mapping_version(){
    local elastic_server_url="$1"    # Host name in the url format
    local source_index_name="$2"     # ES index name (source)
    local settings_file="$3"       # JSON file path name containing the settings for the index

    local returnedCode

    >&2 echo -e "\n\n Checking mapping version"

    remote_schema="$(curl -s -X GET "$elastic_server_url/$source_index_name/_mapping?pretty")"

    remote_version=$(echo "$remote_schema" | jq -r ".$source_index_name.mappings._meta.version.number // \"0\"" | bc -l)

    local_mappings=$(cat $settings_file | jq '.mappings')
    local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

    >&2 echo "Local version: $local_version"
    >&2 echo "Remote version: $remote_version"

    if [ $(echo "$local_version > $remote_version" | bc -l) -eq 1 ]; then
    
    >&2 echo "Versions are different."
    return 1
    else
    >&2 echo "Remote version is higher than or equal to local version , no need for update"
    return 0
    fi

}
update_request() {
    local elastic_server_url="$1"    # Host name in the url format
    local index_name="$2"     # ES index name (source)
    local mapping_file="$3"       # prefix to use to create the new index name prefix + timestamp

    local returnedCode

    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$elastic_server_url/$index_name/_mapping" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$mapping_file")

    echo "$returnedCode"

}

create_index(){

    local elastic_server_url="$1"    # Host name in the url format
    local index_name="$2"            # ES index name (source)
    local settings_file="$3"          # prefix to use to create the new index name prefix + timestamp
    
    local new_index

    >&2 echo -e "\n\n Start of create-index.sh"

    TIMESTAMP=$(date +%Y%m%d%H%M%S)
    new_index=${index_name}_${TIMESTAMP}

    >&2 echo "Creating index $new_index"

    >&2 echo "Mapping definition:"
    >&2 cat "$settings_file"
    curl -s -o /dev/null -X PUT "$elastic_server_url/$new_index/?pretty" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$settings_file"

    echo $new_index

}

