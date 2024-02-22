## How To Add indexes

### Declare index with DINA_INDEX_DECLARATIONS environment variable

The DINA_INDEX_DECLARATIONS environment variable accepts a space separated list of index declarations to be used to identify the environment variables which will set the index file and index name.

```
DINA_INDEX_DECLARATIONS: AGENT MATERIAL_SAMPLE
```

These declarations will be used to search for environment variables using these declarations.

- DINA_AGENT_INDEX_NAME
- DINA_AGENT_INDEX_SETTINGS_FILE
- DINA_MATERIAL_SAMPLE_INDEX_NAME
- DINA_MATERIAL_SAMPLE_INDEX_SETTINGS_FILE
- DINA_MATERIAL_SAMPLE_OPTIONAL_INDEX_SETTINGS_FILE

### Declare settings file and index name for each Index declaration

* `DINA_{YOUR_INDEX_DECLARATION}_INDEX_NAME` defines the index name for this declaration.
* `DINA_{YOUR_INDEX_DECLARATION}_INDEX_SETTINGS_FILE` defines the index settings file to be used for this index.
* `DINA_{YOUR_INDEX_DECLARATION}_OPTIONAL_INDEX_SETTINGS_FILE` defines the optional index settings file to be used for this index.

## Indices migration

When migrating from a non-aliased indices environment, the script expects PREPARE_ENV environment variable to be declared and not empty.

The result of this run are reindexed indices that carry the same documents as previously but are named with a timestamp of when they are created as well as an alias that matches the index prefix name.
This process also loads schemas that carry a _meta block containing the schema version, which will be used to assess if future reindexing is required.

### Example of new index/alias pairs:
```
Alias                       Index
dina_loan_transaction_index dina_loan_transaction_index_20240221183205

dina_storage_index          dina_storage_index_20240221183159
         
dina_agent_index            dina_agent_index_20240221183153
           
dina_material_sample_index  dina_material_sample_index_20240221183156

dina_object_store_index     dina_object_store_index_20240221183202
```

Once the environment has aliased indices, as shown above, the container should be executed without the PREPARE_ENV environment variable. At which case it will assess at start time if indices 
require reindexing, that is if their corresponding schema remote version is lower than the provided local schema.


