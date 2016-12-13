
package org.folio.rest.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class ItemStorageClient {

    private final static String GLOBAL_PATH = "/item_storage";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public ItemStorageClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
    }

    public ItemStorageClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Service endpoint ^/item_storage/item
     * 
     */
    public void getItem(String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/item_storage/item"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/item_storage/item
     * 
     */
    public void postItem(String lang, org.folio.rest.jaxrs.model.Item Item, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/item_storage/item"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Item).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/item_storage/item/{itemId}
     * 
     */
    public void postItemId(String itemId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/item_storage/item/"+itemId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/item_storage/item/{itemId}
     * 
     */
    public void getItemId(String itemId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/item_storage/item/"+itemId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/item_storage/item/{itemId}
     * 
     */
    public void deleteItemId(String itemId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.delete("/item_storage/item/"+itemId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/item_storage/item/{itemId}
     * 
     */
    public void putItemId(String itemId, String lang, org.folio.rest.jaxrs.model.Item Item, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/item_storage/item/"+itemId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Item).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "text/plain");
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
