package ca.gc.aafc.dina.search.cli.config;

import ca.gc.aafc.dina.client.config.OpenIdConnectConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "http-client")
@Getter
@Setter
@NoArgsConstructor
public class HttpClientConfig extends OpenIdConnectConfig {
}
