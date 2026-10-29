package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real service/JPA/JDBC against isolated H2, with a synthetic principal for standalone MVC (not real JWT). */
@SpringBootTest(classes={BoardPlanStorageTest.Config.class,BoardAdminService.class},webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.config.location=classpath:board-test.properties","spring.datasource.url=jdbc:h2:mem:board-admin-r02;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
class BoardAdminV2MaintenanceTest {
 @Autowired BoardAdminService admin;@Autowired BoardCatalogService catalog;@Autowired BoardDatasetValidator json;@Autowired BoardCalculationService calc;@Autowired BoardPlanService plans;
 @Autowired BoardPlanSnapshotRepository snapshots;@Autowired BoardQuoteRepository quotes;@Autowired BoardVariantRepository variants;@Autowired BoardProductRepository products;@Autowired BoardRuleRepository rules;@Autowired BoardTemplateRepository templates;@Autowired JdbcTemplate db;
 @MockBean AdminAuditLogService audit;
 ObjectNode data;
 @BeforeEach void setup()throws Exception{
  reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();variants.deleteAllInBatch();products.deleteAllInBatch();
  db.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));db.update("MERGE INTO tb_user(user_id) KEY(user_id) VALUES(101)");
  try(var in=Files.newInputStream(Path.of("docs/equipment-data/normalized-equipment.reference.v2.json"))){data=(ObjectNode)json.read(in);}
  assertThat(catalog.importDataset(data,catalog.catalogVersion(),17L).errors()).isEmpty();reset(audit);
 }
 @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
 ObjectNode current(String resource,String key){return (ObjectNode)json.parse(catalog.current(resource).get(key).getContentJson());}
 ObjectNode payload(ObjectNode record){ObjectNode b=record.deepCopy();b.remove(List.of("active","version","catalogVersion","id"));b.put("expectedVersion",record.path("version").asLong());b.put("notes","R02 synthetic maintenance note only");return b;}
 void unchanged(String resource,String key,ObjectNode original,String version){assertThat(current(resource,key)).isEqualTo(original);assertThat(catalog.catalogVersion()).isEqualTo(version);}
 @Test void originalPhillipsFullPayloadMaintainsNotesOverMvcWithoutDroppingEvidence()throws Exception{
  String key="HW-001--phillips-length-unresolved";ObjectNode before=current("variants",key),body=payload(before);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser(17L,"ADMIN","synthetic-r02"),null,List.of()));
  var mvc=MockMvcBuilders.standaloneSetup(new BoardAdminController(admin,json)).setControllerAdvice(new BoardErrorAdvice()).build();
  var response=mvc.perform(patch("/api/admin/board-assistant/variants/"+key).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.notes").value("R02 synthetic maintenance note only")).andReturn().getResponse();
  ObjectNode after=current("variants",key);for(String f:List.of("fieldEvidence","identity","specs","rawSpec","sources","historicalOnly"))assertThat(after.get(f)).as(f).isEqualTo(before.get(f));assertThat(after.path("specs").path("lengthMm").isNull()).isTrue();
  String output=System.getProperty("r02.evidenceDir");if(output!=null){Path dir=Path.of(output);Files.createDirectories(dir);ObjectNode evidence=json.object().put("evidenceLayer","STANDALONE_MOCKMVC_REAL_SERVICE_H2_SYNTHETIC_PRINCIPAL_NOT_REAL_HTTP_JWT_MYSQL");evidence.set("request",body);evidence.set("response",json.parse(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));Files.writeString(dir.resolve("phillips-maintenance-example.json"),evidence.toPrettyString());}
 }
 @Test void allV2ResourceExtensionsRoundTripWithoutChangingFacts(){
  Map<String,List<String>> keys=Map.of("products",List.of("TRUCK-006"),"variants",List.of("HW-001--allen-1in","HW-001--phillips-length-unresolved","TRUCK-006--size-8-0-k5-standard","RSR-001--height-3-175mm","SET-001--deck-none-complete","SET-001--graphic-circus"),"quotes",List.of("BRG-008--cn-price"),"rules",List.of(data.path("rules").get(0).path("key").asText()),"templates",List.of(data.path("templates").get(0).path("key").asText()));
  for(var entry:keys.entrySet())for(String key:entry.getValue()){
   ObjectNode before=current(entry.getKey(),key);JsonNode result=admin.patch(entry.getKey(),key,payload(before),17L);assertThat(result.path("errorCode").asText()).as(result.toString()).isEmpty();
   ObjectNode after=current(entry.getKey(),key);before.fields().forEachRemaining(field->{if(!List.of("version","notes").contains(field.getKey()))assertThat(after.get(field.getKey())).as(entry.getKey()+":"+field.getKey()).isEqualTo(field.getValue());});
   assertThat(after.path("version").asLong()).isEqualTo(before.path("version").asLong()+1);
  }
 }
 @Test void createUsesTheSameWhitelistAndPreservesMetadata(){
  ObjectNode original=current("variants","HW-001--phillips-length-unresolved"),body=original.deepCopy();body.put("key","R02-SYNTHETIC-PHILLIPS-DRAFT");body.remove("version");
  JsonNode draft=admin.create("variants",body,17L);assertThat(draft.path("errorCode").asText()).as(draft.toString()).isEmpty();assertThat(draft.path("active").asBoolean()).isFalse();assertThat(draft.path("fieldEvidence")).isEqualTo(original.path("fieldEvidence"));assertThat(draft.path("identity")).isEqualTo(original.path("identity"));
 }
 @Test void badExtensionStructuresAndUnknownFieldsNeverWrite(){
  String key="HW-001--phillips-length-unresolved";ObjectNode original=current("variants",key);String version=catalog.catalogVersion();
  Map<String,JsonNode> malformed=new LinkedHashMap<>();malformed.put("fieldEvidence",json.object());malformed.put("identity",json.array());malformed.put("packaging",json.array());malformed.put("compatibilityAdvice",json.array());malformed.put("historicalOnly",json.object());malformed.put("replacements",json.object());
  for(var bad:malformed.entrySet()){ObjectNode body=payload(original);body.set(bad.getKey(),bad.getValue());JsonNode result=admin.patch("variants",key,body,17L);assertThat(result.path("errorCode").asText()).as(bad.getKey()+result).isEqualTo("SELECTION_INVALID");unchanged("variants",key,original,version);}
  ObjectNode bad=payload(original);bad.put("fieldEvidencTypo",true);assertThatThrownBy(()->admin.patch("variants",key,bad,17L)).isInstanceOf(BoardRequestException.class);unchanged("variants",key,original,version);
  ObjectNode wrongResource=payload(current("products","TRUCK-006"));wrongResource.set("identity",json.object());assertThatThrownBy(()->admin.patch("products","TRUCK-006",wrongResource,17L)).isInstanceOf(BoardRequestException.class);
 }
 @Test void nestedEvidenceAdviceAndReplacementValidationRemainStrict(){
  String key="TRUCK-006--size-8-0-k5-standard";ObjectNode before=current("variants",key);String version=catalog.catalogVersion();
  ObjectNode evidence=payload(before);((ObjectNode)evidence.path("fieldEvidence").get(0)).put("checkedAt","yesterday");assertThat(admin.patch("variants",key,evidence,17L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");
  ObjectNode advice=payload(before);((ObjectNode)advice.path("compatibilityAdvice")).put("hardBoundary",true);assertThat(admin.patch("variants",key,advice,17L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");
  ObjectNode identity=payload(before);identity.set("identity",json.object().put("script","anything()"));assertThat(admin.patch("variants",key,identity,17L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");
  ObjectNode references=payload(before);references.set("replacements",json.array().add("NOT-A-REGISTERED-VARIANT"));assertThat(admin.patch("variants",key,references,17L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");unchanged("variants",key,before,version);
  ObjectNode packageBody=payload(current("variants","HW-001--allen-1in"));packageBody.set("packaging",json.object().put("boltsPerPack","eight"));assertThat(admin.patch("variants","HW-001--allen-1in",packageBody,17L).path("errorCode").asText()).isEqualTo("SELECTION_INVALID");
 }
 @Test void staleVersionAndHistoricalSnapshotsRemainProtected(){
  String key="SET-001--graphic-circus";ObjectNode before=current("variants",key);ObjectNode selection=json.object().put("kind","COMPLETE").set("items",json.array().add(json.object().put("category","COMPLETE").put("variantKey",key)));
  ObjectNode request=json.object();request.set("demand",BoardCalculationTest.demand(json));request.set("selection",selection);JsonNode evaluated=calc.evaluate(request);request.put("name","R02 synthetic snapshot").put("expectedCalculationVersion",evaluated.path("calculationVersion").asText());String saved=plans.save(101L,request).path("key").asText();String frozen=snapshots.findByKeyAndOwnerId(saved,101L).orElseThrow().getContentJson();
  ObjectNode body=payload(before);assertThat(admin.patch("variants",key,body,17L).path("errorCode").asText()).isEmpty();String version=catalog.catalogVersion();assertThat(admin.patch("variants",key,body,17L).path("errorCode").asText()).isEqualTo("VERSION_CONFLICT");assertThat(catalog.catalogVersion()).isEqualTo(version);assertThat(current("variants",key).path("version").asLong()).isEqualTo(before.path("version").asLong()+1);assertThat(snapshots.findByKeyAndOwnerId(saved,101L).orElseThrow().getContentJson()).isEqualTo(frozen);
 }
}
