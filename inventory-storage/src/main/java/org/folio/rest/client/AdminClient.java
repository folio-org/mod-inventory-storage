
package org.folio.rest.client;

import java.io.Reader;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class AdminClient {

    private final static String GLOBAL_PATH = "/admin";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public AdminClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
    }

    public AdminClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Service endpoint ^/admin/upload
     * 
     */
    public void postUpload(org.folio.rest.jaxrs.resource.AdminResource.PersistMethod persist_method, String bus_address, String file_name, javax.mail.internet.MimeMultipart MimeMultipart, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/admin/upload"+queryParams.toString());
        request.handler(responseHandler);
        if(persist_method != null) {queryParams.append("persist_method="+persist_method.toString());}
        queryParams.append("&");
        if(bus_address != null) {queryParams.append("bus_address="+bus_address);}
        queryParams.append("&");
        if(file_name != null) {queryParams.append("file_name="+file_name);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(MimeMultipart).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "multipart/form-data");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/collstats
     * 
     */
    public void putCollstats(Reader reader, Handler<HttpClientResponse> responseHandler)
        throws Exception
    {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/admin/collstats"+queryParams.toString());
        request.handler(responseHandler);
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        if(reader != null){buffer.appendString(org.apache.commons.io.IOUtils.toString(reader));}
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/loglevel
     * 
     */
    public void putLoglevel(org.folio.rest.jaxrs.resource.AdminResource.Level level, String java_package, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/admin/loglevel"+queryParams.toString());
        request.handler(responseHandler);
        if(level != null) {queryParams.append("level="+level.toString());}
        queryParams.append("&");
        if(java_package != null) {queryParams.append("java_package="+java_package);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/loglevel
     * 
     */
    public void getLoglevel(Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/admin/loglevel"+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/jstack
     * 
     */
    public void putJstack(Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/admin/jstack"+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/jstack
     * 
     */
    public void getJstack(Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/admin/jstack"+queryParams.toString());
        request.handler(responseHandler);
        request.putHeader("Accept", "text/html,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/admin/memory
     * 
     */
    public void getMemory(boolean history, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/admin/memory"+queryParams.toString());
        request.handler(responseHandler);
        queryParams.append("history="+history);
        queryParams.append("&");
        request.putHeader("Accept", "text/html,text/plain");
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
