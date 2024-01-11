package ca.gc.aafc.dina.search.ws.container;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;

public class HttpWaitStrategyWithSsl extends AbstractWaitStrategy {

    private final HttpClient client;

    public HttpWaitStrategyWithSsl(HttpClient client) {
        this.client = client;
    }

    @Override
    protected void waitUntilReady() {
        
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
        
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
        
            @Override
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        if(sslContext != null) {
            Builder clientBuilder = HttpClient.newBuilder();
            clientBuilder.version(Version.HTTP_2);
            clientBuilder.sslContext(sslContext);
            HttpClient httpClient = clientBuilder.build();
            String url = "https://" + waitStrategyTarget.getHost() + ":" + waitStrategyTarget.getMappedPort(9200);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
                
            while (true) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200 || response.statusCode() == 401) {
                        break;
                    }
                } catch (IOException | InterruptedException e) {
                    // handle exception
                }
            }
        }
    }
}
