package ca.gc.aafc.dina.search.cli.commands;

import ca.gc.aafc.dina.search.cli.config.IndexSettingDescriptor;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import ca.gc.aafc.dina.search.cli.config.ServiceEndpointProperties;
import ca.gc.aafc.dina.search.cli.exceptions.SearchApiException;
import ca.gc.aafc.dina.search.cli.http.OpenIDHttpClient;
import ca.gc.aafc.dina.search.cli.indexing.IndexableDocumentHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class GetDocument {

  private final OpenIDHttpClient aClient;
  private final ServiceEndpointProperties svcEndpointProps;
  private final IndexableDocumentHandler indexableDocumentHandler;
  
  public GetDocument(OpenIDHttpClient aClient, ServiceEndpointProperties svcEndpointProps,
              IndexableDocumentHandler indexableDocumentHandler) {

    this.aClient = aClient;
    this.svcEndpointProps = svcEndpointProps;
    this.indexableDocumentHandler = indexableDocumentHandler;
  }

  @Command(name = "get-document", description = "Get Document from a specified endpoint")
  public String getDocument(
                  @Option(shortName = 't', longName = "type", description = "Document type") String type,
                  @Option(shortName = 'i', longName = "documentId", description = "Unique object identifier") String documentId,
                  @Option(longName = "assemble", description = "Assemble a document", defaultValue = "false") boolean assemble) {

    String msg = null;
    if (!svcEndpointProps.isTypeSupportedForEndpointDescriptor(type)) {
      msg = "Unsupported endpoint type:" + type;
      log.error(msg);
      return msg;
    }

    try {
      IndexSettingDescriptor indexSettingDescriptor = svcEndpointProps.getIndexSettingDescriptorForType(type);
      msg = aClient.getDataById(svcEndpointProps.getApiResourceDescriptorForType(type),
          indexSettingDescriptor.relationships(), indexSettingDescriptor.optionalFields(), documentId);

      if (assemble) {
        msg = IndexableDocumentHandler.OM.writeValueAsString(indexableDocumentHandler.assembleDocument(msg));
      }
    } catch (SearchApiException | JacksonException ex) {
      log.error("Error during operation execution", ex);
    }

    return msg;
  }
}
