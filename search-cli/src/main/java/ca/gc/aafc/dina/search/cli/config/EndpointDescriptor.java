package ca.gc.aafc.dina.search.cli.config;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Data
public class EndpointDescriptor {
  private String targetUrl;
  private List<String> relationships;
}
