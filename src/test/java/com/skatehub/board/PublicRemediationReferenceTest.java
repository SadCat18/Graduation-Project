package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.skatehub.board.BoardDatasetValidator.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=BoardPlanStorageTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.config.location=classpath:board-test.properties","spring.datasource.url=jdbc:h2:mem:public-remediation-reference-v2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class PublicRemediationReferenceTest {
 @Autowired BoardCatalogService catalog;@Autowired BoardCalculationService calc;@Autowired BoardDatasetValidator json;@Autowired BoardPlanService plans;
 @Autowired BoardPlanSnapshotRepository snapshots;@Autowired BoardQuoteRepository quotes;@Autowired BoardVariantRepository variants;@Autowired BoardProductRepository products;@Autowired BoardRuleRepository rules;@Autowired BoardTemplateRepository templates;@Autowired JdbcTemplate db;
 @MockBean AdminAuditLogService audit;
 ObjectNode v1,v2;
 void clock(String instant){var c=Clock.fixed(Instant.parse(instant),ZoneOffset.UTC);calc.calculationClock(c);catalog.calculationClock(c);}
 @BeforeEach void setup()throws Exception {
  reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();
  db.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));db.update("MERGE INTO tb_user(user_id) KEY(user_id) VALUES(101)");
  v1=read("normalized-equipment.reference.v1.json");v2=read("normalized-equipment.reference.v2.json");clock("2026-10-24T00:00:00Z");
 }
 ObjectNode read(String file)throws Exception{try(var in=Files.newInputStream(Path.of("docs/equipment-data/"+file))){return (ObjectNode)json.read(in);}}
 ObjectNode demand(long budget){return json.object().put("budgetCents",budget).put("purpose","ACTION").put("preference","EASY_START").put("recommendationType","BOTH");}
 String first(JsonNode d,String prefix){for(JsonNode v:d.path("variants"))if(v.path("key").asText().startsWith(prefix)&&v.path("active").asBoolean())return v.path("key").asText();throw new AssertionError(prefix);}
 ObjectNode request(JsonNode d){
  ObjectNode body=json.object();body.set("demand",demand(60000));var selected=json.array();
  Map<String,String> prefixes=Map.of("DECK","DECK-005--","TRUCK","TRUCK-006--size-8-0-","WHEEL","WHEEL-009--","BEARING","BRG-008--","GRIP","GRIP-002--","HARDWARE","HW-001--");
  for(String category:List.of("DECK","TRUCK","WHEEL","BEARING","GRIP","HARDWARE")) {
   String selectedKey=first(d,prefixes.get(category));
   if(category.equals("DECK"))selectedKey=BoardTestData.find(d,"quotes","DECK-005--cn-price").path("variantKey").asText();
   selected.add(json.object().put("category",category).put("variantKey",selectedKey));
  }
  body.set("selection",json.object().put("kind","ASSEMBLY").set("items",selected));return body;
 }
 void install(JsonNode d){assertThat(catalog.importDataset(d,catalog.catalogVersion(),7L).errors()).isEmpty();}
 @Test void unchangedCatalogClockExpiryForcesSaveReconfirmationAndPreservesHistoricalCopies(){
  clock("2026-10-10T00:00:00Z");install(v1);String version=catalog.catalogVersion();ObjectNode body=request(v1);JsonNode before=calc.evaluate(body);
  assertThat(before.path("knownSubtotalCents").asLong()).isEqualTo(78620);body.put("name","Historical recorded reference").put("expectedCalculationVersion",before.path("calculationVersion").asText());
  String key=plans.save(101L,body).path("key").asText();String frozen=snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson();
  clock("2026-10-24T00:00:00Z");assertThat(catalog.catalogVersion()).isEqualTo(version);
  JsonNode current=calc.evaluate(request(v1));assertThat(current.path("knownSubtotalCents").asLong()).isEqualTo(67100);assertThat(current.path("totalCents").isNull()).isTrue();assertThat(current.path("budgetDeltaCents").isNull()).isTrue();
  assertThat(current.path("knownSubtotalCents").asLong()-60000).isEqualTo(7100);
  JsonNode conflict=plans.save(101L,body);assertThat(conflict.path("errorCode").asText()).isEqualTo("PRICE_CHANGED");assertThat(snapshots.count()).isEqualTo(1);
  JsonNode detail=plans.detail(101L,key);assertThat(detail.path("snapshot").path("knownSubtotalCents").asLong()).isEqualTo(78620);assertThat(detail.path("latestEvaluation").path("knownSubtotalCents").asLong()).isEqualTo(67100);
  JsonNode copy=plans.copy(101L,key,json.object().put("name","Explicit historical copy"));assertThat(copy.path("knownSubtotalCents").asLong()).isEqualTo(78620);assertThat(copy.path("historicalCopy").asBoolean()).isTrue();
  String copyKey=copy.path("key").asText();assertThat(plans.detail(101L,copyKey).path("latestEvaluation").path("knownSubtotalCents").asLong()).isEqualTo(67100);
  assertThat(snapshots.findByKeyAndOwnerId(copyKey,101L).orElseThrow().getContentJson()).isEqualTo(frozen);
  assertThat(snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson()).isEqualTo(frozen);
  var historical=catalog.variant(first(v1,"BRG-008--"));assertThat(historical.path("eligibility").path("referencePriceEligible").asBoolean()).isFalse();assertThat(historical.path("displayQuotes").get(0).path("historicalOnly").asBoolean()).isTrue();
 }
 @Test void datePrecisionUncertainBoundaryAndOrdinaryReferencesRemainDistinct(){
  ObjectNode q=(ObjectNode)BoardTestData.find(v1,"quotes","BRG-008--cn-price");
  assertThat(referenceQuote(q,Instant.parse("2026-10-12T09:59:59Z"))).isTrue();
  assertThat(referenceTimeStatus(q,Instant.parse("2026-10-12T10:00:00Z"))).isEqualTo("BOUNDARY_UNCERTAIN");
  assertThat(referenceQuote(q,Instant.parse("2026-10-13T11:59:59Z"))).isFalse();assertThat(referenceTimeStatus(q,Instant.parse("2026-10-13T12:00:00Z"))).isEqualTo("EXPIRED");
  JsonNode normal=BoardTestData.find(v1,"quotes","DECK-005--cn-price");assertThat(referenceQuote(normal,Instant.parse("2035-01-01T00:00:00Z"))).isTrue();
  q.put("validUntil","2026-10-11T00:00:00Z");assertThat(referenceQuote(q,Instant.parse("2026-10-11T00:00:00Z"))).isFalse();
 }
 @Test void exactCannotBypassAnExplicitEndedPromotion(){
  ObjectNode q=(ObjectNode)BoardTestData.find(v1,"quotes","BRG-008--cn-price");q.put("pricingMode","EXACT").put("verificationStatus","VERIFIED").put("shippingCents",0).put("taxCents",0).put("landedPriceConfirmed",true).put("collectedAt","2026-10-09T00:00:00Z").put("validUntil","2030-01-01T00:00:00Z");
  q.path("sources").forEach(s->((ObjectNode)s).put("verificationStatus","VERIFIED"));
  BoardCatalogService stub=mock(BoardCatalogService.class);when(stub.calculationCatalog()).thenReturn(v1);var calculation=new BoardCalculationService(stub,json);calculation.calculationClock(Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"),ZoneOffset.UTC));
  JsonNode result=calculation.evaluate(request(v1));assertThat(result.path("knownSubtotalCents").asLong()).isEqualTo(67100);
 }
 @Test void v2ImportPreservesLineageAndLegacyIdentityWithoutSilentGraphicMapping(){
  install(v1);ObjectNode request=json.object();request.set("demand",demand(60000));request.set("selection",json.object().put("kind","COMPLETE").set("items",json.array().add(json.object().put("category","COMPLETE").put("variantKey","SET-001--deck-none-complete"))));
  JsonNode oldEval=calc.evaluate(request);assertThat(oldEval.path("totalCents").asLong()).isEqualTo(58000);request.put("name","Legacy graphic selection").put("expectedCalculationVersion",oldEval.path("calculationVersion").asText());String key=plans.save(101L,request).path("key").asText();String frozen=snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson();
  install(v2);assertThat(products.count()).isEqualTo(50);assertThat(variants.count()).isEqualTo(122);
  JsonNode legacy=catalog.variant("SET-001--deck-none-complete");assertThat(legacy.path("variant").path("active").asBoolean()).isFalse();assertThat(legacy.path("variant").path("replacements")).hasSize(4);assertThat(legacy.path("eligibility").path("referencePriceEligible").asBoolean()).isFalse();
  JsonNode detail=plans.detail(101L,key);assertThat(detail.path("snapshot").path("totalCents").asLong()).isEqualTo(58000);assertThat(detail.path("latestEvaluation").path("totalCents").isNull()).isTrue();assertThat(snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson()).isEqualTo(frozen);
  assertThat(detail.path("snapshot").path("selection").path("items").get(0).path("variantKey").asText()).isEqualTo("SET-001--deck-none-complete");
  var ledger=BoardTestData.find(v2,"products","SET-001");assertThat(ledger.path("sourceRecordId").asText()).isEqualTo("SET-001");
  for(String graphic:List.of("circus","zoo-party","pj-la","lucas-collab"))assertThat(catalog.variant("SET-001--graphic-"+graphic).path("eligibility").path("referencePriceEligible").asBoolean()).isTrue();
 }
 @Test void v2FieldScopesStayConservativeAndPassTheRealValidator(){
  var report=json.validate(v2);assertThat(report.valid()).as(report.errors().toString()).isTrue();install(v2);
  JsonNode truck=BoardTestData.find(v2,"variants","TRUCK-006--size-8-0-k5-standard");assertThat(truck.path("specs").path("axleWidthInch").asDouble()).isEqualTo(8);assertThat(truck.path("specs").path("heightMm").isNull()).isTrue();assertThat(truck.path("specs").path("deckMinInch").isNull()).isTrue();assertThat(truck.path("compatibilityAdvice").path("hardBoundary").asBoolean()).isFalse();
  assertThat(truck.path("verificationStatus").asText()).isEqualTo("RECORDED");JsonNode wheel=BoardTestData.find(v2,"variants",first(v2,"WHEEL-009--"));assertThat(wheel.path("verificationStatus").asText()).isEqualTo("RECORDED");assertThat(wheel.path("specs").path("widthMm").isNull()).isTrue();
  JsonNode ph=BoardTestData.find(v2,"variants","HW-001--phillips-length-unresolved");assertThat(ph.path("specs").path("lengthMm").isNull()).isTrue();assertThat(ph.path("fieldEvidence").get(0).path("verificationStatus").asText()).isEqualTo("INSUFFICIENT");
  assertThat(v2.path("variants").findValues("headType").toString()).contains("ALLEN","PHILLIPS");
  JsonNode domestic=BoardTestData.find(v2,"quotes","TRUCK-006--cn-price");assertThat(domestic.path("packageQuantity").isNull()).isTrue();assertThat(domestic.path("amountCents").asLong()).isEqualTo(32200);
 }
 @Test void outputActualReferenceAndCatalogExamples()throws Exception {
  String out=System.getProperty("remediation.evidenceDir");if(out==null)return;
  clock("2026-10-10T00:00:00Z");install(v1);ObjectNode request=request(v1);ObjectNode before=calc.evaluate(request);
  ObjectNode save=request.deepCopy().put("name","Synthetic historical reference").put("expectedCalculationVersion",before.path("calculationVersion").asText());String key=plans.save(101L,save).path("key").asText();
  ObjectNode examples=json.object().put("evidenceLayer","REAL_SERVICE_ISOLATED_H2_SYNTHETIC_OWNER_RECORDED_DATA_NOT_HTTP_MYSQL_PRODUCTION");examples.set("fixedSelectionRequest",request);examples.set("historicalEffectiveBeforeExpiry",calc.publicView(before));
  clock("2026-10-24T00:00:00Z");ObjectNode current=calc.evaluate(request);examples.set("sameCatalogAfterExpiry",calc.publicView(current));examples.set("staleSaveConflict",plans.save(101L,save));ObjectNode copyBody=json.object().put("name","Explicit historical copy");examples.set("historicalCopyRequest",copyBody);examples.set("historicalCopyResponse",plans.copy(101L,key,copyBody));examples.set("historicalSnapshotAndLatest",plans.detail(101L,key));
  examples.set("endedBearingQuote",catalog.variant(first(v1,"BRG-008--")));examples.set("ordinaryDeckReference",catalog.variant(BoardTestData.find(v1,"quotes","DECK-005--cn-price").path("variantKey").asText()));
  var validation=catalog.validate(v2);examples.set("v2Validation",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(validation));install(v2);
  examples.set("v2LegacyGraphicIdentity",catalog.variant("SET-001--deck-none-complete"));examples.set("v2ExplicitGraphic",catalog.variant("SET-001--graphic-circus"));examples.set("v2Allen1Inch",catalog.variant("HW-001--allen-1in"));examples.set("v2PhillipsUnknownLength",catalog.variant("HW-001--phillips-length-unresolved"));examples.set("v2KruxAdvice",catalog.variant("TRUCK-006--size-8-0-k5-standard"));
  examples.set("v2Dooks",catalog.variant("RSR-001--height-3-175mm"));examples.set("v2DbhUnknownWheelWidth",catalog.variant(first(v2,"WHEEL-009--")));
  examples.set("v2Recommendation",calc.publicView(calc.recommend(demand(60000))));
  Path dir=Path.of(out);Files.createDirectories(dir);Files.writeString(dir.resolve("reference-examples.json"),examples.toPrettyString());
 }

}
