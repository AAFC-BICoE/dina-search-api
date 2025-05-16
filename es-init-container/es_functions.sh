
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

# Repeatedly calls get_document_count to check the document count in the provided Elasticsearch index.
# Waits 1 second between calls, up to a maximum of 25 attempts, until the target count is reached.
#
# Parameters:
# - elastic_server_url: URL of the Elasticsearch server.
# - index_name: Name of the index to query.
# - target_count: Document count to reach.
#
# Returns 0 if the target count is reached, 1 otherwise.
wait_for_document_count() {
    local elastic_server_url="$1"
    local index_name="$2"
    local target_count="$3"

    local max_attempts=25
    local attempt=0

    while (( attempt < max_attempts )); do
        local current_count
        current_count=$(get_document_count "$elastic_server_url" "$index_name")

        # Check if the returned value is a valid number
        if ! [[ "$current_count" =~ ^[0-9]+$ ]]; then
            echo "Error: Invalid document count returned: $current_count"
            return 1
        fi

        if (( current_count == target_count )); then
            echo "Target document count of $target_count reached."
            return 0
        fi

        echo "Current document count: $current_count. Waiting for target count of $target_count..."
        (( attempt++ ))
        sleep 1
    done

    echo "Maximum attempts reached. Target document count of $target_count not reached."
    return 1
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
    local reindex_script="$4"        # optional painless script

    # Validate parameters
    if [[ -z "$elastic_server_url" || -z "$source_index_name" || -z "$dest_index_name" ]]; then
        >&2 echo "Error: Missing required parameters."
        return 1
    fi

    local returnedCode

    >&2 echo "Re-indexing documents."

    >&2 echo "Source index is: $source_index_name and destination index is: $dest_index_name"

    # Base reindex_payload
    reindex_payload='{
      "source": {
        "index": "'"$source_index_name"'"
      },
      "dest": {
        "index": "'"$dest_index_name"'"
      }'

    # Add painless script block if provided
    if [[ -n "$reindex_script" ]]; then
      reindex_payload+=',
      "script": {
        "lang": "painless",
        "source": "'"$reindex_script"'"
      }'
    fi

    # Close the reindex_payload
    reindex_payload+="}"

    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -X POST "$elastic_server_url/_reindex" -d "$reindex_payload")

    >&2 echo "Response is: $returnedCode"

    if [[ "$returnedCode" -ge 400 ]]; then
        echo "ERROR: Reindex failed. HTTP Status Code: $returnedCode" >&2

        # Fetch and print the full error response from Elasticsearch
        error_response=$(curl -s -X POST "$elastic_server_url/_reindex" -H 'Content-Type:application/json' -H 'Accept: application/json' -d "$reindex_payload")
        echo "Elasticsearch Error Response: $error_response" >&2
    fi

    echo "$returnedCode"
}

# Compares the local version with the remote version and determines if an update is required.
#
# Parameters:
#   $1 (elastic_server_url): The Elasticsearch server URL
#   $2 (source_index_name): The name of the Elasticsearch index to check (e.g., "my_index")
#   $3 (settings_file): The path to a JSON file containing the local mapping settings
#
# Returns:
#   0: If the remote version is greater than or equal to the local version (no update needed).
#   1: If the local version is greater than the remote version (update required).
#
# Notes:
#   - Assumes local_version and remote_version are defined as strings in a format suitable for version sorting (e.g., "1.2.3").
#   - Uses the 'sort -Vr' command for version comparison.
check_mapping_version(){
    local elastic_server_url="$1"    # Host name in the url format
    local source_index_name="$2"     # ES index name (source)
    local settings_file="$3"       # JSON file path name containing the settings for the index

    local returnedCode

    >&2 echo -e "\n\n Checking mapping version"

    remote_schema="$(curl -s -X GET "$elastic_server_url/$source_index_name/_mapping?pretty")"

    remote_version=$(echo "$remote_schema" | jq -r ".$source_index_name.mappings._meta.version.number // \"0\"" | bc -l)

    local_mappings=$(jq -r '.mappings' < "$settings_file")
    # Extract local version from the settings file
    local_version=$(echo "$local_mappings" | jq '._meta.version.number' | bc -l)

    >&2 echo "Local version: $local_version"
    >&2 echo "Remote version: $remote_version"

    if [ "$(printf '%s\n' "$local_version" "$remote_version" | sort -Vr | head -n1)" == "$remote_version" ]; then
      echo "Remote version is higher than or equal to local version , no need for update"
      return 0
    else
      echo "local_version is greater than remote_version. Schema update required"
      return 1
    fi
}

update_request() {
    local elastic_server_url="$1" # Host name in the url format
    local index_name="$2"         # ES index name (source)
    local mapping_file="$3"       # file including the mapping to use

    local returnedCode

    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$elastic_server_url/$index_name/_mapping" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$mapping_file")

    if [[ "$returnedCode" -ge 400 ]]; then
        echo "ERROR: Mapping update failed. HTTP Status Code: $returnedCode" >&2
        # Fetch and print the full error response from Elasticsearch
        error_response=$(curl -s -X POST "$elastic_server_url/$index_name/_mapping" -H 'Content-Type:application/json' -H 'Accept: application/json' -d @"$mapping_file")
        echo "Elasticsearch Error Response: $error_response" >&2
        return 1
    fi
    return 0
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

