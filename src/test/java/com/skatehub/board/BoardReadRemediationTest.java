package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.system.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(classes={BoardPlanStorageTest.Config.class,BoardAdminService.class},webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.config.location=classpath:board-test.properties","spring.datasource.url=jdbc:h2:mem:board-read-remediation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ExtendWith(OutputCaptureExtension.class)
class BoardReadRemediationTest {
 @Autowired BoardCatalogService catalog; @Autowired BoardCalculationService calculation; @Autowired BoardPlanService plans; @Autowired BoardAdminService admin;
 @Autowired BoardDatasetValidator json; @Autowired BoardCatalogMetaRepository meta; @Autowired BoardPlanSnapshotRepository snapshots;
 @SpyBean BoardProductRepository products; @Autowired BoardVariantRepository variants; @SpyBean BoardQuoteRepository quotes;
 @Autowired BoardRuleRepository rules; @Autowired BoardTemplateRepository templates;
 @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactions; @MockBean AdminAuditLogService audit;
 @BeforeEach void setup(){
  reset(audit);snapshots.deleteAllInBatch();quotes.deleteAllInBatch();variants.deleteAllInBatch();rules.deleteAllInBatch();templates.deleteAllInBatch();products.deleteAllInBatch();
  jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",json.hash(json.array()));jdbc.update("MERGE INTO tb_user KEY(user_id) VALUES(101),(102)");
  ObjectNode d=BoardCalculationTest.assembly(json);d.remove("catalogVersion");assertThat(catalog.importDataset(d,catalog.catalogVersion(),7L).errors()).isEmpty();
 }
 @Test void secondPublicReadCompletesWhileFirstOuterTransactionIsHeld()throws Exception{
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
  try{
   Future<?> first=pool.submit(()->new TransactionTemplate(transactions).execute(status->{catalog.calculationCatalog();held.countDown();await(release);return null;}));
   assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();Future<?> second=pool.submit(catalog::calculationCatalog);
   assertThat(second.get(5,TimeUnit.SECONDS)).isNotNull();assertThat(release.getCount()).isEqualTo(1);release.countDown();first.get(5,TimeUnit.SECONDS);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void publicReadKeepsOneVersionAcrossConcurrentCommittedQuoteWrite()throws Exception{
  String oldVersion=catalog.catalogVersion();CountDownLatch reading=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean first=new AtomicBoolean(true);
  doAnswer(i->{if(first.compareAndSet(true,false)){reading.countDown();await(release);}return products.findAll(org.springframework.data.domain.Sort.unsorted());}).when(products).findAll();
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try{
   Future<ObjectNode> read=pool.submit(catalog::calculationCatalog);assertThat(reading.await(5,TimeUnit.SECONDS)).isTrue();
   JsonNode written=admin.patch("quotes","SYN-Q-DECK",json.object().put("expectedVersion",1).put("amountCents",201),7L);assertThat(written.path("version").asLong()).isEqualTo(2);release.countDown();
   ObjectNode snapshot=read.get(5,TimeUnit.SECONDS);assertThat(snapshot.path("catalogVersion").asText()).isEqualTo(oldVersion);assertThat(BoardTestData.find(snapshot,"quotes","SYN-Q-DECK").path("version").asLong()).isEqualTo(1);assertThat(BoardTestData.find(snapshot,"quotes","SYN-Q-DECK").path("amountCents").asLong()).isEqualTo(101);
   assertThat(catalog.calculationCatalog().path("catalogVersion").asText()).isNotEqualTo(oldVersion);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void latestInactiveDoesNotResurrectHistoryAndReadQueriesUseLatestOnly(){
  long initial=quotes.count();for(int version=1;version<=3;version++)assertThat(admin.patch("quotes","SYN-Q-DECK",json.object().put("expectedVersion",version).put("amountCents",200+version),7L).path("version").asInt()).isEqualTo(version+1);
  assertThat(admin.activate("quotes","SYN-Q-DECK",json.object().put("expectedVersion",4).put("active",false),7L).path("version").asInt()).isEqualTo(5);assertThat(quotes.count()).isEqualTo(initial+4);
  clearInvocations(quotes);ObjectNode snapshot=catalog.calculationCatalog();verify(quotes).findLatest();verify(quotes,never()).findAll();assertThat(snapshot.path("quotes").size()).isEqualTo(initial);assertThat(BoardTestData.find(snapshot,"quotes","SYN-Q-DECK").path("active").asBoolean()).isFalse();
  var page=admin.list("quotes",1,1);assertThat(page.getTotal()).isEqualTo(initial);verify(quotes).findLatestPage(argThat(p->p.getPageSize()==1));verify(quotes,never()).findAll();
  ObjectNode body=json.object();body.set("demand",BoardCalculationTest.demand(json));body.set("selection",BoardCalculationTest.selection(json));assertThat(calculation.evaluate(body).path("totalCents").isNull()).isTrue();
  clearInvocations(quotes);catalog.catalog(null,1,20);verify(quotes).findLatestBySourceProductKeyIn(anyCollection());verify(quotes,never()).findBySourceProductKeyIn(anyCollection());
 }
 @Test void administratorPagesUseSqlTotalsKeyOrderingAndIncludeDrafts(){
  for(String group:BoardDatasetValidator.GROUPS){
   var first=admin.list(group,1,1);var second=admin.list(group,2,1);List<String> keys=new ArrayList<>(catalog.current(group).keySet());assertThat(first.getTotal()).isEqualTo(keys.size());assertThat(first.getList().get(0).path("key").asText()).isEqualTo(keys.get(0));if(keys.size()>1)assertThat(second.getList().get(0).path("key").asText()).isEqualTo(keys.get(1));
  }
  clearInvocations(products);admin.list("products",1,1);verify(products).findAll(argThat((org.springframework.data.domain.Pageable p)->p.getPageSize()==1));verify(products,never()).findAll();
 }
 @Test void missingMetadataHasInternalDiagnosticButSafeLocal503Envelope(CapturedOutput output)throws Exception{
  BoardCatalogMetaRepository missing=mock(BoardCatalogMetaRepository.class);when(missing.findById(1L)).thenReturn(Optional.empty());when(missing.lockCatalog()).thenReturn(null);
  BoardCatalogService subject=new BoardCatalogService(products,variants,quotes,rules,templates,missing,json,audit);
  var mvc=MockMvcBuilders.standaloneSetup(new BoardCatalogController(subject)).setControllerAdvice(new BoardErrorAdvice()).build();var response=mvc.perform(get("/api/skate/board-assistant/catalog")).andReturn().getResponse();
  assertThat(response.getStatus()).isEqualTo(503);JsonNode envelope=json.parse(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertThat(envelope.path("code").asInt()).isEqualTo(503);assertThat(envelope.path("message").asText()).isEqualTo("器材目录暂时不可用，请稍后重试");assertThat(envelope.toString()).doesNotContain("tb_board","V9","id=1");
  assertThatThrownBy(subject::lockedCalculationCatalog).isInstanceOfSatisfying(BoardRequestException.class,e->assertThat(e.status()).isEqualTo(503));assertThat(output.getOut()).contains("Board catalog metadata missing", "tb_board_catalog_meta", "V9");
 }
 @Test void namedMismatchPolicyDoesNotRejectASeparateExplicitSyntheticQuote(){
  ObjectNode q=BoardTestData.find(BoardTestData.synthetic(json),"quotes","SYNTHETIC-Q").deepCopy();
  q.put("pricingMode","REFERENCE").put("key","TRUCK-001--cn-price-corrected");q.set("priceAssumptions",json.array().add("Synthetic correctly scoped new quote control only"));
  assertThat(BoardDatasetValidator.referenceQuote(q)).isTrue();q.put("key","TRUCK-001--cn-price");assertThat(BoardDatasetValidator.referenceQuote(q)).isFalse();
 }
 @Test void stableKeysHaveSharedAsciiAndLengthBoundaries(){
  for(String key:List.of("A","abc._-09","a".repeat(200))){assertThat(BoardDatasetValidator.stableKey(key)).isTrue();assertThat(BoardCalculationService.key(json.object().put("key",key),"key")).isEqualTo(key);}
  for(String key:List.of("",":bad","catalog:example","a".repeat(201),"汉字","-leading")){assertThat(BoardDatasetValidator.stableKey(key)).isFalse();assertThatThrownBy(()->BoardCalculationService.key(json.object().put("key",key),"key")).isInstanceOf(BoardRequestException.class);}
 }
 private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test deadlock guard");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
