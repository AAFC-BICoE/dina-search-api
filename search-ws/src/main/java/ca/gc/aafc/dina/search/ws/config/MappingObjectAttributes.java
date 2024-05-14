package ca.gc.aafc.dina.search.ws.config;

import java.util.List;
import java.util.Map;

import ca.gc.aafc.dina.property.YamlPropertyLoaderFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties
@PropertySource(value = "classpath:mappings.yml", factory = YamlPropertyLoaderFactory.class)
@RequiredArgsConstructor
@Getter
@Validated
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

  /**
   * Get the Object attribute of a specific type from the mapping configuration.
   * Object attribute represents the higher level of the attribute.
   * This method will only try to match the first level. managedAttributes.test_2 will match managedAttributes.
   * @param type
   * @param attributeName
   * @return the mapping attributes or null
   */
  public MappingAttribute getObjectAttribute(String type, String attributeName) {
    List<MappingAttribute> allAttribute = getAttributes(type);
    if(allAttribute == null) {
      return null;
    }

    String potentialObjectName = StringUtils.substringBefore(attributeName, ".");
    for (MappingAttribute ma : allAttribute) {
      if (potentialObjectName.equals(ma.getName()) && ma.getType().equals("object")) {
        return ma;
      }
    }
    return null;
  }

}
