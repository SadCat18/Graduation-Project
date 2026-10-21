package com.skatehub.service;

import com.fasterxml.jackson.databind.*;
import com.skatehub.controller.*;
import com.skatehub.dao.*;
import com.skatehub.pojo.*;
import com.skatehub.pojo.skate.*;
import com.skatehub.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.data.redis.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** All persisted records are isolated H2 fixtures; no production data, migration or provider evidence. */
@SpringBootTest(classes=PublicReadContractTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.config.location=optional:classpath:public-read-contract-no-config.properties",
 "spring.datasource.url=jdbc:h2:mem:public-read-contract;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","logging.level.root=WARN"})
class PublicReadContractTest {
 @Configuration
 @EnableAutoConfiguration(exclude={RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
 @EntityScan(basePackageClasses=Place.class) @EnableJpaRepositories(basePackageClasses=PlaceRepository.class)
 @Import({PublicContentService.class,PlaceReviewService.class,PostService.class,EquipmentService.class,TrainingPlanService.class}) static class Config{}
 @Autowired PublicContentService content; @Autowired PlaceReviewService reviews; @Autowired PostService posts; @Autowired EquipmentService equipment; @Autowired TrainingPlanService training;
 @Autowired PlaceRepository places; @SpyBean PlaceReviewRepository reviewRepository; @Autowired TrainingPlanRepository planRepository; @Autowired TrainingTaskRepository tasks;
 @SpyBean PostRepository postRepository; @SpyBean SkateEquipmentRepository equipmentRepository; @Autowired UserRepository users;
 @MockBean CommunityBulletinService bulletins; @MockBean UserGrowthService growth; @MockBean MessageNotifyService notify; @MockBean ModerationRuleService moderation;
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @BeforeEach void clear(){reviewRepository.deleteAllInBatch();places.deleteAllInBatch();tasks.deleteAllInBatch();planRepository.deleteAllInBatch();postRepository.deleteAllInBatch();equipmentRepository.deleteAllInBatch();}
 TrainingPlan plan(Integer days,int... available){TrainingPlan plan=new TrainingPlan();plan.setPlanName("Synthetic plan");plan.setDurationDays(days);plan=planRepository.saveAndFlush(plan);for(int day:available){TrainingTask task=new TrainingTask();task.setPlanId(plan.getPlanId());task.setTaskTitle("Synthetic day");task.setDayNo(day);tasks.saveAndFlush(task);}return plan;}
 JsonNode coverage(TrainingPlan plan){return mapper.valueToTree(training.detail(plan.getPlanId()).get("scheduleCoverage"));}
 @Test void scheduleSevenThreeAndFiveTwoReportActualMissingDays(){
  JsonNode seven=coverage(plan(7,3,1,2,2));assertThat(seven.path("availableDays").toString()).isEqualTo("[1,2,3]");assertThat(seven.path("missingDays").toString()).isEqualTo("[4,5,6,7]");assertThat(seven.path("complete").asBoolean()).isFalse();
  JsonNode five=coverage(plan(5,2,1));assertThat(five.path("availableDays").toString()).isEqualTo("[1,2]");assertThat(five.path("missingDays").toString()).isEqualTo("[3,4,5]");
 }
 @Test void completeEmptyAndAnomalousSchedulesDoNotInventCoverage(){
  assertThat(coverage(plan(3,3,1,2,2)).path("complete").asBoolean()).isTrue();JsonNode empty=coverage(plan(3));assertThat(empty.path("availableDays").isEmpty()).isTrue();assertThat(empty.path("missingDays").toString()).isEqualTo("[1,2,3]");
  TrainingPlan malformed=plan(3,1,2,3,0,-1,4);TrainingTask task=new TrainingTask();task.setTaskTitle("Synthetic unknown day");task.setPlanId(malformed.getPlanId());tasks.saveAndFlush(task);
  JsonNode bad=coverage(malformed);assertThat(bad.path("complete").asBoolean()).isFalse();assertThat(bad.path("diagnostics").size()).isEqualTo(4);assertThat(bad.path("availableDays").toString()).isEqualTo("[1,2,3]");
  for(Integer invalid:Arrays.asList(null,0,-1)){JsonNode result=coverage(plan(invalid,1));assertThat(result.path("complete").asBoolean()).isFalse();assertThat(result.path("diagnostics").isEmpty()).isFalse();}
 }
 Place place(String name,String oldScore,int oldCount){Place p=new Place();p.setName(name);p.setIntro("Synthetic flat ground");p.setScore(new BigDecimal(oldScore));p.setReviewCount(oldCount);p.setVerificationStatus("APPROVED");p.setVerificationNote("Synthetic legacy note");p.setCreateTime(LocalDateTime.of(2026,1,1,0,0));return places.saveAndFlush(p);}
 void review(Place p,int score){PlaceReview r=new PlaceReview();r.setPlaceId(p.getPlaceId());r.setUserId(999L);r.setScore(score);reviewRepository.saveAndFlush(r);}
 @Test void localReviewsAreBatchedSameSourceAndNeverOverwriteHistoricalValues(){
  Place noReviews=place("Synthetic seed only","4.9",203),rated=place("Synthetic local reviews","1.0",100);review(rated,5);review(rated,4);review(rated,4);
  clearInvocations(reviewRepository);var list=content.places();assertThat(list.get(0).place().getPlaceId()).isEqualTo(rated.getPlaceId());assertThat(list.get(0).localReviewSummary().score()).isEqualByComparingTo("4.3");assertThat(list.get(0).localReviewSummary().count()).isEqualTo(3);
  assertThat(list.get(1).localReviewSummary().score()).isNull();assertThat(list.get(1).localReviewSummary().count()).isZero();verify(reviewRepository,times(1)).aggregateLocalReviews(anyCollection());verify(reviewRepository,never()).findByPlaceIdOrderByCreateTimeDesc(anyLong());
  JsonNode detail=mapper.valueToTree(content.placeDetail(rated.getPlaceId()));assertThat(detail.path("localReviewSummary")).isEqualTo(mapper.valueToTree(list.get(0).localReviewSummary()));assertThat(reviews.listByPlace(rated.getPlaceId())).hasSize(3);
  JsonNode legacyJson=mapper.valueToTree(rated),viewJson=mapper.valueToTree(list.get(0));legacyJson.fields().forEachRemaining(field->assertThat(viewJson.get(field.getKey())).isEqualTo(field.getValue()));assertThat(viewJson.has("place")).isFalse();
  assertThat(places.findById(noReviews.getPlaceId()).orElseThrow().getScore()).isEqualByComparingTo("4.9");assertThat(places.findById(noReviews.getPlaceId()).orElseThrow().getReviewCount()).isEqualTo(203);
  assertThat(places.findById(rated.getPlaceId()).orElseThrow().getScore()).isEqualByComparingTo("1.0");
 }
 @Test void failedReviewAggregateIsAnErrorNotAnEmptySummary()throws Exception{
  place("Synthetic failure","4.9",203);doThrow(new IllegalStateException("synthetic storage failure")).when(reviewRepository).aggregateLocalReviews(anyCollection());assertThatThrownBy(content::places).hasMessage("synthetic storage failure");
  var mvc=MockMvcBuilders.standaloneSetup(new PublicController(posts,mock(ActivityService.class),content,mock(PublicUserProfileService.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
  var response=mvc.perform(get("/api/public/places")).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);JsonNode body=mapper.readTree(response.getContentAsString());assertThat(body.path("code").asInt()).isEqualTo(500);assertThat(body.has("data")).isFalse();
 }
 @Test void unapprovedPlacesAreNotAddedToThePublicReviewBatch(){Place hidden=place("Synthetic hidden","5.0",999);hidden.setVerificationStatus("PENDING");places.saveAndFlush(hidden);review(hidden,5);Place shown=place("Synthetic visible","4.9",203);clearInvocations(reviewRepository);var result=content.places();assertThat(result).hasSize(1);assertThat(result.get(0).place().getPlaceId()).isEqualTo(shown.getPlaceId());verify(reviewRepository).aggregateLocalReviews(argThat(ids->ids.size()==1&&ids.contains(shown.getPlaceId())));}

 Post post(String category){Post p=new Post();p.setUserId(999L);p.setTitle("Synthetic category");p.setContent("Fixture only");p.setCategory(category);return postRepository.saveAndFlush(p);}
 @Test void postCategoriesAreGlobalStableAndDoNotAliasOrBroadenSuggestions(){
  for(int i=0;i<25;i++)post("历史类别"+i);post("装备讨论");post("装备分享");post(" ");post(null);
  JsonNode result=mapper.valueToTree(posts.categories());List<String> values=new ArrayList<>();result.path("filterOptions").forEach(row->values.add(row.path("key").asText()));assertThat(values).contains("历史类别24","装备讨论","装备分享");assertThat(values).isSorted().doesNotHaveDuplicates();assertThat(result.path("aliasMap").isEmpty()).isTrue();
  List<String> create=new ArrayList<>();result.path("createOptions").forEach(row->create.add(row.path("key").asText()));assertThat(create).containsExactlyElementsOf(PostCategoryOptions.CREATE).doesNotContain("历史类别24","装备分享");
  assertThat(posts.list(null,"装备讨论","latest",1,1).getTotal()).isEqualTo(1);assertThat(posts.list(null,"装备分享","latest",1,1).getTotal()).isEqualTo(1);assertThat((JsonNode)mapper.valueToTree(posts.categories())).isEqualTo(result);
 }
 SkateEquipment item(String category,String status){SkateEquipment e=new SkateEquipment();e.setEquipmentName("Synthetic equipment");e.setCategory(category);e.setBrand("Fixture");e.setStatus(status);return equipmentRepository.saveAndFlush(e);}
 @Test void equipmentCategoriesCoverAllPagesAndUseSameVisibility(){for(int i=0;i<22;i++)item("类别"+i,"1");item("工具","1");item("砂纸","1");item("隐藏分类","0");item(" ","1");
  assertThat(equipment.categories()).extracting(row->row.get("key")).contains("工具","砂纸","类别21").doesNotContain("隐藏分类"," ").isSorted().doesNotHaveDuplicates();assertThat(equipment.list(1,1,null,null,null,null,null).getTotal()).isEqualTo(25);assertThat(equipment.list(1,1,null,"工具",null,null,null).getTotal()).isEqualTo(1);
 }
 @Test void emptyCategoriesRemainValidAndStorageErrorsPropagate(){assertThat(posts.categories().get("filterOptions")).isNotNull();assertThat(equipment.categories()).isEmpty();doThrow(new IllegalStateException("synthetic categories failure")).when(equipmentRepository).findPublicCategories();assertThatThrownBy(equipment::categories).hasMessage("synthetic categories failure");}
 @Test void frozenReadPathsSerializeRealServicesAndH2Responses()throws Exception{
  TrainingPlan p=plan(7,1,2,3);place("Synthetic no local ratings","4.9",203);post("装备讨论");post("装备分享");item("工具","1");item("砂纸","1");
  var mvc=MockMvcBuilders.standaloneSetup(new PublicController(posts,mock(ActivityService.class),content,mock(PublicUserProfileService.class)),new SkateController(mock(TrickService.class),training,mock(SkateEventService.class),equipment)).setControllerAdvice(new GlobalExceptionHandler()).build();
  Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("evidenceLayer","MOCKMVC_REAL_SERVICES_ISOLATED_H2_SYNTHETIC_FIXTURES_NOT_LIVE_HTTP_MYSQL_PRODUCTION");
  for(String path:List.of("/api/skate/training-plans/"+p.getPlanId(),"/api/public/places","/api/public/places/"+places.findAll().get(0).getPlaceId(),"/api/public/posts/categories","/api/skate/equipments/categories")){
   var response=mvc.perform(get(path)).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);JsonNode body=mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertThat(body.path("code").asInt()).isEqualTo(200);evidence.put(path,body);
  }
  Path output=Path.of("target/test-evidence/public-remediation/backend-contract-examples.json");Files.createDirectories(output.getParent());Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
 }
}
