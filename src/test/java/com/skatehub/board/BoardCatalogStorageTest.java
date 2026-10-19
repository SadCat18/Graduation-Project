package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.board.entity.BoardProduct;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.data.redis.*;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=BoardCatalogStorageTest.Config.class, webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.config.location=classpath:board-test.properties")
class BoardCatalogStorageTest {
    @Configuration @EnableAutoConfiguration(exclude={RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class})
    @EntityScan(basePackageClasses=BoardProduct.class) @EnableJpaRepositories(basePackageClasses=BoardProductRepository.class)
    @Import({BoardCatalogService.class,BoardDatasetValidator.class})
    static class Config {
        @Bean FlywayMigrationStrategy boardMigration() {return f->{
            // Test-only prerequisite for the real V10 FK; never touches a configured application database.
            try(var connection=f.getConfiguration().getDataSource().getConnection();var statement=connection.createStatement()){
                statement.execute("CREATE TABLE IF NOT EXISTS tb_user(user_id BIGINT PRIMARY KEY)");
            }catch(java.sql.SQLException e){throw new IllegalStateException(e);}
            f.baseline();f.migrate();};}
    }
    @Autowired BoardCatalogService service;
    @Autowired BoardDatasetValidator validator;
    @Autowired BoardProductRepository products;
    @Autowired BoardVariantRepository variants;
    @Autowired BoardQuoteRepository quotes;
    @Autowired BoardRuleRepository rules;
    @Autowired BoardTemplateRepository templates;
    @Autowired BoardCatalogMetaRepository meta;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @MockBean AdminAuditLogService audit;
    @BeforeEach void clear() {
        reset(audit);quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();
        jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",validator.hash(validator.array()));
    }
    @Test void v9MigratesOnlyNewTablesAndJpaValidatesSchema() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("10");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name like 'tb_board_%'",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='tb_skate_equipment'",Integer.class)).isZero();
    }
    @Test void realDraftImportIsIdempotentAndNeverPublic() {
        JsonNode d=BoardTestData.real(validator);String token=service.catalogVersion();
        var dry=service.validate(d);assertThat(dry.valid()).isTrue();assertThat(dry.gaps().size()).isEqualTo(299);assertThat(products.count()).isZero();verifyNoInteractions(audit);
        var first=service.importDataset(d,token,1L);assertThat(first.errors()).isEmpty();assertThat(first.created()).isEqualTo(266);
        assertThat(products.count()).isEqualTo(50);assertThat(variants.count()).isEqualTo(114);assertThat(quotes.count()).isEqualTo(60);assertThat(rules.count()).isEqualTo(39);assertThat(templates.count()).isEqualTo(3);
        var again=service.importDataset(d,first.catalogVersion(),1L);assertThat(again.unchanged()).isEqualTo(266);assertThat(again.created()+again.updated()).isZero();
        assertThat(service.catalog(null,1,20).getTotal()).isZero();
        assertThatThrownBy(()->service.variant(d.path("variants").get(0).path("key").asText())).isInstanceOf(BoardRequestException.class);
        assertThat(service.validate(d).valid()).isTrue();
    }
    @Test void sameVersionContentConflictDoesNotWrite() {
        ObjectNode d=(ObjectNode)BoardTestData.real(validator);service.importDataset(d,service.catalogVersion(),1L);
        String before=meta.findById(1L).orElseThrow().getDatasetFingerprints();BoardTestData.find(d,"quotes","BRG-001--cn-price").put("amountCents",10011);
        var result=service.importDataset(d,service.catalogVersion(),1L);assertThat(result.errorCode()).isEqualTo("VERSION_CONFLICT");assertThat(result.created()+result.updated()).isZero();
        assertThat(quotes.findFirstByKeyOrderByVersionDesc("BRG-001--cn-price").orElseThrow().getAmountCents()).isEqualTo(10010);
        assertThat(meta.findById(1L).orElseThrow().getDatasetFingerprints()).isEqualTo(before);
    }
    @Test void quoteNewVersionKeepsImmutableHistoryAndRejectsOlderVersion() {
        ObjectNode d=(ObjectNode)BoardTestData.real(validator);service.importDataset(d,service.catalogVersion(),1L);String original=quotes.findFirstByKeyOrderByVersionDesc("BRG-001--cn-price").orElseThrow().getContentJson();
        ObjectNode changed=d.deepCopy();changed.put("datasetVersion","test-revision-2");BoardTestData.find(changed,"quotes","BRG-001--cn-price").put("version",2).put("amountCents",10011);
        var result=service.importDataset(changed,service.catalogVersion(),1L);assertThat(result.errorCode()).isNull();assertThat(result.updated()).isEqualTo(1);assertThat(result.unchanged()).isEqualTo(265);
        assertThat(quotes.count()).isEqualTo(61);assertThat(quotes.findAll().stream().filter(q->q.getKey().equals("BRG-001--cn-price")&&q.getVersion()==1).findFirst().orElseThrow().getContentJson()).isEqualTo(original);
        assertThat(service.importDataset(d,service.catalogVersion(),1L).errorCode()).isEqualTo("VERSION_CONFLICT");
    }
    @Test void hardErrorRejectsWholeBatch() {
        ObjectNode d=(ObjectNode)BoardTestData.real(validator);BoardTestData.find(d,"variants",d.path("variants").get(0).path("key").asText()).put("productKey","MISSING");
        var result=service.importDataset(d,service.catalogVersion(),1L);assertThat(result.errorCode()).isEqualTo("SELECTION_INVALID");assertThat(result.rejected()).isEqualTo(266);assertThat(products.count()+variants.count()+quotes.count()).isZero();verifyNoInteractions(audit);
    }
    @Test void lateAuditFailureRollsBackAllRowsAndMetadata() {
        doThrow(new IllegalStateException("synthetic audit failure")).when(audit).record(anyLong(),anyString(),isNull(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->service.importDataset(BoardTestData.real(validator),service.catalogVersion(),1L)).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()+variants.count()+quotes.count()+rules.count()+templates.count()).isZero();
        assertThat(meta.findById(1L).orElseThrow().getDatasetFingerprints()).isEqualTo("{}");
    }
    @Test void publishesOnlyActivatedSyntheticRowsAndOmitsPrivateProvenance() {
        var d=BoardTestData.synthetic(validator);var imported=service.importDataset(d,service.catalogVersion(),1L);assertThat(imported.errors()).isEmpty();
        var page=service.catalog("COMPLETE",1,10);assertThat(page.getTotal()).isEqualTo(1);assertThat(page.getList().get(0).path("key").asText()).isEqualTo("SYNTHETIC-P");assertThat(page.getList().get(0).toString()).doesNotContain("evidenceRef","synthetic-memory","rawPrice","dataset_fingerprints");
        var detail=service.variant("SYNTHETIC-V");assertThat(detail.path("eligibility").path("exactPriceEligible").asBoolean()).isTrue();assertThat(detail.path("displayQuotes").get(0).path("amountCents").longValue()).isEqualTo(10010);
        assertThatThrownBy(()->service.catalog(null,0,20)).isInstanceOf(BoardRequestException.class);assertThatThrownBy(()->service.catalog(null,1,51)).isInstanceOf(BoardRequestException.class);
    }
    @Test void staleExpectedCatalogDoesNotWrite() {
        String old=service.catalogVersion();var d=BoardTestData.synthetic(validator);service.importDataset(d,old,1L);
        var result=service.importDataset(d,old,1L);assertThat(result.errorCode()).isEqualTo("CATALOG_CHANGED");assertThat(result.created()+result.updated()).isZero();assertThat(products.count()).isEqualTo(1);
    }
    @Test void concurrentImportsSerializeTheExpectedVersionCheck() throws Exception {
        String expected=service.catalogVersion();var dataset=BoardTestData.synthetic(validator);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<BoardDtos.ImportResult> task=()->{start.await(5,TimeUnit.SECONDS);return service.importDataset(dataset,expected,1L);};
            Future<BoardDtos.ImportResult> first=pool.submit(task),second=pool.submit(task);start.countDown();
            var results=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(results.stream().filter(r->r.errorCode()==null).count()).isEqualTo(1);
            assertThat(results.stream().filter(r->"CATALOG_CHANGED".equals(r.errorCode())).count()).isEqualTo(1);assertThat(products.count()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void importDisabledByDefaultPolicyCanBlockWrites() {
        ReflectionTestUtils.setField(service,"importEnabled",false);
        try {assertThatThrownBy(()->service.importDataset(BoardTestData.synthetic(validator),service.catalogVersion(),1L)).isInstanceOf(BoardRequestException.class);assertThat(products.count()).isZero();}
        finally {ReflectionTestUtils.setField(service,"importEnabled",true);}
    }
    @Test void productAndVariantUpdatesRequireExplicitHigherVersion() {
        ObjectNode d=(ObjectNode)BoardTestData.real(validator);service.importDataset(d,service.catalogVersion(),1L);
        d.put("datasetVersion","test-product-variant-v2");((ObjectNode)d.path("products").get(0)).put("model","Recorded draft revision");
        assertThat(service.importDataset(d,service.catalogVersion(),1L).errorCode()).isEqualTo("VERSION_CONFLICT");
        ((ObjectNode)d.path("products").get(0)).put("version",2);((ObjectNode)d.path("variants").get(0)).put("version",2).put("label","Recorded draft revision");
        var result=service.importDataset(d,service.catalogVersion(),1L);assertThat(result.errorCode()).isNull();assertThat(result.updated()).isEqualTo(2);assertThat(result.unchanged()).isEqualTo(264);
        assertThat(products.findByKey("DECK-001").orElseThrow().getVersion()).isEqualTo(2);assertThat(variants.findByKey(d.path("variants").get(0).path("key").asText()).orElseThrow().getVersion()).isEqualTo(2);
    }
}
