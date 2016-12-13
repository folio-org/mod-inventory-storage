
package org.folio.rest.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class InstanceStorageClient {

    private final static String GLOBAL_PATH = "/instance_storage";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public InstanceStorageClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
    }

    public InstanceStorageClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Service endpoint ^/instance_storage/instance/{instanceId}
     * 
     */
    public void postInstanceId(String instanceId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/instance_storage/instance/"+instanceId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/instance_storage/instance/{instanceId}
     * 
     */
    public void getInstanceId(String instanceId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/instance_storage/instance/"+instanceId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/instance_storage/instance/{instanceId}
     * 
     */
    public void deleteInstanceId(String instanceId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.delete("/instance_storage/instance/"+instanceId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/instance_storage/instance/{instanceId}
     * 
     */
    public void putInstanceId(String instanceId, String lang, org.folio.rest.jaxrs.model.Instance Instance, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/instance_storage/instance/"+instanceId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Instance).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/instance_storage/instance
     * 
     */
    public void getInstance(String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/instance_storage/instance"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/instance_storage/instance
     * 
     */
    public void postInstance(String lang, org.folio.rest.jaxrs.model.Instance Instance, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/instance_storage/instance"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Instance).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
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
