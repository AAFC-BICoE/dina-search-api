package ca.gc.aafc.dina.search.cli.config;

import java.util.List;
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
  private final List<IndexSettingDescriptor> indexSettings;

  public IndexSettingDescriptor getIndexSettingDescriptorForType(String type) {
    return indexSettings.stream()
        .filter(endpointDescriptor -> type.equals(endpointDescriptor.type()))
        .findFirst()
        .orElse(null);
  }

  public boolean isTypeSupportedForEndpointDescriptor(String type) {
    return indexSettings.stream()
        .anyMatch(endpointDescriptor -> type.equals(endpointDescriptor.type()));
  }

  public Stream<IndexSettingDescriptor> getFilteredEndpointDescriptorStream(Predicate<IndexSettingDescriptor> predicate) {
    return indexSettings.stream()
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

  public void addEndpointDescriptor(IndexSettingDescriptor endpointDescriptor) {
    indexSettings.add(endpointDescriptor);
  }
  public void removeEndpointDescriptor(IndexSettingDescriptor endpointDescriptor) {
    indexSettings.remove(endpointDescriptor);
  }
}
