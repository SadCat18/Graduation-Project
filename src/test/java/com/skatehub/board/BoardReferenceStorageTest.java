package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.reset;

/** Actual recorded reference catalog in isolated H2; no remote or purchasing evidence. */
@SpringBootTest(classes={BoardPlanStorageTest.Config.class,BoardAdminService.class},webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.config.location=classpath:board-test.properties","spring.datasource.url=jdbc:h2:mem:board-reference01;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class BoardReferenceStorageTest {
 @Autowired BoardCatalogService catalog;
 @Autowired BoardCalculationService calculation;
 @Autowired BoardPlanService plans;
 @Autowired BoardAdminService admin;
 @Autowired BoardDatasetValidator json;
 @Autowired BoardPlanSnapshotRepository snapshots;
 @Autowired BoardQuoteRepository quotes;
 @Autowired BoardVariantRepository variants;
 @Autowired BoardProductRepository products;
 @Autowired BoardRuleRepository rules;
 @Autowired BoardTemplateRepository templates;
 @Autowired JdbcTemplate jdbc;
 @MockBean AdminAuditLogService audit;
 ObjectNode dataset;
 @BeforeEach void setup() throws Exception {
  // Historical/effective reference fixture before the explicitly dated promotions ended.
  calculation.calculationClock(java.time.Clock.fixed(java.time.Instant.parse("2026-10-10T00:00:00Z"),java.time.ZoneOffset.UTC));
  Files.createDirectories(Path.of("target/test-evidence/board/REFERENCE-01"));
  reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();
  jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));
  jdbc.update("MERGE INTO tb_user(user_id) KEY(user_id) VALUES(101)");jdbc.update("MERGE INTO tb_user(user_id) KEY(user_id) VALUES(102)");
  try(var in=Files.newInputStream(Path.of("docs/equipment-data/normalized-equipment.reference.v1.json"))){dataset=(ObjectNode)json.read(in);}
  assertThat(catalog.importDataset(dataset,catalog.catalogVersion(),7L).errors()).isEmpty();
 }
 ObjectNode demand(String purpose,long budget){return json.object().put("purpose",purpose).put("preference","EASY_START").put("recommendationType","BOTH").put("budgetCents",budget);}
 ObjectNode complete(String product){String key=dataset.path("variants").findValuesAsText("key").stream().filter(k->k.startsWith(product+"--")).findFirst().orElseThrow();return json.object().put("kind","COMPLETE").set("items",json.array().add(json.object().put("category","COMPLETE").put("variantKey",key)));}
 ObjectNode eval(String product){ObjectNode request=json.object();request.set("demand",demand("ACTION",60000));request.set("selection",complete(product));return calculation.evaluate(request);}
 ObjectNode saveInput(ObjectNode e){ObjectNode b=json.object().put("name","原资料参考方案").put("expectedCalculationVersion",e.path("calculationVersion").asText());b.set("demand",e.path("demand"));b.set("selection",e.path("selection"));return b;}
 @Test void importsRecordedDataAndRetainsStrictExactEligibility() {
  assertThat(products.count()).isEqualTo(50);assertThat(variants.count()).isEqualTo(114);assertThat(quotes.count()).isEqualTo(60);
  assertThat(catalog.catalog("COMPLETE",1,20).getTotal()).isEqualTo(4);
  JsonNode v=catalog.variant(complete("SET-001").path("items").get(0).path("variantKey").asText());
  assertThat(v.path("eligibility").path("referencePriceEligible").asBoolean()).isTrue();assertThat(v.path("eligibility").path("exactPriceEligible").asBoolean()).isFalse();
  assertThat(v.path("sources").get(0).path("verificationStatus").asText()).isEqualTo("RECORDED");assertThat(v.path("sources").get(0).path("collectedDate").asText()).isEqualTo("2026-10-09");
  assertThat(catalog.importDataset(dataset,catalog.catalogVersion(),7L).unchanged()).isEqualTo(266);
 }
 @Test void upgradesOriginalDraftVersionsWithoutChangingKeys() {
  quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));
  assertThat(catalog.importDataset(BoardTestData.real(json),catalog.catalogVersion(),7L).created()).isEqualTo(266);
  var upgrade=catalog.importDataset(dataset,catalog.catalogVersion(),7L);assertThat(upgrade.errors()).isEmpty();assertThat(upgrade.updated()).isEqualTo(266);assertThat(upgrade.created()).isZero();assertThat(quotes.count()).isEqualTo(120);assertThat(catalog.catalog("COMPLETE",1,20).getTotal()).isEqualTo(4);
 }
 @Test void sixHundredReturnsOriginalReferenceCompletesForThreePurposes() throws Exception {
  ObjectNode evidence=json.object().put("evidenceKind","ORIGINAL_RECORDED_DATA_REAL_SERVICE_ISOLATED_H2_NOT_HTTP_OR_PRODUCTION");
  ObjectNode api=json.object().put("evidenceKind","REAL_RECORDED_CATALOG_CONTROLLER_MOCKMVC_H2_NOT_LIVE_HTTP_OR_PRODUCTION");
  var mvc=MockMvcBuilders.standaloneSetup(new BoardCalculationController(calculation,json)).build();
  for(String purpose:List.of("ACTION","CRUISE","BALANCED")){
   ObjectNode request=demand(purpose,60000);JsonNode response=calculation.recommend(request);JsonNode complete=response.path("complete");
   assertThat(complete.path("state").asText()).isEqualTo("OK");assertThat(complete.path("plans").size()).isEqualTo(3);
   Set<Long> prices=new HashSet<>();for(JsonNode p:complete.path("plans")) {prices.add(p.path("totalCents").asLong());assertThat(p.path("pricingMode").asText()).isEqualTo("REFERENCE");assertThat(p.path("validationState").asText()).isEqualTo("REFERENCE_ESTIMATE");assertThat(p.path("priceAssumptions").size()).isPositive();assertThat(p.path("items").get(0).path("shippingCents").isNull()).isTrue();}
   assertThat(prices).containsExactlyInAnyOrder(58000L,32300L,44600L);
   ObjectNode sample=json.object();sample.set("request",request);sample.set("response",calculation.publicView(response));evidence.set(purpose,sample);
   ObjectNode apiRequest=request.deepCopy().put("recommendationType","COMPLETE");var actual=mvc.perform(MockMvcRequestBuilders.post("/api/skate/board-assistant/recommend").contentType("application/json").content(apiRequest.toString())).andReturn().getResponse();assertThat(actual.getStatus()).isEqualTo(200);ObjectNode example=json.object().put("httpStatus",actual.getStatus());example.set("request",apiRequest);example.set("response",json.parse(actual.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));api.set(purpose,example);
  }
  Path p=Path.of("target/test-evidence/board/REFERENCE-01/actual-600-service-examples.json");Files.createDirectories(p.getParent());Files.writeString(p,evidence.toPrettyString());
  Files.writeString(p.resolveSibling("actual-600-api-examples.json"),api.toPrettyString());
 }
 @Test void publicRemediationPreservesOneCentBoundaryAndUnknownAssemblyTotal(){
  JsonNode exact=calculation.recommend(demand("BALANCED",32300).put("recommendationType","COMPLETE")).path("complete");assertThat(exact.path("state").asText()).isEqualTo("OK");assertThat(exact.path("plans").get(0).path("totalCents").asLong()).isEqualTo(32300);
  JsonNode cent=calculation.recommend(demand("BALANCED",32299).put("recommendationType","COMPLETE")).path("complete");assertThat(cent.path("state").asText()).isEqualTo("BUDGET_INSUFFICIENT");assertThat(cent.path("shortfallCents").asLong()).isEqualTo(1);
  JsonNode partial=calculation.recommend(demand("ACTION",60000)).path("assembly").path("plans").get(0);assertThat(partial.path("knownSubtotalCents").asLong()).isEqualTo(78620);assertThat(partial.path("totalCents").isNull()).isTrue();assertThat(partial.path("budgetDeltaCents").isNull()).isTrue();
 }
 @Test void lowBudgetReportsReferenceShortfall() {
  JsonNode r=calculation.recommend(demand("ACTION",10000)).path("complete");assertThat(r.path("state").asText()).isEqualTo("BUDGET_INSUFFICIENT");assertThat(r.path("shortfallCents").asLong()).isEqualTo(22300);assertThat(r.path("minimumFeasiblePlan").path("pricingMode").asText()).isEqualTo("REFERENCE");
 }
 @Test void partialAssembliesHaveMissingHardwarePriceAndBoundedSearch() {
  JsonNode r=calculation.recommend(demand("ACTION",60000));assertThat(r.path("evaluatedCount").asInt()).isLessThanOrEqualTo(2000);
  JsonNode bucket=r.path("assembly");assertThat(bucket.path("state").asText()).isEqualTo("DATA_INSUFFICIENT");assertThat(bucket.path("plans").size()).isBetween(1,3);
  for(JsonNode p:bucket.path("plans")){assertThat(p.path("totalCents").isNull()).isTrue();assertThat(p.path("budgetDeltaCents").isNull()).isTrue();assertThat(p.path("knownSubtotalCents").asLong()).isPositive();assertThat(p.path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");for(JsonNode line:p.path("items"))if(line.path("category").asText().equals("HARDWARE"))assertThat(line.path("lineTotalCents").isNull()).isTrue();}
 }
 @Test void compareAndReplaceUseSameReferenceCalculation() {
  ObjectNode compare=json.object();ArrayNode list=json.array();for(String p:List.of("SET-001","SET-002","SET-003")){ObjectNode n=json.object();n.set("demand",demand("ACTION",60000));n.set("selection",complete(p));list.add(n);}compare.set("plans",list);assertThat(calculation.compare(compare).path("evaluations").size()).isEqualTo(3);
  ObjectNode b=json.object().put("category","COMPLETE");b.set("demand",demand("ACTION",60000));b.set("selection",complete("SET-001"));JsonNode replacement=calculation.replacements(b);assertThat(replacement.path("total").asInt()).isEqualTo(4);for(JsonNode e:replacement.path("list"))assertThat(e.path("pricingMode").asText()).isEqualTo("REFERENCE");
 }
 @Test void snapshotPreservesReferenceIdentityOwnershipAndPriceConflict() {
  ObjectNode e=eval("SET-001"),body=saveInput(e);JsonNode saved=plans.save(101L,body);String key=saved.path("key").asText();assertThat(saved.path("snapshotState").asText()).isEqualTo("REFERENCE_ESTIMATE");
  String frozen=snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson();assertThatThrownBy(()->plans.detail(102L,key)).isInstanceOf(BoardRequestException.class);
  // Synthetic one-cent revision is a version-conflict control, not a newly researched price.
  assertThat(admin.patch("quotes","SET-001--cn-price",json.object().put("expectedVersion",2).put("amountCents",58001),7L).path("version").asInt()).isEqualTo(3);
  assertThat(plans.save(101L,body).path("errorCode").asText()).isEqualTo("PRICE_CHANGED");assertThat(snapshots.count()).isEqualTo(1);
  assertThat(plans.detail(101L,key).path("snapshot").path("totalCents").asLong()).isEqualTo(58000);assertThat(plans.detail(101L,key).path("latestEvaluation").path("totalCents").asLong()).isEqualTo(58001);
  String copy=plans.copy(101L,key,json.object().put("name","参考历史副本")).path("key").asText();assertThat(snapshots.findByKeyAndOwnerId(copy,101L).orElseThrow().getContentJson()).isEqualTo(frozen);
  ObjectNode evidence=json.object().put("evidenceKind","ORIGINAL_RECORDED_REFERENCE_SNAPSHOT_H2_WITH_SYNTHETIC_ONE_CENT_CONFLICT_CONTROL");evidence.set("saveRequest",body);evidence.set("saveResponse",saved);evidence.set("detailAfterSyntheticRevision",plans.detail(101L,key));evidence.set("staleSaveAfterSyntheticRevision",plans.save(101L,body));try{Files.writeString(Path.of("target/test-evidence/board/REFERENCE-01/actual-snapshot-service-examples.json"),evidence.toPrettyString());}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 @Test void partialSnapshotRemainsDraftWithoutTurningUnknownsIntoZero() {
  JsonNode e=calculation.recommend(demand("ACTION",60000)).path("assembly").path("plans").get(0);JsonNode saved=plans.save(101L,saveInput((ObjectNode)e));assertThat(saved.path("snapshotState").asText()).isEqualTo("DRAFT");assertThat(saved.path("totalCents").isNull()).isTrue();assertThat(saved.path("knownSubtotalCents").asLong()).isPositive();
 }
 @Test void referenceAdminActivationDoesNotRelaxUnmarkedOrMidPriceRows() {
  ObjectNode q=BoardTestData.find(dataset,"quotes","SET-001--cn-price").deepCopy().put("key","REFERENCE-ADMIN-Q").put("version",1);
  assertThat(admin.create("quotes",q,7L).path("active").asBoolean()).isFalse();assertThat(admin.activate("quotes","REFERENCE-ADMIN-Q",json.object().put("expectedVersion",1).put("active",true),7L).path("active").asBoolean()).isTrue();
  assertThat(admin.patch("quotes","REFERENCE-ADMIN-Q",json.object().put("expectedVersion",2).put("pricingMode","EXACT"),7L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");
  ObjectNode bad=dataset.deepCopy();BoardTestData.find(bad,"quotes","TRUCK-001--cn-price").put("active",true);assertThat(json.validate(bad).errors().stream().map(BoardDtos.Issue::code)).contains("MODEL_MISMATCH");
 }
 @Test void referenceChecksExcludeKnownOutOfRangeWithoutInventingEndpoints() throws Exception {
  ArrayNode items=json.array();for(String c:BoardCalculationService.REQUIRED){String key=dataset.path("variants").findValuesAsText("key").stream().filter(k->k.startsWith(Map.of("DECK","DECK-005--width-8-0-","TRUCK","TRUCK-001--size-129-","WHEEL","WHEEL-009--","BEARING","BRG-001--","GRIP","GRIP-002--","HARDWARE","HW-").get(c))).findFirst().orElseGet(()->dataset.path("variants").findValuesAsText("key").stream().filter(k->k.startsWith("HARDWARE-")).findFirst().orElseThrow());items.add(json.object().put("category",c).put("variantKey",key));}
  ObjectNode b=json.object();b.set("demand",demand("ACTION",60000));b.set("selection",json.object().put("kind","ASSEMBLY").set("items",items));ObjectNode evaluated=calculation.evaluate(b);Files.writeString(Path.of("target/test-evidence/board/REFERENCE-01/known-incompatible-control.json"),evaluated.toPrettyString());assertThat(evaluated.path("validationState").asText()).as(evaluated.path("checks").toString()).isEqualTo("INCOMPATIBLE");
  ((ObjectNode)items.get(0)).put("variantKey","DECK-005--width-7-75-length-31-25-wheelbase-14-0");assertThat(calculation.evaluate(b).path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");
 }
}
