
package org.folio.rest.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;

public class JobsClient {

    private final static String GLOBAL_PATH = "/jobs";
    private String tenantId;
    private HttpClientOptions options;
    private HttpClient httpClient;

    public JobsClient(String host, int port, String tenantId, boolean keepAlive) {
        this.tenantId = tenantId;
        options = new HttpClientOptions();
        options.setLogActivity(true);
        options.setKeepAlive(keepAlive);
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        httpClient = io.vertx.core.Vertx.vertx().createHttpClient(options);
    }

    public JobsClient(String host, int port, String tenantId) {
        this(host, port, tenantId, true);
    }

    /**
     * Service endpoint ^/jobs/jobconfs
     * 
     */
    public void postJobconfs(String lang, org.folio.rest.jaxrs.model.JobConf JobConf, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/jobs/jobconfs"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(JobConf).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs
     * 
     */
    public void getJobconfs(String query, String orderBy, org.folio.rest.jaxrs.resource.JobsResource.Order order, int offset, int limit, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/jobs/jobconfs"+queryParams.toString());
        request.handler(responseHandler);
        if(query != null) {queryParams.append("query="+query);}
        queryParams.append("&");
        if(orderBy != null) {queryParams.append("orderBy="+orderBy);}
        queryParams.append("&");
        if(order != null) {queryParams.append("order="+order.toString());}
        queryParams.append("&");
        queryParams.append("offset="+offset);
        queryParams.append("&");
        queryParams.append("limit="+limit);
        queryParams.append("&");
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}
     * 
     */
    public void getJobconfsId(String jobconfsId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/jobs/jobconfs/"+jobconfsId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}
     * 
     */
    public void deleteJobconfsId(String jobconfsId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.delete("/jobs/jobconfs/"+jobconfsId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}
     * 
     */
    public void putJobconfsId(String jobconfsId, String lang, org.folio.rest.jaxrs.model.JobConf JobConf, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/jobs/jobconfs/"+jobconfsId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(JobConf).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs
     * 
     */
    public void getJobconfsIdJobs(String jobconfsId, String query, String orderBy, org.folio.rest.jaxrs.resource.JobsResource.Order order, int offset, int limit, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/jobs/jobconfs/"+jobconfsId+"/jobs"+queryParams.toString());
        request.handler(responseHandler);
        if(query != null) {queryParams.append("query="+query);}
        queryParams.append("&");
        if(orderBy != null) {queryParams.append("orderBy="+orderBy);}
        queryParams.append("&");
        if(order != null) {queryParams.append("order="+order.toString());}
        queryParams.append("&");
        queryParams.append("offset="+offset);
        queryParams.append("&");
        queryParams.append("limit="+limit);
        queryParams.append("&");
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs
     * 
     */
    public void postJobconfsIdJobs(String jobconfsId, String lang, org.folio.rest.jaxrs.model.Job Job, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/jobs/jobconfs/"+jobconfsId+"/jobs"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Job).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs/{jobId}
     * 
     */
    public void getJobId(String jobId, String jobconfsId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/jobs/jobconfs/"+jobconfsId+"/jobs/"+jobId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs/{jobId}
     * 
     */
    public void deleteJobId(String jobId, String jobconfsId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.delete("/jobs/jobconfs/"+jobconfsId+"/jobs/"+jobId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs/{jobId}
     * 
     */
    public void putJobId(String jobId, String jobconfsId, String lang, org.folio.rest.jaxrs.model.Job Job, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.put("/jobs/jobconfs/"+jobconfsId+"/jobs/"+jobId+""+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Job).encode());
        request.write(buffer);
        request.setChunked(true);
        request.putHeader("Content-type", "application/json");
        request.putHeader("Accept", "text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs/{jobId}/bulks
     * 
     */
    public void getJobIdBulks(String jobId, String jobconfsId, String lang, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.get("/jobs/jobconfs/"+jobconfsId+"/jobs/"+jobId+"/bulks"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        request.putHeader("Accept", "application/json,text/plain");
        request.putHeader("Authorization", tenantId);
        request.putHeader("x-okapi-tenant", tenantId);
        request.end();
    }

    /**
     * Service endpoint ^/jobs/jobconfs/{jobconfsId}/jobs/{jobId}/bulks
     * 
     */
    public void postJobIdBulks(String jobId, String jobconfsId, String lang, org.folio.rest.jaxrs.model.Bulk Bulk, Handler<HttpClientResponse> responseHandler) {
        StringBuilder queryParams = new StringBuilder("?");
        io.vertx.core.http.HttpClientRequest request = httpClient.post("/jobs/jobconfs/"+jobconfsId+"/jobs/"+jobId+"/bulks"+queryParams.toString());
        request.handler(responseHandler);
        if(lang != null) {queryParams.append("lang="+lang);}
        queryParams.append("&");
        io.vertx.core.buffer.Buffer buffer = io.vertx.core.buffer.Buffer.buffer();
        buffer.appendString(org.folio.rest.tools.utils.JsonUtils.entity2Json(Bulk).encode());
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
