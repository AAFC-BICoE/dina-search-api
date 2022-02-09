package ca.gc.aafc.dina.search.cli.indexing;

import ca.gc.aafc.dina.search.cli.containers.DinaAPIMockServerContainer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
public class DinaAPIMockServerTest {

  // UUIDs are declared in mock-server/initializerJson.json
  public static final String PERSON_UUID = "899a6de1-4baa-4032-815d-12f50b8516ff";
  public static final String MATERIAL_SAMPLE_UUID = "65214cb9-88ea-4aa2-a1a8-259751df3d9b";

  @Container
  private static final DinaAPIMockServerContainer DINA_API_MOCK_CONTAINER = new DinaAPIMockServerContainer();

  private final OkHttpClient client = new OkHttpClient().newBuilder().build();

  @BeforeAll
  static void beforeAll() {
    DINA_API_MOCK_CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    DINA_API_MOCK_CONTAINER.stop();
  }

  @Test
  public void agentApi_onGetPerson_ApiResponseReceived() throws IOException {

    Request getAgent = new Request.Builder()
        .url(DINA_API_MOCK_CONTAINER.getEndpoint() + "/person/" + PERSON_UUID)
        .get().build();

    Response response = client.newCall(getAgent).execute();
    assertEquals(200, response.code());
  }

  @Test
  public void collectionApi_onGetMaterialSample_ApiResponseReceived() throws IOException {
    Request getMaterialSample = new Request.Builder()
        .url(DINA_API_MOCK_CONTAINER.getEndpoint() + "/material-sample/" + MATERIAL_SAMPLE_UUID + "?include=preparedBy")
        .get().build();

    Response response = client.newCall(getMaterialSample).execute();
    assertEquals(200, response.code());
  }

}
