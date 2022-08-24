package ca.gc.aafc.dina.search.ws.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import ca.gc.aafc.dina.search.common.config.YamlPropertySourceFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@ConfigurationProperties
@PropertySource(value = "classpath:mappings.yml", factory = YamlPropertySourceFactory.class)
@RequiredArgsConstructor
@Getter
public class MappingObjectAttributes {
  private final Map<String, List<MappingAttribute>> mappings;

  /**
   * Get the attributes list of a specific type from the mapping configuration.
   * @param type
   * @return the mapping attributes list or null
   */
  public List<MappingAttribute> getAttributes(String type) {
    if (mappings == null) {
      return null;
    }
    return mappings.get(type);
  }

  /**
   * Get the attribute of a specific type from the mapping configuration.
   * @param type
   * @param attributeName
   * @return the mapping attributes or null
   */
  public MappingAttribute getAttribute(String type, String attributeName) {
    List<MappingAttribute> allAttribute = getAttributes(type);
    if(allAttribute == null) {
      return null;
    }

    for (MappingAttribute ma : allAttribute) {
      if (attributeName.equals(ma.getName())) {
        return ma;
      }
    }
    return null;
  }

}
