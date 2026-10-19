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
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(classes={BoardPlanStorageTest.Config.class,BoardAdminService.class},webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.config.location=classpath:board-test.properties")
class BoardAdminStorageTest {
    @Autowired BoardAdminService admin;
    @Autowired BoardCatalogService catalog;
    @Autowired BoardCalculationService calculation;
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
    @BeforeEach void setup(){reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));ReflectionTestUtils.setField(catalog,"importEnabled",true);dataset=BoardCalculationTest.assembly(json);dataset.remove("catalogVersion");assertThat(catalog.importDataset(dataset,catalog.catalogVersion(),7L).errors()).isEmpty();reset(audit);}
    @Test void draftCreationCannotImplicitlyActivateAndDoesNotRequireImportSwitch(){ReflectionTestUtils.setField(catalog,"importEnabled",false);ObjectNode p=BoardTestData.find(dataset,"products","SYNTHETIC-P").deepCopy().put("key","ADMIN-DRAFT").put("sourceRecordId","ADMIN-DRAFT");JsonNode result=admin.create("products",p,17L);assertThat(result.path("active").asBoolean()).isFalse();assertThat(result.path("version").asLong()).isEqualTo(1);assertThat(products.findByKey("ADMIN-DRAFT").orElseThrow().isActive()).isFalse();verify(audit).record(eq(17L),eq("BOARD_CATALOG"),isNull(),eq("CREATE_DRAFT"),eq("SUCCESS"),anyString());}
    @Test void activationRejectsUnverifiedSourcesAndDoesNotWrite(){ObjectNode p=BoardTestData.find(dataset,"products","SYNTHETIC-P").deepCopy().put("key","ADMIN-DRAFT").put("sourceRecordId","ADMIN-DRAFT");((ObjectNode)p.path("sources").get(0)).put("verificationStatus","RECORDED");admin.create("products",p,17L);reset(audit);JsonNode result=admin.activate("products","ADMIN-DRAFT",json.object().put("active",true).put("expectedVersion",1),17L);assertThat(result.path("errorCode").asText()).isEqualTo("SELECTION_INVALID");assertThat(products.findByKey("ADMIN-DRAFT").orElseThrow().getVersion()).isEqualTo(1);verifyNoInteractions(audit);}
    @Test void activationRejectsIncompleteOrExpiredQuotes(){ObjectNode q=BoardTestData.find(dataset,"quotes","SYN-Q-DECK").deepCopy().put("key","ADMIN-Q").putNull("shippingCents");assertThat(admin.create("quotes",q,17L).path("errorCode").asText()).isEmpty();JsonNode result=admin.activate("quotes","ADMIN-Q",json.object().put("active",true).put("expectedVersion",1),17L);assertThat(result.path("errorCode").asText()).isEqualTo("SELECTION_INVALID");assertThat(result.path("errors").toString()).contains("ACTIVE_QUOTE_COSTS");assertThat(quotes.findFirstByKeyOrderByVersionDesc("ADMIN-Q").orElseThrow().getVersion()).isEqualTo(1);}
    @Test void patchVersionConflictLeavesRowsAndQuotesHistoryIntact(){ObjectNode b=json.object().put("expectedVersion",1).put("amountCents",201);JsonNode changed=admin.patch("quotes","SYN-Q-DECK",b,17L);assertThat(changed.path("version").asLong()).as(changed.toString()).isEqualTo(2);assertThat(quotes.findAll().stream().filter(q->q.getKey().equals("SYN-Q-DECK")).count()).isEqualTo(2);assertThat(quotes.findAll().stream().filter(q->q.getKey().equals("SYN-Q-DECK")&&q.getVersion()==1).findFirst().orElseThrow().getAmountCents()).isEqualTo(101);assertThat(admin.patch("quotes","SYN-Q-DECK",b,17L).path("errorCode").asText()).isEqualTo("VERSION_CONFLICT");}
    @Test void activeFlagCannotBypassActivationAndScriptsAreRejected(){assertThatThrownBy(()->admin.patch("quotes","SYN-Q-DECK",json.object().put("expectedVersion",1).put("active",false),17L)).isInstanceOf(BoardRequestException.class);ObjectNode b=json.object().put("expectedVersion",1);ObjectNode c=BoardTestData.find(dataset,"rules","SYN-R-DECK_TRUCK_FIT").with("conditions").deepCopy();c.put("script","anything()");b.set("conditions",c);JsonNode result=admin.patch("rules","SYN-R-DECK_TRUCK_FIT",b,17L);assertThat(result.path("errorCode").asText()).isEqualTo("SELECTION_INVALID");assertThat(result.path("errors").toString()).contains("UNKNOWN_RULE_CONDITION");}
    @Test void dependencyDeactivationIsRejectedUntilDependentsAreDisabled(){JsonNode result=admin.activate("variants","SYN-V-DECK",json.object().put("expectedVersion",1).put("active",false),17L);assertThat(result.path("errorCode").asText()).isEqualTo("SELECTION_INVALID");assertThat(variants.findByKey("SYN-V-DECK").orElseThrow().isActive()).isTrue();}
    @Test void whitelistAndPagingAreValidated(){for(String r:List.of("users","products/../../users","scripts"))assertThatThrownBy(()->admin.list(r,1,20)).isInstanceOfSatisfying(BoardRequestException.class,e->assertThat(e.status()).isEqualTo(404));assertThatThrownBy(()->admin.list("quotes",1,51)).isInstanceOf(BoardRequestException.class);for(String r:BoardDatasetValidator.GROUPS)assertThat(admin.list(r,1,20).getTotal()).isPositive();}
    @Test void trialReusesPublicCalculationsAndExclusions(){assertThat(admin.trial(BoardCalculationTest.demand(json))).isEqualTo(calculation.publicView(calculation.recommend(BoardCalculationTest.demand(json))));assertThat(admin.trial(BoardCalculationTest.demand(json).put("budgetCents",100)).path("assembly").path("excludedCounts").path("OVER_BUDGET").asInt()).isEqualTo(2);}
    @Test void auditFailureRollsBackQuoteAndCatalogVersions(){String before=catalog.catalogVersion();long count=quotes.count();doThrow(new IllegalStateException("synthetic audit failure")).when(audit).record(anyLong(),anyString(),isNull(),anyString(),anyString(),anyString());assertThatThrownBy(()->admin.patch("quotes","SYN-Q-DECK",json.object().put("expectedVersion",1).put("amountCents",201),17L)).isInstanceOf(IllegalStateException.class);assertThat(quotes.count()).isEqualTo(count);assertThat(catalog.catalogVersion()).isEqualTo(before);assertThat(quotes.findFirstByKeyOrderByVersionDesc("SYN-Q-DECK").orElseThrow().getVersion()).isEqualTo(1);}
    @Test void concurrentAdminPatchesHaveOnlyOneWriter()throws Exception{ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);try{Callable<JsonNode> task=()->{start.await(5,TimeUnit.SECONDS);return admin.patch("quotes","SYN-Q-DECK",json.object().put("expectedVersion",1).put("amountCents",201),17L);};Future<JsonNode> a=pool.submit(task),b=pool.submit(task);start.countDown();var results=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertThat(results.stream().filter(n->n.path("errorCode").asText().equals("VERSION_CONFLICT")).count()).isEqualTo(1);assertThat(results.stream().filter(n->n.path("version").asLong()==2).count()).isEqualTo(1);}finally{pool.shutdownNow();}}
    @Test void allFiveResourcesCanBeCreatedAsDraftThenActivated(){
        for(String resource:BoardDatasetValidator.GROUPS){JsonNode first=dataset.path(resource).get(0);ObjectNode row=(ObjectNode)first.deepCopy();String k="ADMIN-"+resource;row.put("key",k);if(resource.equals("products"))row.put("sourceRecordId",k);JsonNode created=admin.create(resource,row,17L);assertThat(created.path("errorCode").asText()).as(created.toString()).isEmpty();assertThat(created.path("active").asBoolean()).isFalse();JsonNode active=admin.activate(resource,k,json.object().put("active",true).put("expectedVersion",1),17L);assertThat(active.path("errorCode").asText()).as(active.toString()).isEmpty();assertThat(active.path("active").asBoolean()).isTrue();assertThat(active.path("version").asLong()).isEqualTo(2);}
    }
    @Test void realRecordedQuoteCanBeMaintainedWithoutRewritingSourcePointers(){
        quotes.deleteAllInBatch();templates.deleteAllInBatch();rules.deleteAllInBatch();variants.deleteAllInBatch();products.deleteAllInBatch();jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));assertThat(catalog.importDataset(BoardTestData.real(json),catalog.catalogVersion(),17L).errors()).isEmpty();
        JsonNode patched=admin.patch("quotes","BRG-001--cn-price",json.object().put("expectedVersion",1).put("displayPrice","草稿展示价"),17L);assertThat(patched.path("errorCode").asText()).as(patched.toString()).isEmpty();assertThat(patched.path("active").asBoolean()).isFalse();assertThat(patched.path("sources").get(0).path("evidenceRef").asText()).startsWith("equipment.json#");
    }
}
