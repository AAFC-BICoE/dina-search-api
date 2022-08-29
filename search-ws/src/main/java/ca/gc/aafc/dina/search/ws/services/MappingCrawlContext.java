package ca.gc.aafc.dina.search.ws.services;

import ca.gc.aafc.dina.search.ws.config.MappingObjectAttributes;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Package protected mapping crawling context.
 * The context is immutable
 */
@RequiredArgsConstructor
@Builder
@Getter
class MappingCrawlContext {

  private final MappingObjectAttributes mappingConfiguration;
  private final String documentType;

}
