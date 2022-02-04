package ca.gc.aafc.dina.search.cli.config;

import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class EndpointDescriptor {
  private String targetUrl;
  private String indexName;
  private List<String> relationships;
}
