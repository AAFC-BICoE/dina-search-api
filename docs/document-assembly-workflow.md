# Document Assembly Workflow in Search-CLI

## Overview

The search-cli component is responsible for retrieving DINA documents from various APIs, assembling them with related documents, and indexing them into Elasticsearch. This document describes the complete workflow of document assembly, indexing, and re-indexing, with a focus on the relationship processing mechanisms.

## Table of Contents

1. [Architecture Components](#architecture-components)
2. [Indexing Workflow](#indexing-workflow)
3. [Document Assembly Process](#document-assembly-process)
4. [Relationship Processing](#relationship-processing)
   - [External Relationships](#external-relationships-processexternalrelationships)
   - [Reverse Relationships](#reverse-relationships-processreverserelationships)
   - [Augmented Relationships](#augmented-relationships-processaugmentedrelationships)
5. [Re-indexing Triggers](#re-indexing-triggers)
6. [Configuration](#configuration)
7. [Examples](#examples)

---

## Architecture Components

### Key Classes

- **`DocumentManager`**: Orchestrates the entire indexing workflow - retrieves documents, coordinates assembly, and sends them to Elasticsearch.
- **`IndexableDocumentHandler`**: Handles the document assembly logic, including all relationship processing methods.
- **`DocumentProcessor`**: Translates RabbitMQ messages into document operations (index, update, delete).
- **`ElasticSearchDocumentIndexer`**: Handles low-level Elasticsearch operations.
- **`DinaApiAccess`**: HTTP client for fetching documents from DINA APIs.

### Configuration Records

- **`ApiResourceDescriptor`**: Maps a JSON:API type to a URL endpoint.
- **`IndexSettingDescriptor`**: Defines indexing configuration for each document type, including:
  - `indexName`: Target Elasticsearch index
  - `type`: JSON:API document type
  - `relationships`: List of relationships to include
  - `relationshipsType`: Types that may appear in relationships
  - `optionalFields`: Additional fields to request from APIs
  - `reverseRelationships`: List of reverse relationship configurations
  - `augmentedRelationships`: List of augmented relationship configurations

---

## Indexing Workflow

The complete indexing workflow follows these steps:

```
Message Received → Document Processor → Document Manager → Index Document
                                                          ↓
                                                     Assemble Document
                                                          ↓
                                                   Elasticsearch Index
```

### Step-by-Step Process

1. **Message Reception** (`DocumentOperationNotificationConsumer`)
   - Listens to RabbitMQ queue for `DocumentOperationNotification` messages
   - Messages contain: `documentType`, `documentId`, `operationType` (ADD, UPDATE, DELETE, REFRESH)

2. **Message Processing** (`DocumentProcessor.processMessage`)
   - Validates message attributes
   - Routes to appropriate operation:
     - **ADD/UPDATE/REFRESH**: Calls `documentManager.indexDocument()`
     - **DELETE**: Calls `documentManager.deleteDocument()`
   - Checks if the updated document affects other indices (embedded document processing)

3. **Document Retrieval** (`DocumentManager.indexDocument`)
   - Fetches the document from the appropriate API using `ApiResourceDescriptor`
   - Requests relationships based on `IndexSettingDescriptor.relationships`
   - Requests optional fields based on `IndexSettingDescriptor.optionalFields`

4. **Document Assembly** (`IndexableDocumentHandler.assembleDocument`)
   - Parses the raw JSON:API document
   - Processes relationships (see below)
   - Cleans up metadata
   - Returns assembled document ready for indexing

5. **Elasticsearch Indexing** (`ElasticSearchDocumentIndexer.indexDocument`)
   - Indexes the assembled document into the configured index
   - Uses the document ID as the Elasticsearch document ID

---

## Document Assembly Process

The `assembleDocument` method in `IndexableDocumentHandler` is the heart of the assembly process:

```java
public ObjectNode assembleDocument(String rawPayload)
```

### Assembly Steps

1. **Parse JSON:API Document**
   - Extract the `data` section
   - Extract or create the `included` section (array of related documents)

2. **Process External Relationships** (`processExternalRelationships`)
   - Fetch relationship documents from external APIs
   - Add them to the `included` array

3. **Apply Node Transformations** (`applyIncludedNodeTransformation`)
   - Apply field-specific transformations (e.g., coordinate extraction for geospatial fields)

4. **Process Reverse Relationships** (`processReverseRelationships`)
   - Find documents that reference this document
   - Add them to the `included` array

5. **Process Augmented Relationships** (`processAugmentedRelationships`)
   - Re-fetch documents already in `included` with nested relationship references
   - Replace entries with enriched versions

6. **Clean Metadata** (`processMeta`)
   - Remove unnecessary metadata fields
   - Prepare document for indexing

7. **Return Assembled Document**
   - Document with complete `data`, `included`, and `meta` sections

---

## Relationship Processing

### External Relationships (`processExternalRelationships`)

#### Purpose
Fetches documents referenced in the main document's `relationships` section from external APIs and adds them to the `included` array.

#### How It Works

1. **Iterates through relationships** in the document's `relationships` section
2. **For each relationship**:
   - Extracts type and ID from the relationship data (handles both single objects and arrays)
   - Checks if the document is already in the `included` array (avoids duplicates)
   - Verifies an `ApiResourceDescriptor` exists for the type
3. **Fetches the document** from the appropriate API:
   - Uses `fetchDocument(type, id, Optional.of(Set.of()))` - note the empty set for includes
   - This fetches the document without any nested relationships (shallow fetch)
4. **Adds to included array**: Extracts the `data` section and appends to parent's `included` array

#### Example Scenario

A `material-sample` has a relationship to a `person` (preparedBy):

```json
{
  "data": {
    "type": "material-sample",
    "id": "123",
    "relationships": {
      "preparedBy": {
        "data": { "type": "person", "id": "456" }
      }
    }
  }
}
```

- `processExternalRelationships` detects the `person` relationship
- Fetches `person` with ID `456` from the agent API
- Adds the complete person document to the `included` array

#### Configuration

External relationships are processed automatically based on the relationships present in the document. No special configuration is required in `endpoints.yml` - the system only needs an `ApiResourceDescriptor` for the relationship type.

---

### Reverse Relationships (`processReverseRelationships`)

#### Purpose
Finds documents in other APIs that reference the current document and includes them in the indexed document. This handles the "opposite direction" of relationships.

#### How It Works

1. **Looks up reverse relationships** in `IndexSettingDescriptor.reverseRelationships`
2. **For each configured reverse relationship**:
   - Queries the related API using a filter: `filter[relationshipName]=documentId`
   - Example: For a `collecting-event` with reverse relationship to `run-summary`, queries: `GET /run-summary?filter[materialSample]=123`
3. **Processes results**:
   - The API returns an array of documents that reference the current document
   - Each document is added to the `included` array
4. **Creates included array if needed**: If the document has no `included` section yet, creates one

#### Example Scenario

A `material-sample` is referenced by multiple `run-summary` records:

Configuration in `endpoints.yml`:
```yaml
indexSettings:
  - indexName: dina_material_sample_index
    type: material-sample
    reverseRelationships:
      - type: run-summary
        relationshipName: materialSample
```

When indexing `material-sample` ID `123`:
1. Queries: `GET /run-summary?filter[materialSample]=123`
2. Returns all run-summary records that reference this material-sample
3. Adds all returned run-summary documents to the material-sample's `included` array

#### Why Use Reverse Relationships?

- **Data Denormalization**: Embeds related data directly in the search index for faster queries
- **Bidirectional Navigation**: Allows searching from either direction of a relationship
- **Completeness**: Ensures all related information is available without additional API calls during search

#### Configuration

Reverse relationships are configured in `endpoints.yml` under `indexSettings`:

```yaml
reverseRelationships:
  - type: run-summary              # Type of document that references this one
    relationshipName: materialSample  # Name of the relationship field in the remote document
```

---

### Augmented Relationships (`processAugmentedRelationships`)

#### Purpose
Enriches documents already in the `included` array by re-fetching them with nested relationship references. This allows one level of deep relationship nesting while preventing mapping explosion in Elasticsearch.

#### How It Works

1. **Looks up augmented relationships** in `IndexSettingDescriptor.augmentedRelationships`
2. **For each configured augmented relationship**:
   - Finds the relationship in the parent document's `relationships` section
   - Extracts type/id references (handles both single and array relationships)
3. **For each referenced document**:
   - Locates the document in the `included` array by type and ID
   - Re-fetches the document with specified nested includes: `fetchDocument(type, id, Optional.of(nestedRelationships))`
   - This fetch includes relationship references (type/id) in the `relationships` section
   - **Important**: The nested documents' `included` sections are NOT added (prevents explosion)
4. **Replaces the document** in the `included` array with the enriched version

#### Example Scenario

A `material-sample` has a `collectingEvent`, and that collecting-event has `collectors` (persons):

Configuration in `endpoints.yml`:
```yaml
augmentedRelationships:
  - relationshipName: collectingEvent
    nestedRelationships:
      - collectors
```

**Without augmented relationships**, the material-sample's included section would contain:
```json
{
  "included": [
    {
      "type": "collecting-event",
      "id": "ce-1",
      "attributes": { "eventType": "field collection" }
      // No relationships section
    }
  ]
}
```

**With augmented relationships**, the collecting-event is re-fetched with the `collectors` include:
```json
{
  "included": [
    {
      "type": "collecting-event",
      "id": "ce-1",
      "attributes": { "eventType": "field collection" },
      "relationships": {
        "collectors": {
          "data": [
            { "type": "person", "id": "p-1" },
            { "type": "person", "id": "p-2" }
          ]
        }
      }
    }
  ]
}
```

Note that the actual person documents are NOT added to the included array - only the references (type/id) are present in the relationships section.

#### Why Use Augmented Relationships?

- **Nested Navigation**: Allows Elasticsearch queries to access nested relationships (e.g., search for material-samples by collector's name)
- **Controlled Denormalization**: Includes relationship references without fetching the full nested documents
- **Mapping Explosion Prevention**: By including only references and stripping the nested `included` sections, keeps the Elasticsearch mapping manageable

#### Configuration

Augmented relationships are configured in `endpoints.yml` under `indexSettings`:

```yaml
augmentedRelationships:
  - relationshipName: collectingEvent   # Relationship from parent document
    nestedRelationships:                # Relationships to include from the nested document
      - collectors
      - preparationMethod
```

---

## Re-indexing Triggers

### Embedded Document Processing

When a document is updated, it may affect other documents that include it in their `included` section. The system automatically handles this through **embedded document processing**.

#### How It Works

1. **After processing the primary document** (index, update, or delete), `DocumentProcessor` checks if the document type is used in any relationships:
   ```java
   List<String> indices = documentManager.getIndexForRelationshipType(docOpMessage.getDocumentType());
   ```

2. **For each affected index**, searches Elasticsearch for documents that contain the updated document in their `included` section:
   - Uses `ElasticSearchDocumentIndexer.search()` to find documents by type and ID
   - Supports pagination for large result sets

3. **Re-indexes each found document**:
   - Fetches fresh data from the source API
   - Re-runs the complete assembly process
   - Updates the Elasticsearch index

#### Example Scenario

When a `person` document is updated:

1. System determines that `person` is used in relationships by:
   - `material-sample` (via `preparedBy` relationship)
   - `metadata` (via `acMetadataCreator` relationship)

2. Searches both indices for documents containing this person

3. Re-indexes all found documents, which will:
   - Fetch fresh person data through `processExternalRelationships`
   - Update the `included` section with the new person attributes

#### Performance Considerations

- **Cascading Updates**: A single person update could trigger re-indexing of thousands of material-samples
- **Pagination**: Large result sets are processed in pages (default: 50 documents per page)
- **Point-in-Time (PIT)**: For large re-indexing jobs, uses Elasticsearch PIT API for consistent pagination

---

## Configuration

### endpoints.yml Structure

The `endpoints.yml` file configures all aspects of document indexing:

```yaml
# API Resources - Maps types to API URLs
apiResources:
  - type: material-sample
    url: ${COLLECTION_API_URL}/material-sample
  - type: person
    url: ${AGENT_API_URL}/person
    enabled: ${AGENT_API_ENABLED:true}  # Optional: can be disabled

# Index Settings - Defines how each document type is indexed
indexSettings:
  - indexName: dina_material_sample_index
    type: material-sample
    
    # Regular relationships to include (fetched with parent document)
    relationships:
      - collectingEvent
      - preparedBy
      - collection
    
    # Types that may appear in relationships (for validation)
    relationshipsType:
      - person
      - collecting-event
      - metadata
    
    # Optional fields to request from APIs
    optionalFields:
      material-sample:
        - hierarchy
      storage-unit-usage:
        - cellNumber
    
    # Reverse relationships (documents that reference this one)
    reverseRelationships:
      - type: run-summary
        relationshipName: materialSample
    
    # Augmented relationships (nested relationship references)
    augmentedRelationships:
      - relationshipName: collectingEvent
        nestedRelationships:
          - collectors
          - preparationMethod
```

---

## Examples

### Example 1: Simple Indexing with External Relationships

**Scenario**: Index a material-sample that has a preparedBy relationship to a person.

**Input Message**:
```json
{
  "documentType": "material-sample",
  "documentId": "ms-123",
  "operationType": "ADD"
}
```

**Process**:
1. DocumentProcessor receives message
2. DocumentManager fetches material-sample from collection API
3. IndexableDocumentHandler.assembleDocument:
   - Detects `preparedBy` relationship to person `p-456`
   - `processExternalRelationships` fetches person from agent API
   - Adds person to `included` array
4. Document indexed to `dina_material_sample_index`

**Result**:
```json
{
  "data": {
    "type": "material-sample",
    "id": "ms-123",
    "attributes": { "materialSampleName": "Sample A" },
    "relationships": {
      "preparedBy": {
        "data": { "type": "person", "id": "p-456" }
      }
    }
  },
  "included": [
    {
      "type": "person",
      "id": "p-456",
      "attributes": { "displayName": "John Doe" }
    }
  ]
}
```

### Example 2: Reverse Relationships

**Scenario**: Index a material-sample that is referenced by run-summary records.

**Configuration**:
```yaml
reverseRelationships:
  - type: run-summary
    relationshipName: materialSample
```

**Process**:
1. Material-sample `ms-123` is being indexed
2. `processReverseRelationships` queries: `GET /run-summary?filter[materialSample]=ms-123`
3. Returns 3 run-summary records
4. All 3 records are added to the material-sample's `included` array

**Result**: Material-sample document includes all run-summaries that reference it.

### Example 3: Augmented Relationships with Nested References

**Scenario**: Index a material-sample with a collecting-event that has collectors.

**Configuration**:
```yaml
relationships:
  - collectingEvent
augmentedRelationships:
  - relationshipName: collectingEvent
    nestedRelationships:
      - collectors
```

**Process**:
1. Material-sample fetched with `collectingEvent` relationship
2. Collecting-event `ce-789` added to `included` (without relationships)
3. `processAugmentedRelationships`:
   - Re-fetches collecting-event with `collectors` include
   - New version has `relationships.collectors` section with person references
   - Replaces collecting-event in `included` array

**Result**:
```json
{
  "data": {
    "type": "material-sample",
    "id": "ms-123",
    "relationships": {
      "collectingEvent": {
        "data": { "type": "collecting-event", "id": "ce-789" }
      }
    }
  },
  "included": [
    {
      "type": "collecting-event",
      "id": "ce-789",
      "attributes": { "eventType": "field collection" },
      "relationships": {
        "collectors": {
          "data": [
            { "type": "person", "id": "p-1" },
            { "type": "person", "id": "p-2" }
          ]
        }
      }
    }
  ]
}
```

Note: The actual person documents (p-1, p-2) are NOT in the included array - only their references are present.

### Example 4: Cascading Re-indexing

**Scenario**: A person is updated, triggering re-indexing of all material-samples that reference them.

**Input Message**:
```json
{
  "documentType": "person",
  "documentId": "p-456",
  "operationType": "UPDATE"
}
```

**Process**:
1. Person `p-456` is indexed to `dina_agent_index`
2. `DocumentProcessor` checks for embedded usage
3. Finds that `person` is used by `material-sample` index
4. Searches `dina_material_sample_index` for documents containing person `p-456`
5. Finds 50 material-samples
6. Re-indexes all 50 material-samples:
   - Each material-sample is fetched fresh
   - Assembly process runs again
   - Updated person data is included via `processExternalRelationships`

**Result**: All 50 material-samples now have the updated person data in their `included` sections.

---

## Summary

The document assembly workflow in search-cli is a sophisticated process that:

1. **Retrieves documents** from multiple DINA APIs based on configuration
2. **Processes relationships** in three different ways:
   - **External relationships**: Fetches related documents from external APIs
   - **Reverse relationships**: Finds documents that reference the current document
   - **Augmented relationships**: Enriches documents with nested relationship references
3. **Assembles complete documents** with all related data in the `included` section
4. **Indexes into Elasticsearch** for fast search and retrieval
5. **Handles cascading updates** by re-indexing documents when their embedded content changes

This design enables rich, denormalized search documents while maintaining reasonable Elasticsearch mapping sizes and keeping data fresh through automatic re-indexing.

---

*This documentation was generated with AI assistance and reviewed for accuracy.*
