package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConfigurationProperties
@PropertySource(value = "classpath:endpoints.yml", factory = YamlPropertySourceFactory.class)
@RequiredArgsConstructor
@Getter
public class ServiceEndpointProperties {

  private final List<ApiResourceDescriptor> apiResources;
  private final Map<String, EndpointDescriptor> endpoints;

  public EndpointDescriptor getEndpointDescriptorForType(String type) {
    return endpoints.values().stream()
        .filter(endpointDescriptor -> type.equals(endpointDescriptor.getType()))
        .findFirst()
        .orElse(null);
  }

  public boolean isTypeSupportedForEndpointDescriptor(String type) {
    return endpoints.values().stream()
        .anyMatch(endpointDescriptor -> type.equals(endpointDescriptor.getType()));
  }

  public Stream<EndpointDescriptor> getFilteredEndpointDescriptorStream(Predicate<EndpointDescriptor> predicate) {
    return endpoints.values().stream()
        .filter(predicate);
  }

  public ApiResourceDescriptor getApiResourceDescriptorForType(String type) {
    return apiResources.stream()
        .filter(ad -> ad.type().equals(type))
        .findFirst()
        .orElse(null);
  }

  public void addApiResourceDescriptor(ApiResourceDescriptor apiResourceDescriptor) {
    apiResources.add(apiResourceDescriptor);
  }
  public void removeApiResourceDescriptor(ApiResourceDescriptor apiResourceDescriptor) {
    apiResources.remove(apiResourceDescriptor);
  }

  public void addEndpointDescriptor(String key, EndpointDescriptor endpointDescriptor) {
    endpoints.put(key,endpointDescriptor);
  }
  public void removeEndpointDescriptor(String key) {
    endpoints.remove(key);
  }
}
