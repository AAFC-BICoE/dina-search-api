# Geo-Shape Search Example for `siteGeom`

## Overview

This document demonstrates how to perform a **geo-shape search** on **sites** using the `siteGeom` field on the collecting-event index.  

---

## Example: Polygon Geo Search

The following query returns collecting events where the `siteGeom` **intersects with a polygon drawn by the user**:

```json
GET /search?indexName=dina_collecting_event_index
{
  "query": {
    "nested": {
      "path": "included",
      "query": {
        "geo_shape": {
          "included.attributes.siteGeom": {
            "shape": {
              "type": "polygon",
              "coordinates": [
                [
                  [-73.60, 45.50],
                  [-73.55, 45.50],
                  [-73.55, 45.55],
                  [-73.60, 45.55],
                  [-73.60, 45.50]
                ]
              ]
            },
            "relation": "intersects"
          }
        }
      }
    }
  },
  "size": 25,
  "from": 0,
  "sort": [
    { "data.attributes.createdOn": { "order": "desc" } }
  ],
  "_source": {
    "includes": [
      "data.id",
      "data.type",
      "data.attributes.dwcFieldNumber",
      "data.attributes.dwcRecordNumber",
      "data.attributes.otherRecordNumbers",
      "data.attributes.startEventDateTime",
      "data.attributes.endEventDateTime",
      "data.attributes.verbatimEventDateTime",
      "data.attributes.group",
      "data.attributes.createdBy",
      "data.attributes.createdOn",
      "included.id",
      "included.type"
    ]
  }
}
```

## Explanation

| Field                          | Description                                               |
| ------------------------------ | --------------------------------------------------------- |
| `nested`                       | Required because `included` is mapped as a nested field   |
| `included.attributes.siteGeom` | The geo_shape field representing the site geometry        |
| `shape`                        | GeoJSON geometry representing the shape drawn by the user |
| `polygon`                      | Shape drawn in the UI                                     |
| `relation`                     | Spatial relation operator (default: `intersects`)         |

## Spatial Relation Options

The `relation` parameter defines how the query shape interacts with indexed shapes:

| Relation     | Description                                                |
| ------------ | ---------------------------------------------------------- |
| `intersects` | Returns shapes that intersect the query geometry (default) |
| `within`     | Returns shapes completely inside the query geometry       |
| `contains`   | Returns shapes that contain the query geometry            |
| `disjoint`   | Returns shapes that do not intersect the query geometry   |
