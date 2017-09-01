/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import java.util.Map;
import org.folio.rest.jaxrs.model.Shelflocation;
import org.folio.rest.jaxrs.resource.ShelfLocationsResource;

import javax.ws.rs.core.Response;
import io.vertx.core.Handler;
import io.vertx.core.Context;
import io.vertx.core.AsyncResult;

/**
 *
 * @author kurt
 */
public class ShelfLocationAPI implements ShelfLocationsResource {

  @Override
  public void deleteShelfLocations(
          String lang, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception{
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void getShelfLocations(     
        String query,       
        int offset,   
        int limit,  
        String lang, 
        Map<String, String>okapiHeaders, 
        Handler<AsyncResult<Response>>asyncResultHandler, 
        Context vertxContext)
        throws Exception {
   throw new UnsupportedOperationException("Not supported yet.");
  }
  

  @Override
  public void postShelfLocations(
          String lang, 
          Shelflocation entity, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void getShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, 
          Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void deleteShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, Map<String, String> okapiHeaders, 
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putShelfLocationsByMaterialtypeId(
          String materialtypeId, 
          String lang, 
          Shelflocation entity, 
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>>asyncResultHandler, 
          Context vertxContext) 
          throws Exception {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
}
