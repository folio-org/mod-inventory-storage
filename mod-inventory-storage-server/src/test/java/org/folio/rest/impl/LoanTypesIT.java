package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.LoanType;
import org.folio.rest.jaxrs.model.LoanTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class LoanTypesIT extends BaseReferenceDataIntegrationTest<LoanType, LoanTypes> {

  @Override
  protected String referenceTable() {
    return "loan_type";
  }

  @Override
  protected String resourceUrl() {
    return "/loan-types";
  }

  @Override
  protected Class<LoanType> targetClass() {
    return LoanType.class;
  }

  @Override
  protected Class<LoanTypes> collectionClass() {
    return LoanTypes.class;
  }

  @Override
  protected LoanType sampleRecord() {
    return new LoanType()
      .withName("Sample-Loan-Type")
      .withSource("Sample-Source");
  }

  @Override
  protected Function<LoanTypes, List<LoanType>> collectionRecordsExtractor() {
    return LoanTypes::getLoantypes;
  }

  @Override
  protected List<Function<LoanType, Object>> recordFieldExtractors() {
    return List.of(
      LoanType::getName,
      LoanType::getSource
    );
  }

  @Override
  protected Function<LoanType, String> idExtractor() {
    return LoanType::getId;
  }

  @Override
  protected Function<LoanType, Metadata> metadataExtractor() {
    return LoanType::getMetadata;
  }

  @Override
  protected UnaryOperator<LoanType> recordModifyingFunction() {
    return loanType -> loanType.withName(loanType.getName() + "-Updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Sample-Loan-Type", "source==Sample-Source");
  }

  @Test
  void canCreateLoanTypeWithSourceFieldPopulated(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    var loanType = new JsonObject()
      .put("name", "Reading room")
      .put("source", "System");
    
    doPost(client, resourceUrl(), loanType)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var createdLoanType = response.jsonBody();
        
        assertThat(createdLoanType.getString("id")).isNotNull();
        assertThat(createdLoanType.getString("name")).isEqualTo("Reading room");
        assertThat(createdLoanType.getString("source")).isEqualTo("System");
        
        ctx.completeNow();
      })));
  }

  @Test
  void cannotCreateLoanTypeWithAdditionalProperties(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    var loanType = new JsonObject()
      .put("name", "Can Circulate")
      .put("additional", "foo");
    
    doPost(client, resourceUrl(), loanType)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateLoanTypeWithSameName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    var loanType = new JsonObject()
      .put("name", "Can circulate");
    
    doPost(client, resourceUrl(), loanType)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> doPost(client, resourceUrl(), loanType))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateLoanTypeWithSameId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    var loanType = new JsonObject()
      .put("name", "Can circulate");
    
    doPost(client, resourceUrl(), loanType)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> {
        var loanTypeId = response.jsonBody().getString("id");
        var anotherLoanType = new JsonObject()
          .put("name", "Overnight")
          .put("id", loanTypeId);
          
        return doPost(client, resourceUrl(), anotherLoanType);
      })
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotGetLoanTypeThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    doGet(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotDeleteLoanTypeThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    doDelete(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotUpdateLoanTypeThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    
    var loanType = new JsonObject()
      .put("name", "Reading room");
    
    doPut(client, resourceUrlById(UUID.randomUUID().toString()), loanType)
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
}
