package ca.gc.aafc.dina.search.cli.commands.messaging;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class DocumentInfo {
  private final String type;
  private final String indexName;
}
