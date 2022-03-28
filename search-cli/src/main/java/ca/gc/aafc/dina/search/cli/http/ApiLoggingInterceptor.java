package ca.gc.aafc.dina.search.cli.http;

import java.io.IOException;

import lombok.extern.log4j.Log4j2;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Log4j2
public class ApiLoggingInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        log.info("Operation:{} onto API url: {}", request.method(),  request.url());

        return chain.proceed(request);
    }
    
}