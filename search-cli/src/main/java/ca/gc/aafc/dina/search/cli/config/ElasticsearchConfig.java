package ca.gc.aafc.dina.search.cli.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.springframework.context.annotation.Primary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.nio.file.Files;


import java.nio.file.Path;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import org.apache.http.ssl.*;
import org.apache.http.ssl.SSLContexts;
import javax.net.ssl.SSLContext;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@Configuration
public class ElasticsearchConfig {

  private static final String HOST = "server_address";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";

  private final YAMLConfigProperties yamlConfigProps;

  public ElasticsearchConfig(YAMLConfigProperties yamlConfigProps) {
    this.yamlConfigProps = yamlConfigProps;
  }

  @Bean
  @Primary
  public ElasticsearchClient customElasticsearchClient() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    // Create low level client for the elastic search client to use.

    String username = "elastic";
    String password = "changeme";
    
    Path caCertificatePath = Paths.get("src/test/resources/http_ca.crt");
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    Certificate trustedCa;
    try (InputStream is = Files.newInputStream(caCertificatePath)) {
        trustedCa = factory.generateCertificate(is);
    }
    KeyStore trustStore = KeyStore.getInstance("pkcs12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry("ca", trustedCa);
    SSLContextBuilder sslContextBuilder = SSLContexts.custom()
        .loadTrustMaterial(trustStore, null);
    final SSLContext sslContext = sslContextBuilder.build();
   
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY,
    new UsernamePasswordCredentials(username, password));
    
    RestClientBuilder restClient = RestClient.builder(
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(HOST), 
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_1))
      ),
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(HOST), 
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_2))
      )
    )
    .setHttpClientConfigCallback(new HttpClientConfigCallback() {
        @Override
        public HttpAsyncClientBuilder customizeHttpClient(
                HttpAsyncClientBuilder httpClientBuilder) {
            httpClientBuilder.disableAuthCaching();
            return httpClientBuilder
                .setDefaultCredentialsProvider(credentialsProvider)
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
    });

    // Create the elastic search transport using Jackson and the low level rest client.
    ElasticsearchTransport transport = new RestClientTransport(
      restClient.build(), new JacksonJsonpMapper());

    // Create the elastic search client.
    return new ElasticsearchClient(transport);
    
  }
}
