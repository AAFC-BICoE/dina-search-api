
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

