package ca.gc.aafc.dina.search.cli.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;

public class YamlPropertySourceFactory extends DefaultPropertySourceFactory {

  @Override
  public PropertySource<?> createPropertySource(String name, EncodedResource resource)
      throws IOException {

    if (resource == null) {
      throw new IllegalArgumentException("Encoded resource must not be null");
    }

    CompositePropertySource propertySource = new CompositePropertySource(
        resource.getResource().getFilename());
    new YamlPropertySourceLoader()
        .load(resource.getResource().getFilename(), resource.getResource())
        .stream()
        .forEach(propertySource::addPropertySource);
    return propertySource;
  }
}
