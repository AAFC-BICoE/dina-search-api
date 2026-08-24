package ca.gc.aafc.dina.search.ws.repository;

import ca.gc.aafc.dina.search.ws.config.ApiInfoConfiguration;
import ca.gc.aafc.dina.search.ws.config.IndicesProperty;
import ca.gc.aafc.dina.search.ws.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.ws.services.IndexMappingResponse;
import ca.gc.aafc.dina.search.ws.services.SearchService;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.gc.aafc.dina.dto.ApiInfoDto;
import ca.gc.aafc.dina.security.auth.DinaAdminAuthorizationService;

import static com.toedter.spring.hateoas.jsonapi.JsonApiModelBuilder.jsonApiModel;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "${dina.apiPrefix:}", produces = JSON_API_VALUE)
public class ApiInfoRepository {

  private final DinaAdminAuthorizationService dinaAdminAuthorizationService;
  private final SearchService searchService;

  private final ApiInfoConfiguration apiInfoConfiguration;
  private final IndicesProperty indicesProperty;

  public ApiInfoRepository(DinaAdminAuthorizationService dinaAdminAuthorizationService,
      SearchService searchService,ApiInfoConfiguration apiInfoConfiguration, IndicesProperty indicesProperty) {
    this.dinaAdminAuthorizationService = dinaAdminAuthorizationService;
    this.searchService = searchService;
    this.apiInfoConfiguration = apiInfoConfiguration;
    this.indicesProperty = indicesProperty;
  }

  @GetMapping(ApiInfoDto.TYPE_NAME)
  public ResponseEntity<RepresentationModel<?>> onApiInfo() {
    // admin only
    dinaAdminAuthorizationService.authorize();

    ApiInfoDto infoDto = apiInfoConfiguration.buildApiInfoDto();
    Map<String, Object> moduleInfo = new HashMap<>();
    Map<String, Object> indicesInfo = new HashMap<>();
    moduleInfo.put("indices", indicesInfo);

    boolean isSearchIndexReachable = searchService.isSearchIndexReachable();
    moduleInfo.put("isSearchIndexReachable", isSearchIndexReachable);

    if (isSearchIndexReachable) {
      for (String index : indicesProperty.getIndices()) {
        boolean online = true;
        String schemaVersion = "";
        try {
          IndexMappingResponse mappingResponse = searchService.getIndexMapping(index);
          schemaVersion = mappingResponse.getSchemaVersion();
        } catch (SearchApiException ex) {
          online = false;
        }
        indicesInfo.put(index, Map.of("online", online, "schemaVersion", schemaVersion));
      }
    } else {
      infoDto.setAttentionRequired(true);
    }

    infoDto.setModuleInfo(moduleInfo);
    return ResponseEntity.ok(jsonApiModel().model(RepresentationModel.of(infoDto)).build());
  }
}
