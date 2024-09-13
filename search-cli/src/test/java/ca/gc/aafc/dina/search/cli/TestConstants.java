package ca.gc.aafc.dina.search.cli;

import java.nio.file.Path;

public interface TestConstants {

  int KEYCLOAK_MOCK_PORT = 8080;

  String AGENT_INDEX_MAPPING_FILE = "es-mapping/dina_agent_index_settings.json";
  String OBJECT_STORE_INDEX_MAPPING_FILE = "es-mapping/dina_object_store_index_settings.json";
  String MATERIAL_SAMPLE_INDEX_MAPPING_FILE = "es-mapping/dina_material_sample_index_settings.json";

  String AGENT_INDEX = "dina_agent_index";
  String OBJECT_STORE_INDEX = "dina_object_store_index";
  String MATERIAL_SAMPLE_INDEX = "dina_material_sample_index";

  String PERSON_TYPE = "person";
  String ORGANIZATION_TYPE = "organization";

  // the ID is matching the id in the documents
  String PERSON_DOCUMENT_ID = "bdae3b3a-b5a6-4b36-89dc-52634f9e044f";
  String PERSON_DOCUMENT_TYPE = "person";
  Path PERSON_RESPONSE_PATH = Path.of("src/test/resources/get_person_embedded_response.json");
  Path PERSON_ORG_RESPONSE_PATH = Path.of("src/test/resources/get_person_updated_org_response.json");
}
