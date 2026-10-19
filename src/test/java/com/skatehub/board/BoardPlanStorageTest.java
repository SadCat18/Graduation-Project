package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(classes=BoardPlanStorageTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.config.location=classpath:board-test.properties")
class BoardPlanStorageTest {
    @Configuration @Import({BoardCatalogStorageTest.Config.class,BoardPlanService.class,BoardCalculationService.class}) static class Config{}
    @Autowired BoardPlanService plans;
    @Autowired BoardPlanSnapshotRepository snapshots;
    @Autowired BoardCatalogService catalog;
    @Autowired BoardCalculationService calculation;
    @Autowired BoardDatasetValidator json;
    @Autowired BoardQuoteRepository quotes;
    @Autowired BoardVariantRepository variants;
    @Autowired BoardProductRepository products;
    @Autowired BoardRuleRepository rules;
    @Autowired BoardTemplateRepository templates;
    @Autowired JdbcTemplate jdbc;
    @MockBean AdminAuditLogService audit;
    ObjectNode dataset;
    @BeforeEach void setup(){reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));jdbc.update("MERGE INTO tb_user KEY(user_id) VALUES(101),(102)");dataset=BoardCalculationTest.assembly(json);dataset.remove("catalogVersion");assertThat(catalog.importDataset(dataset,catalog.catalogVersion(),7L).errors()).isEmpty();}
    ObjectNode input(){ObjectNode n=json.object();n.set("demand",BoardCalculationTest.demand(json));n.set("selection",BoardCalculationTest.selection(json));return n;}
    ObjectNode saveInput(){ObjectNode n=input();n.put("name","合成测试方案").put("note","not real product evidence").put("expectedCalculationVersion",calculation.evaluate(input()).path("calculationVersion").asText());return n;}
    @Test void saveRecomputesAndFrozenSnapshotSurvivesQuoteRevision(){JsonNode p=plans.save(101L,saveInput());String key=p.path("key").asText();assertThat(p.path("totalCents").asLong()).isEqualTo(1747);String old=snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson();ObjectNode revised=dataset.deepCopy().put("datasetVersion","synthetic-price-v2");BoardTestData.find(revised,"quotes","SYN-Q-DECK").put("version",2).put("amountCents",201);assertThat(catalog.importDataset(revised,catalog.catalogVersion(),7L).errors()).isEmpty();JsonNode detail=plans.detail(101L,key);assertThat(detail.path("snapshot").path("totalCents").asLong()).isEqualTo(1747);assertThat(detail.path("latestEvaluation").path("totalCents").asLong()).isEqualTo(1847);assertThat(snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson()).isEqualTo(old);assertThat(detail.path("snapshot").path("calculationSources").isEmpty()).isFalse();}
    @Test void quoteConflictDoesNotInsertAndReconfirmationWorks(){ObjectNode body=saveInput();ObjectNode revised=dataset.deepCopy().put("datasetVersion","synthetic-price-v2");BoardTestData.find(revised,"quotes","SYN-Q-DECK").put("version",2).put("amountCents",201);catalog.importDataset(revised,catalog.catalogVersion(),7L);JsonNode conflict=plans.save(101L,body);assertThat(conflict.path("errorCode").asText()).isEqualTo("PRICE_CHANGED");assertThat(conflict.path("evaluation").path("totalCents").asLong()).isEqualTo(1847);assertThat(snapshots.count()).isZero();body.put("expectedCalculationVersion",conflict.path("evaluation").path("calculationVersion").asText());assertThat(plans.save(101L,body).path("key").asText()).isNotBlank();}
    @Test void unrelatedOrUnknownTokenFailsClosedAsCatalogConflict(){ObjectNode body=saveInput().put("expectedCalculationVersion","0".repeat(64));assertThat(plans.save(101L,body).path("errorCode").asText()).isEqualTo("CATALOG_CHANGED");assertThat(snapshots.count()).isZero();}
    @Test void expiredQuoteMustBeReconfirmedBeforeDraftSave(){ObjectNode body=saveInput();var q=quotes.findFirstByKeyOrderByVersionDesc("SYN-Q-DECK").orElseThrow();ObjectNode content=(ObjectNode)json.parse(q.getContentJson());content.put("validUntil",Instant.now().minusSeconds(1).toString());q.setContentJson(json.canonical(content));quotes.saveAndFlush(q);JsonNode conflict=plans.save(101L,body);assertThat(conflict.path("errorCode").asText()).isEqualTo("PRICE_CHANGED");assertThat(snapshots.count()).isZero();body.put("expectedCalculationVersion",conflict.path("evaluation").path("calculationVersion").asText());JsonNode draft=plans.save(101L,body);assertThat(draft.path("snapshotState").asText()).isEqualTo("DRAFT");assertThat(draft.path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");assertThat(draft.path("totalCents").isNull()).isTrue();}
    @Test void allCrossUserOperationsAre404(){String key=plans.save(101L,saveInput()).path("key").asText();for(Runnable action:List.<Runnable>of(()->plans.detail(102L,key),()->plans.patch(102L,key,json.object().put("name","other")),()->plans.copy(102L,key,json.object()),()->plans.delete(102L,key)))assertThatThrownBy(action::run).isInstanceOfSatisfying(BoardRequestException.class,e->assertThat(e.status()).isEqualTo(404));assertThat(plans.list(102L,1,20).getTotal()).isZero();}
    @Test void copyPreservesHistoryHasIndependentKeyAndDelete(){JsonNode saved=plans.save(101L,saveInput());String key=saved.path("key").asText();String frozen=snapshots.findByKeyAndOwnerId(key,101L).orElseThrow().getContentJson();JsonNode copy=plans.copy(101L,key,json.object().put("name","历史副本"));String second=copy.path("key").asText();assertThat(second).isNotEqualTo(key);assertThat(copy.path("copiedFromKey").asText()).isEqualTo(key);assertThat(copy.path("historicalCopy").asBoolean()).isTrue();assertThat(snapshots.findByKeyAndOwnerId(second,101L).orElseThrow().getContentJson()).isEqualTo(frozen);plans.patch(101L,second,json.object().put("name","改名").putNull("note"));assertThat(snapshots.findByKeyAndOwnerId(second,101L).orElseThrow().getContentJson()).isEqualTo(frozen);plans.delete(101L,key);assertThat(plans.detail(101L,second).path("snapshot").path("name").asText()).isEqualTo("改名");assertThat(plans.list(101L,1,20).getTotal()).isEqualTo(1);}
    @Test void rejectsClientOwnerPricePassAndOversizeMetadata(){for(String field:List.of("ownerId","totalCents","validationState")){ObjectNode b=saveInput().put(field,"forged");assertThatThrownBy(()->plans.save(101L,b)).isInstanceOf(BoardRequestException.class);}ObjectNode b=saveInput().put("name","x".repeat(81));assertThatThrownBy(()->plans.save(101L,b)).isInstanceOf(BoardRequestException.class);assertThat(snapshots.count()).isZero();}
    @Test void foreignKeyRejectsAnUnregisteredUser(){assertThatThrownBy(()->plans.save(999999L,saveInput())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(snapshots.count()).isZero();}
    @Test void concurrentCatalogUpdateAndSaveSerializeAndRejectStalePrice()throws Exception{
        ObjectNode body=saveInput(),revised=dataset.deepCopy().put("datasetVersion","synthetic-price-race");BoardTestData.find(revised,"quotes","SYN-Q-DECK").put("version",2).put("amountCents",201);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);doAnswer(i->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test lock timeout");return null;}).when(audit).record(anyLong(),anyString(),isNull(),anyString(),anyString(),anyString());ExecutorService pool=Executors.newFixedThreadPool(2);
        try{Future<?> update=pool.submit(()->catalog.importDataset(revised,catalog.catalogVersion(),7L));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();Future<JsonNode> save=pool.submit(()->plans.save(101L,body));release.countDown();update.get(10,TimeUnit.SECONDS);assertThat(save.get(10,TimeUnit.SECONDS).path("errorCode").asText()).isEqualTo("PRICE_CHANGED");assertThat(snapshots.count()).isZero();}finally{release.countDown();pool.shutdownNow();}
    }
    @Test void latestUnavailableTemplateReturnsGapWithoutChangingSnapshot(){String key=plans.save(101L,saveInput()).path("key").asText();var t=templates.findByKey("SYN-T").orElseThrow();ObjectNode n=(ObjectNode)json.parse(t.getContentJson());n.put("active",false);t.setActive(false);t.setContentJson(json.canonical(n));templates.saveAndFlush(t);JsonNode d=plans.detail(101L,key);assertThat(d.path("latestEvaluation").path("errorCode").asText()).isEqualTo("CATALOG_CHANGED");assertThat(d.path("snapshot").path("totalCents").asLong()).isEqualTo(1747);}
    @Test void writeSnapshotExamplesFromActualIsolatedH2Operations()throws Exception{
        ObjectNode out=json.object().put("evidenceKind","SYNTHETIC_ISOLATED_H2_NOT_MYSQL_OR_PRODUCTION");ObjectNode request=saveInput();JsonNode saved=plans.save(101L,request);String key=saved.path("key").asText();out.set("saveRequest",request);out.set("saveResponse",saved);out.set("detailResponse",plans.detail(101L,key));out.set("copyResponse",plans.copy(101L,key,json.object().put("name","历史副本")));out.set("patchResponse",plans.patch(101L,key,json.object().put("name","测试改名")));ObjectNode revised=dataset.deepCopy().put("datasetVersion","synthetic-example-price-v2");BoardTestData.find(revised,"quotes","SYN-Q-DECK").put("version",2).put("amountCents",201);catalog.importDataset(revised,catalog.catalogVersion(),7L);out.set("savePriceConflict",plans.save(101L,request));java.nio.file.Path path=java.nio.file.Path.of("target/test-evidence/board/B01-snapshot-examples.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,out.toPrettyString(),java.nio.charset.StandardCharsets.UTF_8);
    }
}
