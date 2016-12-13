
package org.folio.rest.client;

import java.math.BigDecimal;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class BooksClient {

    private final static String GLOBAL_PATH = "/books";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public BooksClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
    }

    public BooksClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Service endpoint null
     * 
     */
    public void get(String author, BigDecimal publicationYear, BigDecimal rating, String isbn, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get(GLOBAL_PATH);
        request.handler(responseHandler);
        if(author != null) {queryParams.append("author="+author);}
        queryParams.append("&");
        if(publicationYear != null) {queryParams.append("publicationYear="+publicationYear);}
        queryParams.append("&");
        if(rating != null) {queryParams.append("rating="+rating);}
        queryParams.append("&");
        if(isbn != null) {queryParams.append("isbn="+isbn);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint null
     * 
     */
    public void put(String access_token, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put(GLOBAL_PATH);
        request.handler(responseHandler);
        if(access_token != null) {queryParams.append("access_token="+access_token);}
        queryParams.append("&");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Close the client. Closing will close down any pooled connections. Clients should always be closed after use.
     * 
     */
    public void close() {
        httpClient.close();
    }

}
