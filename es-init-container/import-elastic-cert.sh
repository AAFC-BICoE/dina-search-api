#!/bin/bash
set -e

# Get .p12 file path from env var ES_P12_FILE (required)
: "${ES_P12_FILE:?Environment variable ES_P12_FILE must be set}"

CRT_TMP="/work/elastic-certificates.crt"
CRT_DST="/usr/local/share/ca-certificates/elastic-certificates.crt"

# Check if .p12 exists
if [ ! -f "$ES_P12_FILE" ]; then
  echo "ERROR: $ES_P12_FILE not found"
  exit 1
fi

# Convert .p12 to .crt (.pem format cert)
openssl pkcs12 -in "$ES_P12_FILE" -clcerts -nokeys -out "$CRT_TMP" -passin pass:

# Copy the .crt to system CA location
cp "$CRT_TMP" "$CRT_DST"

# Import to Alpine trust store
update-ca-certificates

echo "Imported cert from $ES_P12_FILE. curl will now trust Elasticsearch"

