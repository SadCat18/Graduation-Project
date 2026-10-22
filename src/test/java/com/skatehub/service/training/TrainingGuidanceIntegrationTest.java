package com.skatehub.service.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.dao.*;
import com.skatehub.pojo.User;
import com.skatehub.service.*;
import com.skatehub.service.ai.AiGatewayService;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.skatehub.service.training.GuidanceJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,classes=TrainingGuidanceIntegrationTest.App.class,properties={
 "spring.datasource.url=jdbc:h2:mem:guidance;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false","spring.flyway.enabled=false","logging.level.root=WARN"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingGuidanceIntegrationTest {
 @SpringBootConfiguration
 @EnableAutoConfiguration(exclude={RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class,SecurityAutoConfiguration.class})
 @EntityScan("com.skatehub.pojo") @EnableJpaRepositories("com.skatehub.dao")
 @Import({TrainingGuidanceService.class,GuidanceContentService.class,GuidanceAiService.class,UserGrowthService.class,TrainingPlanService.class})
 static class App { @Bean TestClock clock(){return new TestClock();} }
 static class TestClock extends Clock {
  volatile Instant instant=Instant.parse("2026-10-22T04:00:00Z");
  public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}
  public Instant instant(){return instant;}
  void day(String day){instant=LocalDate.parse(day).atTime(12,0).atZone(getZone()).toInstant();}
 }
 @Autowired TrainingGuidanceService training;
 @Autowired GuidanceContentService content;
 @Autowired GuidanceAiService ai;
 @Autowired TrainingPlanService legacy;
 @Autowired UserRepository users;
 @Autowired JdbcTemplate jdbc;
 @Autowired TestClock clock;
 @Autowired PlatformTransactionManager manager;
 @MockBean AiGatewayService gateway;
 CurrentUser user,other,admin;
 @BeforeAll void migrate() {
  // Hibernate builds the existing tables; remove the new mapped mode to exercise actual V11 DDL against an old shape.
  jdbc.execute("alter table tb_user_training_plan drop column mode");
  jdbc.update("insert into tb_user_training_plan(user_plan_id,user_id,plan_id,status) values(99999,99999,321,'QUIT')");
  var pop=new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__training_guidance.sql")); pop.execute(Objects.requireNonNull(jdbc.getDataSource()));
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__training_guidance_sources.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
  assertThat(jdbc.queryForObject("select mode from tb_user_training_plan where user_plan_id=99999",String.class)).isEqualTo("LEGACY");
  assertThat(jdbc.queryForObject("select plan_id from tb_user_training_plan where user_plan_id=99999",Long.class)).isEqualTo(321);
 }
 @BeforeEach void setup() {
  for(String table:List.of("tb_guidance_request","tb_guidance_active","tb_guidance_completion","tb_guidance_practice","tb_guidance_plan","tb_guidance_template"))jdbc.update("delete from "+table);
  jdbc.update("delete from tb_user_training_record"); jdbc.update("delete from tb_user_training_plan");
  jdbc.update("delete from tb_user_growth_log");
  var pop=new ResourceDatabasePopulator(new ClassPathResource("db/migration/V12__training_guidance_content.sql"));pop.execute(Objects.requireNonNull(jdbc.getDataSource()));
  jdbc.update("update tb_guidance_source set status='AVAILABLE'");
  clock.day("2026-10-22"); user=newUser("USER");other=newUser("USER");admin=newUser("ADMIN");reset(gateway);
 }
 CurrentUser newUser(String role){User u=new User();u.setUsername("tg"+uuid().substring(0,12));u.setPassword("SYNTHETIC-LOCAL-ONLY");return new CurrentUser(users.saveAndFlush(u).getUserId(),role,"synthetic");}
 ObjectNode createBody(){return obj("requestKey",uuid(),"templateId","basic-foundations","templateVersion",1,"goal","记录基础练习","selfLevel","BEGINNER","trainingDays",List.of(1,2,3,4,5,6,7),"sessionMinutes",20,"startDate",training.today().toString());}
 ObjectNode create(){return (ObjectNode)training.create(user,createBody());}
 ObjectNode feedback(JsonNode p,String completion,int minutes,String...reasons){
  JsonNode t=p.path("tasks").get(0);return feedbackTask(p,t,completion,minutes,reasons);
 }
 ObjectNode feedbackTask(JsonNode p,JsonNode t,String completion,int minutes,String... reasons){
  ArrayNode answers=arr();t.path("selfChecks").forEach(q->answers.add(obj("questionId",q.path("id"),"answer","YES")));
  return obj("requestKey",uuid(),"expectedVersion",p.path("version"),"instanceId",t.path("id"),"practiceDate",training.today().toString(),"completion",completion,"actualMinutes",minutes,"difficulty","MODERATE","selfChecks",answers,"reasons",List.of(reasons),"note","");
 }
 long id(JsonNode p){return p.path("id").asLong();}
 void error(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){assertThatThrownBy(call).isInstanceOfSatisfying(GuidanceException.class,e->assertThat(e.errorCode).isEqualTo(code));}
 ObjectNode decision(JsonNode p,JsonNode s,String action){return obj("requestKey",uuid(),"expectedVersion",p.path("version"),"decision",action);}
 @Test void seedIsPublishableAndPreviewIsReadOnlyAndSplitsWithinUserBudget(){
  content.validate(content.template("basic-foundations",1L,false)); ObjectNode body=createBody();body.put("sessionMinutes",5);
  JsonNode p=training.preview(body);assertThat(p.path("tasks")).hasSize(4);assertThat(p.path("tasks").get(0).path("segments")).hasSize(2);
  for(JsonNode t:p.path("tasks"))for(JsonNode s:t.path("segments"))assertThat(s.path("minutes").asInt()).isLessThanOrEqualTo(5);
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_plan",Long.class)).isZero();
  assertThat(p.path("tasks").get(1).path("unlocked").asBoolean()).isFalse();verifyNoInteractions(gateway);
 }
 @Test void replayMultiplePracticesStatisticsRewardAndRealTransactions(){
  JsonNode p=create();ObjectNode body=feedback(p,"PARTIAL",3,"TIME_SHORTAGE");JsonNode response=training.feedback(user,id(p),body);
  assertThat(training.feedback(user,id(p),body).toString()).isEqualTo(response.toString());
  ObjectNode changed=body.deepCopy();changed.put("actualMinutes",4);error("IDEMPOTENCY_CONFLICT",()->training.feedback(user,id(p),changed));
  JsonNode stats=training.statistics(user,id(p));assertThat(stats.path("practiceCount").asInt()).isEqualTo(1);assertThat(stats.path("practiceMinutes").asInt()).isEqualTo(3);assertThat(stats.path("completedTasks").asInt()).isZero();
  JsonNode next=response.path("plan");JsonNode done=training.feedback(user,id(p),feedback(next,"COMPLETED",7));
  assertThat(done.path("record").path("expAwarded").asInt()).isEqualTo(8);
  JsonNode repeat=training.feedback(user,id(p),feedback(done.path("plan"),"COMPLETED",4));
  assertThat(repeat.path("record").path("expAwarded").asInt()).isZero();
  assertThat(training.statistics(user,id(p)).path("practiceMinutes").asInt()).isEqualTo(14);
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,id(p))).isEqualTo(1);
  assertThat(users.findById(user.id()).orElseThrow().getExp()).isEqualTo(8);
 }
 @Test void sameKeyConcurrentRequestsReturnOriginalAndDifferentKeysUseVersion(){
  JsonNode p=create();ObjectNode b=feedback(p,"PARTIAL",2);
  var results=parallel(()->training.feedback(user,id(p),b),()->training.feedback(user,id(p),b));assertThat(results.get(0).toString()).isEqualTo(results.get(1).toString());
  assertThat(training.statistics(user,id(p)).path("practiceCount").asInt()).isEqualTo(1);
  ObjectNode another=b.deepCopy();another.put("requestKey",uuid());error("PLAN_VERSION_CONFLICT",()->training.feedback(user,id(p),another));
 }
 List<JsonNode> parallel(Callable<JsonNode> a,Callable<JsonNode> b){
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
  try {Future<JsonNode> first=pool.submit(()->{gate.await();return a.call();});Future<JsonNode> second=pool.submit(()->{gate.await();return b.call();});gate.countDown();return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));}
  catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}
 }
 @Test void crossUserAndRollbackDoNotLeakOrLeaveFacts(){
  JsonNode p=create();error("NOT_FOUND",()->training.plan(other,id(p)));error("NOT_FOUND",()->training.feedback(other,id(p),feedback(p,"COMPLETED",5)));
  TransactionTemplate tx=new TransactionTemplate(manager);
  assertThatThrownBy(()->tx.execute(status->{training.feedback(user,id(p),feedback(p,"COMPLETED",5));throw new IllegalStateException("forced after reward");})).isInstanceOf(IllegalStateException.class);
  assertThat(training.statistics(user,id(p)).path("practiceCount").asInt()).isZero();assertThat(users.findById(user.id()).orElseThrow().getExp()).isZero();
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion",Long.class)).isZero();
 }
 @Test void splitPreservesRootAndOnlyRemainingMinutesThenRejectIsNoScheduleChange(){
  JsonNode p=create();JsonNode r=training.feedback(user,id(p),feedback(p,"PARTIAL",4,"TIME_SHORTAGE"));JsonNode s=r.path("suggestions").get(0);
  assertThat(s.path("type").asText()).isEqualTo("SPLIT");int remaining=0;
  for(JsonNode segment:s.path("after").path("tasks").get(0).path("segments"))if(segment.path("status").asText().equals("PENDING"))remaining+=segment.path("minutes").asInt();
  assertThat(remaining).isEqualTo(6);
  JsonNode accepted=training.decision(user,id(p),s.path("id").asText(),decision(r.path("plan"),s,"ACCEPT"));
  assertThat(accepted.path("plan").path("stats").path("totalTasks").asInt()).isEqualTo(4);
  assertThat(accepted.path("plan").path("tasks").get(0).path("id")).isEqualTo(p.path("tasks").get(0).path("id"));
  JsonNode noOp=training.decision(user,id(p),s.path("id").asText(),decision(accepted.path("plan"),s,"ACCEPT"));assertThat(noOp.path("plan").path("version").asLong()).isEqualTo(accepted.path("plan").path("version").asLong());
 }
 @Test void advanceOnlyUnlocksAfterConfirmationAndStaleSuggestionsCannotApply(){
  JsonNode p=create();JsonNode blocked=p.path("tasks").get(1);error("VALIDATION_ERROR",()->training.feedback(user,id(p),feedbackTask(p,blocked,"COMPLETED",4)));
  JsonNode r=training.feedback(user,id(p),feedback(p,"COMPLETED",5));JsonNode s=r.path("suggestions").get(0);assertThat(s.path("type").asText()).isEqualTo("ADVANCE");assertThat(r.path("plan").path("tasks").get(1).path("unlocked").asBoolean()).isFalse();
  JsonNode accepted=training.decision(user,id(p),s.path("id").asText(),decision(r.path("plan"),s,"ACCEPT"));assertThat(accepted.path("plan").path("tasks").get(1).path("unlocked").asBoolean()).isTrue();
  JsonNode again=training.feedback(user,id(p),feedback(accepted.path("plan"),"PARTIAL",2));JsonNode latest=again.path("suggestions").get(0);
  JsonNode changed=training.feedback(user,id(p),feedback(again.path("plan"),"PARTIAL",1));
  error("SUGGESTION_EXPIRED",()->training.decision(user,id(p),latest.path("id").asText(),decision(changed.path("plan"),latest,"ACCEPT")));
 }
 @Test void discomfortWinsThenResumeRequiresAcknowledgementAndFreshReview(){
  JsonNode p=create();JsonNode r=training.feedback(user,id(p),feedback(p,"COMPLETED",5,"DISCOMFORT","TIME_SHORTAGE"));JsonNode s=r.path("suggestions").get(0);
  assertThat(s.path("type").asText()).isEqualTo("PAUSE");assertThat(r.path("safetyAlerts")).isNotEmpty();
  JsonNode paused=training.decision(user,id(p),s.path("id").asText(),decision(r.path("plan"),s,"ACCEPT")).path("plan");
  ObjectNode b=obj("requestKey",uuid(),"expectedVersion",paused.path("version"),"action","RESUME","selfLevel","BEGINNER","safetyAcknowledged",false);
  error("VALIDATION_ERROR",()->training.state(user,id(p),b));b.put("safetyAcknowledged",true);JsonNode resumed=training.state(user,id(p),b);
  assertThat(resumed.path("blockedReasons").toString()).contains("SAFETY_REASSESSMENT_REQUIRED");assertThat(resumed.path("safetyAlerts")).isNotEmpty();
 }
 @Test void datesMinutesQuestionSnapshotAndRestDaysAreEnforced(){
  ObjectNode b=createBody();b.set("trainingDays",JSON.valueToTree(List.of(4)));JsonNode p=training.create(user,b);
  assertThat(training.todayView(user,id(p),LocalDate.parse("2026-10-23")).path("restDay").asBoolean()).isTrue();
  ObjectNode notStarted=feedback(p,"NOT_STARTED",1);error("VALIDATION_ERROR",()->training.feedback(user,id(p),notStarted));
  ObjectNode f=feedback(p,"PARTIAL",5);f.put("practiceDate","2026-10-23");ObjectNode future=f;error("VALIDATION_ERROR",()->training.feedback(user,id(p),future));
  ObjectNode wrong=feedback(p,"PARTIAL",5);((ObjectNode)wrong.path("selfChecks").get(0)).put("questionId","foreign");error("VALIDATION_ERROR",()->training.feedback(user,id(p),wrong));
 }
 @Test void adminVersionSnapshotsDisableAndAiFailureCannotEraseFeedback(){
  JsonNode p=create();JsonNode r=training.feedback(user,id(p),feedback(p,"PARTIAL",3));
  ObjectNode question=obj("recordId",r.path("record").path("id"),"question","重心不稳怎么办？");JsonNode context=training.aiContext(user,id(p),question);verifyNoInteractions(gateway);
  ObjectNode explain=question.deepCopy();explain.put("confirmed",true);explain.set("contextHash",context.path("contextHash"));when(gateway.chat(any())).thenThrow(new IllegalStateException("LOCAL PROVIDER DOUBLE FAILURE"));
  error("AI_UNAVAILABLE",()->ai.explain(user,id(p),explain));assertThat(training.statistics(user,id(p)).path("practiceCount").asInt()).isEqualTo(1);
  ObjectNode doc=content.template("basic-foundations",1L,true).deepCopy();doc.put("title","新版草稿");
  ObjectNode edit=obj("requestKey",uuid(),"expectedVersion",1,"document",doc);
  error("FORBIDDEN",()->content.mutate(user,"basic-foundations","edit",edit));content.mutate(admin,"basic-foundations","edit",edit);
  assertThat(training.plan(user,id(p)).path("template").path("title").asText()).doesNotContain("新版");
  ObjectNode publication=obj("requestKey",uuid(),"expectedVersion",2,"status","DISABLED","reason","安全纠正","safetyCorrection",true);content.mutate(admin,"basic-foundations","publication",publication);
  assertThat(training.plan(user,id(p)).path("contentAvailable").asBoolean()).isFalse();
  error("PLAN_VERSION_CONFLICT",()->ai.explain(user,id(p),explain));
  assertThat(training.record(user,id(p),r.path("record").path("id").asText()).path("record").path("taskSnapshot").path("sources")).isNotEmpty();
 }
 @Test void incompleteSourceAndCyclicDependenciesCannotPublish(){
  ObjectNode doc=content.template("basic-foundations",1L,true).deepCopy();((ObjectNode)doc.path("tasks").get(0)).set("sources",arr());error("VALIDATION_ERROR",()->content.validate(doc));
  ObjectNode cycle=content.template("basic-foundations",1L,true).deepCopy();((ObjectNode)cycle.path("tasks").get(0)).set("prerequisites",JSON.valueToTree(List.of("turn")));error("VALIDATION_ERROR",()->content.validate(cycle));
 }
 @Test void configuredCoachSceneIsResolvedAndRateLimitIsAnActualError(){
  JsonNode p=create();JsonNode r=training.feedback(user,id(p),feedback(p,"PARTIAL",2));
  ObjectNode b=obj("recordId",r.path("record").path("id"),"question","站姿有什么自查要点？");JsonNode c=training.aiContext(user,id(p),b);b.set("contextHash",c.path("contextHash"));b.put("confirmed",true);
  when(gateway.chat(any())).thenReturn(com.skatehub.pojo.ai.AiResponse.builder().content("本地替身解释，非真实AI").build());ai.explain(user,id(p),b);
  var capture=org.mockito.ArgumentCaptor.forClass(com.skatehub.pojo.ai.AiRequest.class);verify(gateway).chat(capture.capture());
  assertThat(capture.getValue().getScene()).isEqualTo("coach_chat");
  var props=new com.skatehub.config.AiProperties();props.setModel("DEFAULT-MUST-NOT-BE-USED");
  var endpoint=new com.skatehub.config.AiProperties.EndpointConfig();endpoint.setModel("LOCAL-COACH-MODEL");props.getScenes().put("coach_chat",endpoint);
  assertThat(props.resolveRequestConfig(capture.getValue().getScene(),capture.getValue().getProvider(),capture.getValue().getModel()).getModel()).isEqualTo("LOCAL-COACH-MODEL");
  reset(gateway);doThrow(new com.skatehub.util.BizException("limit")).when(gateway).checkRateLimit(user);
  assertThatThrownBy(()->ai.explain(user,id(p),b)).isInstanceOfSatisfying(GuidanceException.class,e->assertThat(e.status).isEqualTo(429));verify(gateway,never()).chat(any());
 }
 @Test void concurrentCreateClaimsOneSlotAndLegacyJoinCannotBypassIt(){
  ObjectNode b=createBody();var results=parallel(()->training.create(user,b),()->training.create(user,b));assertThat(id(results.get(0))).isEqualTo(id(results.get(1)));
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_active where user_id=?",Long.class,user.id())).isEqualTo(1);
  error("PLAN_CONFLICT",()->training.create(user,createBody()));
  error("PLAN_CONFLICT",()->legacy.join(user,1L));
  assertThat(legacy.current(user)).containsEntry("guidanceApi","/api/user/training-guidance/current");
 }
 @Test void legacyFinalRetryRejoinRewardAndOutOfOrderProgressAreCorrect(){
  var plans=contextPlan(2);long plan=plans[0],first=plans[1],last=plans[2];
  var joined=legacy.join(user,plan);long up=((com.skatehub.pojo.skate.UserTrainingPlan)joined.get("userPlan")).getUserPlanId();
  error("PLAN_CONFLICT",()->training.create(user,createBody()));
  var req=new com.skatehub.pojo.skate.TrainingTaskCompleteRequest();req.setPracticeMinutes(2);
  var late=legacy.completeTask(user,last,req);assertThat(late.get("completedPlan")).isEqualTo(false);
  assertThat(((com.skatehub.pojo.skate.UserTrainingPlan)late.get("userPlan")).getCompletedDays()).isZero();
  var done=legacy.completeTask(user,first,req);assertThat(done.get("completedPlan")).isEqualTo(true);
  var retry=legacy.completeTask(user,first,req);assertThat(retry.get("taskExpAwarded")).isEqualTo(0);assertThat(retry.get("planExpAwarded")).isEqualTo(0);
  assertThat(jdbc.queryForObject("select count(*) from tb_user_training_record where user_plan_id=?",Long.class,up)).isEqualTo(2);
  legacy.join(user,plan);var repeat=legacy.completeTask(user,first,req);assertThat(repeat.get("taskExpAwarded")).isEqualTo(0);
 }
 long[] contextPlan(int days){
  var tx=new TransactionTemplate(manager);
  return tx.execute(status->{
   var p=new com.skatehub.pojo.skate.TrainingPlan();p.setPlanName("synthetic legacy");p.setDurationDays(days);p.setStatus("1");
   var em=org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(jdbcEntityManagerFactory());em.persist(p);
   var a=new com.skatehub.pojo.skate.TrainingTask();a.setPlanId(p.getPlanId());a.setDayNo(1);a.setTaskTitle("first");em.persist(a);
   var b=new com.skatehub.pojo.skate.TrainingTask();b.setPlanId(p.getPlanId());b.setDayNo(days);b.setTaskTitle("last");em.persist(b);em.flush();return new long[]{p.getPlanId(),a.getTaskId(),b.getTaskId()};
  });
 }
 @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
 jakarta.persistence.EntityManagerFactory jdbcEntityManagerFactory(){return entityManagerFactory;}
 @Test void rejectionIsScheduleNoOpAndGetDoesNotPersistExpiry(){
  JsonNode p=create();JsonNode r=training.feedback(user,id(p),feedback(p,"PARTIAL",2));JsonNode suggestion=r.path("suggestions").get(0);JsonNode tasks=r.path("plan").path("tasks");
  JsonNode rejected=training.decision(user,id(p),suggestion.path("id").asText(),decision(r.path("plan"),suggestion,"REJECT"));assertThat(rejected.path("plan").path("tasks").toString()).isEqualTo(tasks.toString());
  JsonNode again=training.feedback(user,id(p),feedback(rejected.path("plan"),"PARTIAL",2));
  training.feedback(user,id(p),feedback(again.path("plan"),"PARTIAL",1));String before=jdbc.queryForObject("select document from tb_guidance_plan where user_plan_id=?",String.class,id(p));
  JsonNode list=training.suggestions(user,id(p),1,20);assertThat(list.toString()).contains("EXPIRED");assertThat(jdbc.queryForObject("select document from tb_guidance_plan where user_plan_id=?",String.class,id(p))).isEqualTo(before);
 }
 @Test void fullPlanNeedsThreeExplicitStageConfirmationsAndAwardsFourFacts(){
  JsonNode p=create();
  for(int index=0;index<4;index++) {
   JsonNode task=p.path("tasks").get(index);clock.day(task.path("date").asText());JsonNode r=training.feedback(user,id(p),feedbackTask(p,task,"COMPLETED",5));p=r.path("plan");
   if(index<3){JsonNode suggestion=r.path("suggestions").get(0);assertThat(suggestion.path("type").asText()).isEqualTo("ADVANCE");p=training.decision(user,id(p),suggestion.path("id").asText(),decision(p,suggestion,"ACCEPT")).path("plan");}
  }
  assertThat(p.path("status").asText()).isEqualTo("COMPLETED");assertThat(users.findById(user.id()).orElseThrow().getExp()).isEqualTo(62);
  assertThat(p.path("stats").path("completedTasks").asInt()).isEqualTo(4);assertThat(training.current(user)).isNull();
 }

 @Test void nonexistentTamperedAndDisabledSourcesCannotPublishOrRecommend(){
  ObjectNode fake=content.template("basic-foundations",1L,true).deepCopy();((ObjectNode)fake.path("sources").get(0)).put("locator","docs/not-exist.md:1");error("VALIDATION_ERROR",()->content.validate(fake));
  ObjectNode changed=content.template("basic-foundations",1L,true).deepCopy();((ObjectNode)changed.path("tasks").get(0).path("sources").get(0)).put("excerpt","未经核验的指令");error("VALIDATION_ERROR",()->content.validate(changed));
  JsonNode p=create();jdbc.update("update tb_guidance_source set status='DISABLED' where source_id='station-stance'");
  assertThat(content.templates(false)).isEmpty();assertThat(training.plan(user,id(p)).path("contentAvailable").asBoolean()).isFalse();
  error("CONTENT_UNAVAILABLE",()->training.feedback(user,id(p),feedback(p,"PARTIAL",2)));
 }

 @Test void uncertainNotStartedLongBreakAndShanghaiBoundaryRemainConservative(){
  clock.instant=Instant.parse("2026-10-21T16:01:00Z");assertThat(training.today().toString()).isEqualTo("2026-10-22");
  JsonNode p=create();ObjectNode notStarted=feedback(p,"NOT_STARTED",0);JsonNode r=training.feedback(user,id(p),notStarted);assertThat(r.path("record").path("expAwarded").asInt()).isZero();
  ObjectNode uncertain=feedback(r.path("plan"),"PARTIAL",1);((ObjectNode)uncertain.path("selfChecks").get(0)).put("answer","NOT_APPLICABLE");JsonNode u=training.feedback(user,id(p),uncertain);
  assertThat(u.path("record").path("selfAssessment").asText()).isEqualTo("UNCERTAIN");assertThat(u.path("suggestions").get(0).path("type").asText()).isEqualTo("CONSOLIDATE");
  clock.day("2026-11-07");JsonNode refreshed=training.plan(user,id(p));JsonNode longBreak=training.feedback(user,id(p),feedback(refreshed,"PARTIAL",1));assertThat(longBreak.path("suggestions").get(0).path("ruleId").asText()).isEqualTo("LONG_BREAK");
 }
 @Test void partialSegmentCanCompleteOnlyOneRootAndRejectFutureSegment(){
  ObjectNode b=createBody();b.put("sessionMinutes",5);JsonNode p=training.create(user,b);JsonNode t=p.path("tasks").get(0);
  ObjectNode first=feedback(p,"COMPLETED",5);first.set("segmentId",t.path("segments").get(0).path("id"));JsonNode one=training.feedback(user,id(p),first);
  assertThat(one.path("record").path("expAwarded").asInt()).isZero();assertThat(one.path("plan").path("stats").path("completedTasks").asInt()).isZero();
  ObjectNode second=feedback(one.path("plan"),"COMPLETED",5);second.set("segmentId",t.path("segments").get(1).path("id"));error("VALIDATION_ERROR",()->training.feedback(user,id(p),second));
  clock.day(t.path("segments").get(1).path("date").asText());second.put("practiceDate",training.today().toString());JsonNode two=training.feedback(user,id(p),second);
  assertThat(two.path("record").path("expAwarded").asInt()).isEqualTo(8);assertThat(two.path("plan").path("stats").path("completedTasks").asInt()).isEqualTo(1);
 }
 @Test void captureExecutableFrontendExamplesFromRealServiceCalls() throws Exception {
  String destination=System.getProperty("guidance.evidenceDir"); if(destination==null)return;
  ArrayNode examples=arr();
  java.util.function.BiFunction<String,JsonNode,JsonNode> capture=(route,result)->{examples.add(obj("route",route,"response",obj("code",200,"message","success","data",result)));return result;};
  capture.apply("GET /api/user/training-guidance/templates",JSON.valueToTree(content.templates(false)));
  capture.apply("GET /api/user/training-guidance/templates/basic-foundations?version=1",content.template("basic-foundations",1L,false));
  ObjectNode body=createBody();JsonNode preview=capture.apply("POST /api/user/training-guidance/plans/preview",training.preview(body));examples.get(examples.size()-1).deepCopy();((ObjectNode)examples.get(examples.size()-1)).set("request",body.deepCopy());
  JsonNode p=capture.apply("POST /api/user/training-guidance/plans",training.create(user,body));((ObjectNode)examples.get(examples.size()-1)).set("request",body.deepCopy());
  String base="/api/user/training-guidance/plans/"+id(p);
  capture.apply("GET /api/user/training-guidance/current",training.current(user));capture.apply("GET "+base,training.plan(user,id(p)));
  capture.apply("GET "+base+"/today?date=2026-10-22",training.todayView(user,id(p),training.today()));capture.apply("GET "+base+"/tasks/"+p.path("tasks").get(0).path("id").asText(),training.task(user,id(p),p.path("tasks").get(0).path("id").asText()));
  ObjectNode f=feedback(p,"PARTIAL",4,"TIME_SHORTAGE","MOVEMENT_DIFFICULTY");JsonNode r=capture.apply("POST "+base+"/feedback",training.feedback(user,id(p),f));((ObjectNode)examples.get(examples.size()-1)).set("request",f);
  capture.apply("GET "+base+"/statistics",training.statistics(user,id(p)));capture.apply("GET "+base+"/records?page=1&size=20",training.records(user,id(p),1,20));
  String rid=r.path("record").path("id").asText();capture.apply("GET "+base+"/records/"+rid,training.record(user,id(p),rid));capture.apply("GET "+base+"/suggestions?page=1&size=20",training.suggestions(user,id(p),1,20));
  JsonNode suggestion=r.path("suggestions").get(0);ObjectNode accept=decision(r.path("plan"),suggestion,"ACCEPT");JsonNode adjusted=capture.apply("POST "+base+"/suggestions/"+suggestion.path("id").asText()+"/decision",training.decision(user,id(p),suggestion.path("id").asText(),accept));((ObjectNode)examples.get(examples.size()-1)).set("request",accept);
  capture.apply("GET "+base+"/adjustments?page=1&size=20",training.adjustments(user,id(p),1,20));capture.apply("GET /api/user/training-guidance/plans?page=1&size=20",training.history(user,1,20));
  ObjectNode question=obj("recordId",rid,"question","站姿重心不稳如何自查？");JsonNode context=capture.apply("POST "+base+"/ai-context",training.aiContext(user,id(p),question));((ObjectNode)examples.get(examples.size()-1)).set("request",question.deepCopy());
  question.put("confirmed",true);question.set("contextHash",context.path("contextHash"));when(gateway.chat(any())).thenThrow(new IllegalStateException("LOCAL TEST DOUBLE"));
  try{ai.explain(user,id(p),question);throw new AssertionError("expected local AI failure");}catch(GuidanceException e){examples.add(obj("route","POST "+base+"/ai-explain","request",question,"httpStatus",e.status,"response",obj("code",e.status,"message",e.getMessage(),"data",obj("errorCode",e.errorCode))));}
  JsonNode paused=training.state(user,id(p),obj("requestKey",uuid(),"expectedVersion",adjusted.path("plan").path("version"),"action","PAUSE"));
  ObjectNode resume=obj("requestKey",uuid(),"expectedVersion",paused.path("version"),"action","RESUME","selfLevel","BEGINNER","safetyAcknowledged",true);capture.apply("POST "+base+"/state",training.state(user,id(p),resume));((ObjectNode)examples.get(examples.size()-1)).set("request",resume);
  JsonNode resumed=training.plan(user,id(p));clock.day(resumed.path("tasks").get(0).path("date").asText());
  ObjectNode discomfort=feedback(resumed,"PARTIAL",1,"DISCOMFORT","TIME_SHORTAGE");JsonNode safety=capture.apply("POST "+base+"/feedback",training.feedback(user,id(p),discomfort));((ObjectNode)examples.get(examples.size()-1)).set("request",discomfort);((ObjectNode)examples.get(examples.size()-1)).put("scenario","discomfort-has-priority");
  JsonNode pauseSuggestion=safety.path("suggestions").get(0);ObjectNode reject=decision(safety.path("plan"),pauseSuggestion,"REJECT");capture.apply("POST "+base+"/suggestions/"+pauseSuggestion.path("id").asText()+"/decision",training.decision(user,id(p),pauseSuggestion.path("id").asText(),reject));((ObjectNode)examples.get(examples.size()-1)).set("request",reject);((ObjectNode)examples.get(examples.size()-1)).put("scenario","reject-no-schedule-change-safety-retained");
  capture.apply("GET /api/admin/training-guidance/templates?page=1&size=20",page(content.templates(true),1,20));capture.apply("GET /api/admin/training-guidance/templates/basic-foundations",content.template("basic-foundations",null,true));
  ObjectNode draftBody=obj("requestKey",uuid(),"document",content.template("basic-foundations",1L,true));JsonNode draft=capture.apply("POST /api/admin/training-guidance/templates",content.mutate(admin,null,"create",draftBody));((ObjectNode)examples.get(examples.size()-1)).set("request",draftBody);
  String templateId=draft.path("id").asText();ObjectNode edit=obj("requestKey",uuid(),"expectedVersion",1,"document",draftBody.path("document"));capture.apply("PUT /api/admin/training-guidance/templates/"+templateId,content.mutate(admin,templateId,"edit",edit));((ObjectNode)examples.get(examples.size()-1)).set("request",edit);
  ObjectNode publish=obj("requestKey",uuid(),"expectedVersion",2,"status","PUBLISHED","reason","本地已校验的文字来源版本");capture.apply("POST /api/admin/training-guidance/templates/"+templateId+"/publication",content.mutate(admin,templateId,"publication",publish));((ObjectNode)examples.get(examples.size()-1)).set("request",publish);
  java.nio.file.Path dir=java.nio.file.Path.of(destination);java.nio.file.Files.createDirectories(dir);
  java.nio.file.Files.writeString(dir.resolve("frontend-examples.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(obj("evidenceLayer","H2 real service/transaction outputs; not listening HTTP or browser acceptance","examples",examples)),java.nio.charset.StandardCharsets.UTF_8);
 }

 @Test void splittingAfterCompletedSegmentDoesNotSubtractItsMinutesTwice(){
  ObjectNode b=createBody();b.put("sessionMinutes",5);JsonNode p=training.create(user,b);JsonNode task=p.path("tasks").get(0);
  ObjectNode first=feedback(p,"COMPLETED",5);first.set("segmentId",task.path("segments").get(0).path("id"));JsonNode r=training.feedback(user,id(p),first);
  clock.day(task.path("segments").get(1).path("date").asText());ObjectNode partial=feedback(r.path("plan"),"PARTIAL",2,"TIME_SHORTAGE");partial.set("segmentId",task.path("segments").get(1).path("id"));JsonNode split=training.feedback(user,id(p),partial);
  int remaining=0;for(JsonNode segment:split.path("suggestions").get(0).path("after").path("tasks").get(0).path("segments"))if(segment.path("status").asText().equals("PENDING"))remaining+=segment.path("minutes").asInt();assertThat(remaining).isEqualTo(3);
 }

 @Test void resumePreservesSegmentDailyCapAndFuturePlansExposeNoFeedbackAction(){
  ObjectNode b=createBody();b.put("sessionMinutes",5);JsonNode p=training.create(user,b);
  JsonNode paused=training.state(user,id(p),obj("requestKey",uuid(),"expectedVersion",p.path("version"),"action","PAUSE"));
  JsonNode resumed=training.state(user,id(p),obj("requestKey",uuid(),"expectedVersion",paused.path("version"),"action","RESUME","selfLevel","BEGINNER","safetyAcknowledged",true));
  int pending=0;for(JsonNode segment:resumed.path("tasks").get(0).path("segments"))if(segment.path("status").asText().equals("PENDING")){assertThat(segment.path("minutes").asInt()).isLessThanOrEqualTo(5);pending++;}assertThat(pending).isEqualTo(2);
  training.state(user,id(p),obj("requestKey",uuid(),"expectedVersion",resumed.path("version"),"action","QUIT"));
  ObjectNode future=createBody();future.put("startDate",training.today().plusDays(2).toString());JsonNode scheduled=training.create(user,future);
  assertThat(scheduled.path("allowedActions").toString()).doesNotContain("FEEDBACK");assertThat(scheduled.path("today").path("notStarted").asBoolean()).isTrue();
 }

}
