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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import ca.gc.aafc.dina.search.common.config.YAMLConfigProperties;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;

import java.security.cert.X509Certificate;

@Configuration
public class ElasticsearchConfig {

  private static final String HOST = "server_address";
  private static final String PORT_1 = "port_1";
  private static final String PORT_2 = "port_2";


  @Value("${elasticsearch.socketTimeout}")
  private static int socketTimeout;

  @Value("${elasticsearch.connectionTimeout}")
  private static int connectionTimeout;

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
    
    SSLContext sslContext = SSLContext.getInstance("TLS");

    sslContext.init(null, new TrustManager[] { new X509TrustManager() {
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      public X509Certificate[] getAcceptedIssuers() {
          return null;
      }
  } }, null);

    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY,
    new UsernamePasswordCredentials(username, password));
    
    RestClientBuilder restClient = RestClient.builder(
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(HOST), 
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_1)),"https"
      ),
      new HttpHost(
        yamlConfigProps.getElasticsearch().get(HOST), 
        Integer.parseInt(yamlConfigProps.getElasticsearch().get(PORT_2)),"https"
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
    })
    .setRequestConfigCallback(
                new RestClientBuilder.RequestConfigCallback() {
                    @Override
                    public RequestConfig.Builder customizeRequestConfig(
                            RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder.setSocketTimeout(socketTimeout)
                                .setConnectTimeout(connectionTimeout);
                    }
                })
    ;

    // Create the elastic search transport using Jackson and the low level rest client.
    ElasticsearchTransport transport = new RestClientTransport(
      restClient.build(), new JacksonJsonpMapper());

    // Create the elastic search client.
    return new ElasticsearchClient(transport);
    
  }
}
