package ca.gc.aafc.dina.search.cli.config;

import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class EndpointDescriptor {
  private String targetUrl;
  private List<String> relationships;
}
