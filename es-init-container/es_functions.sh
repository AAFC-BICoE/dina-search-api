
#function for retrieving document count from index
get_document_count() {
    local elastic_server_url="$1"
    local index_name="$2"

    # Query Elasticsearch to get the document count
    local num_docs
    num_docs=$(curl -s -X GET "$elastic_server_url/$index_name/_search" -H 'Content-Type: application/json' -d '
    {
        "query": {
            "match_all": {}
        }
    }' | jq -r '.hits.total.value')

    echo "$num_docs"  # Print the document count
}

set_read_only_allow_delete() {
    local elastic_server_url="$1"
    local index_name="$2"
    local valueToSet="$3"

    local returnedCode
    returnedCode=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$elastic_server_url/$index_name/_settings?pretty" -H "Content-Type: application/json" -d '
    {
        "index.blocks.read_only_allow_delete": $valueToSet
    }')
    echo "$returnedCode"
}
