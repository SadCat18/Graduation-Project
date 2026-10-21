package com.skatehub.service.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.SkateHubApplication;
import com.skatehub.config.AiProperties;
import com.skatehub.dao.*;
import com.skatehub.pojo.*;
import com.skatehub.pojo.ai.*;
import com.skatehub.provider.AiProvider;
import com.skatehub.service.ai.*;
import com.skatehub.service.TrainingPlanService;
import com.skatehub.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.skatehub.service.training.GuidanceJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated loopback MySQL + real HTTP/security/JWT. Provider/Redis are explicit local doubles. */
@SpringBootTest(classes=SkateHubApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.config.location=classpath:t07-http.properties","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
 "spring.datasource.username=root","spring.datasource.password=","spring.flyway.baseline-on-migrate=false",
 "spring.jpa.hibernate.ddl-auto=none","spring.main.allow-bean-definition-overriding=true",
 "ai.provider=local-review","ai.model=local-default","ai.scenes.coach_chat.provider=local-review","ai.scenes.coach_chat.model=local-coach",
 "ai.base-url=http://127.0.0.1:1","ai.api-key=LOCAL-NONSECRET-NO-NETWORK"})
@Import(GuidanceMysqlHttpReviewIT.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GuidanceMysqlHttpReviewIT {
 static final Path EV=Path.of(System.getProperty("review.evidence","target/test-evidence/training-review"));
 static final String API="/api/user/training-guidance",ADMIN="/api/admin/training-guidance";
 static final List<Map<String,Object>> MIGRATION=new ArrayList<>();
 static final ReviewClock CLOCK=new ReviewClock();
 static class ReviewClock extends Clock {volatile Instant instant=Instant.parse("2026-10-22T04:00:00Z");public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}public Clock withZone(ZoneId z){return Clock.fixed(instant,z);}public Instant instant(){return instant;}void day(String d){instant=LocalDate.parse(d).atTime(12,0).atZone(getZone()).toInstant();}}
 @TestConfiguration static class Config {
  @Bean @Primary Clock reviewClock(){return CLOCK;}
  @Bean FlywayMigrationStrategy reviewMigration(){return configured->{
   var ds=configured.getConfiguration().getDataSource();
   Flyway ten=Flyway.configure().dataSource(ds).target("10").cleanDisabled(true).load();
   int initial=ten.migrate().migrationsExecuted;
   JdbcTemplate j=new JdbcTemplate(ds);
   // Existing deployment/JPA columns are absent from historical Flyway SQL; explicit local V10 fixture only.
   j.execute("alter table tb_user add column exp int default 0");j.execute("alter table tb_user add column token_version int default 0");
   j.update("insert into tb_user(user_id,username,password,status,exp) values(810001,'legacy_review','LOCAL_ONLY','0',8)");
   j.update("insert into tb_training_plan(plan_id,plan_name,duration_days,status) values(810001,'legacy preserved',1,'1')");
   j.update("insert into tb_training_task(task_id,plan_id,day_no,task_title,target_minutes) values(810001,810001,1,'legacy task preserved',9)");
   j.update("insert into tb_user_training_plan(user_plan_id,user_id,plan_id,start_date,current_day,completed_days,status) values(810001,810001,810001,'2026-10-20',1,1,'COMPLETED')");
   j.update("insert into tb_user_training_record(record_id,user_id,user_plan_id,task_id,practice_minutes,exp_awarded) values(810001,810001,810001,810001,9,8)");
   var before=j.queryForMap("select * from tb_user_training_record where record_id=810001");
   var pBefore=j.queryForMap("select * from tb_user_training_plan where user_plan_id=810001");
   int upgrade=configured.migrate().migrationsExecuted;
   assertThat(j.queryForMap("select * from tb_user_training_record where record_id=810001")).isEqualTo(before);
   var after=j.queryForMap("select * from tb_user_training_plan where user_plan_id=810001");assertThat(after.remove("mode")).isEqualTo("LEGACY");assertThat(after).isEqualTo(pBefore);
   int repeat=configured.migrate().migrationsExecuted;assertThat(repeat).isZero();
   MIGRATION.add(Map.of("baselineMigrations",initial,"upgradeMigrations",upgrade,"repeatMigrations",repeat,"oldRecordPreserved",true,"oldPlanPreserved",true));
  };}
 }
 @Autowired TestRestTemplate http; @Autowired UserRepository users; @Autowired JdbcTemplate jdbc;
 @Autowired JwtTokenService jwt; @Autowired TrainingGuidanceService training; @Autowired PlatformTransactionManager txManager;
 @Autowired PasswordEncoder encoder; @Autowired AiGatewayService gateway;
 @Autowired TrainingPlanService legacy;
 @MockBean AiProviderRegistry providers; @MockBean StringRedisTemplate redis;
 final List<ObjectNode> observations=new ArrayList<>();
 volatile boolean failProvider=false; volatile AiRequest captured; volatile String capturedModel; int providerCalls;
 record Actor(long id,String token){}
 @BeforeEach void doubles()throws Exception {
  CLOCK.day("2026-10-22");failProvider=false;
  Files.createDirectories(EV);
  @SuppressWarnings("unchecked") ValueOperations<String,String> ops=mock(ValueOperations.class);
  when(redis.opsForValue()).thenReturn(ops);when(ops.get(anyString())).thenReturn("T08X");when(redis.delete(anyString())).thenReturn(true);
  AiProvider local=new AiProvider(){ public String getProviderName(){return "local-review";} public AiResponse chat(AiRequest r,AiProperties.EndpointConfig c){captured=r;capturedModel=c.getModel();providerCalls++;if(failProvider||r.getUserPrompt().contains("LOCAL_PROVIDER_FAIL"))throw new IllegalStateException("EXPLICIT LOCAL PROVIDER FAILURE");return AiResponse.builder().content("本地替身：请按已有来源复习，不能认证动作或代替安全判断。").build();}};
  when(providers.getProvider(anyString())).thenAnswer(a->{assertThat(a.<String>getArgument(0)).isEqualTo("local-review");return local;});
 }
 Actor actor(){User u=new User();u.setUsername("r"+uuid().replace("-","").substring(0,12));u.setPassword(encoder.encode("LocalReview2026!"));u=users.saveAndFlush(u);return new Actor(u.getUserId(),jwt.createToken(u.getUserId(),"USER",u.getUsername()));}
 JsonNode request(Actor a,HttpMethod method,String path,JsonNode body,int status){
  HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(a!=null)h.setBearerAuth(a.token());
  var r=http.exchange(path,method,new HttpEntity<>(body,h),JsonNode.class);assertThat(r.getStatusCode().value()).as(path+" "+r.getBody()).isEqualTo(status);
  assertThat(r.getBody()).isNotNull();assertThat(r.getBody().path("code").asInt()).as(path).isEqualTo(status);
  observations.add(obj("method",method.name(),"path",path,"status",status,"body",r.getBody()));return r.getBody().path("data");
 }
 JsonNode get(Actor a,String p){return request(a,HttpMethod.GET,p,null,200);} JsonNode post(Actor a,String p,JsonNode b){return request(a,HttpMethod.POST,p,b,200);}
 ObjectNode createBody(){return obj("requestKey",uuid(),"templateId","basic-foundations","templateVersion",1,"goal","本地独立验收","selfLevel","BEGINNER","trainingDays",List.of(1,2,3,4,5,6,7),"sessionMinutes",20,"startDate",training.today().toString());}
 JsonNode create(Actor a){return post(a,API+"/plans",createBody());}
 String url(JsonNode p){return API+"/plans/"+p.path("id").asLong();}
 ObjectNode feedback(JsonNode p,int task,String completion,int minutes,String... reasons){JsonNode t=p.path("tasks").get(task);ArrayNode answers=arr();t.path("selfChecks").forEach(q->answers.add(obj("questionId",q.path("id"),"answer","YES")));return obj("requestKey",uuid(),"expectedVersion",p.path("version"),"instanceId",t.path("id"),"practiceDate",training.today().toString(),"completion",completion,"actualMinutes",minutes,"difficulty",completion.equals("NOT_STARTED")?null:"MODERATE","selfChecks",answers,"reasons",List.of(reasons),"note","本地独立验收，不是真实用户记录");}
 JsonNode decision(Actor a,JsonNode p,JsonNode s,String action){return post(a,url(p)+"/suggestions/"+s.path("id").asText()+"/decision",obj("requestKey",uuid(),"expectedVersion",p.path("version"),"decision",action));}
 @Test @Order(1) void fullMysqlMigrationPreservesLegacyAndSecurityUsesActualJwt(){
  assertThat(MIGRATION.get(0).get("baselineMigrations")).isEqualTo(10);assertThat(MIGRATION.get(0).get("upgradeMigrations")).isEqualTo(3);
  assertThat(jdbc.queryForObject("select version from flyway_schema_history where success=1 order by installed_rank desc limit 1",String.class)).isEqualTo("13");
  request(null,HttpMethod.GET,API+"/current",null,401);Actor a=actor();get(a,API+"/templates");request(a,HttpMethod.GET,ADMIN+"/templates",null,403);
  Actor b=actor();JsonNode p=create(a);request(b,HttpMethod.GET,url(p),null,404);
  assertThatThrownBy(()->jdbc.update("insert into tb_user_training_plan(user_id,plan_id,mode,status) values(?,null,'LEGACY','QUIT')",a.id())).isInstanceOf(Exception.class);
 }
 @Test @Order(2) void realHttpWorkflowSplitRejectSafetyAiAndHistory(){
  Actor a=actor();ObjectNode cb=createBody();get(a,API+"/templates/basic-foundations?version=1");JsonNode preview=post(a,API+"/plans/preview",cb);assertThat(preview.path("timezone").asText()).isEqualTo("Asia/Shanghai");
  JsonNode p=post(a,API+"/plans",cb);String path=url(p);JsonNode r=post(a,path+"/feedback",feedback(p,0,"PARTIAL",4,"TIME_SHORTAGE","MOVEMENT_DIFFICULTY"));
  assertThat(r.path("record").path("expAwarded").asInt()).isZero();assertThat(r.path("plan").path("stats").path("practiceMinutes").asInt()).isEqualTo(4);assertThat(r.path("suggestions").get(0).path("type").asText()).isEqualTo("SPLIT");
  JsonNode s=r.path("suggestions").get(0);p=decision(a,r.path("plan"),s,"ACCEPT").path("plan");long v=p.path("version").asLong();assertThat(decision(a,p,s,"ACCEPT").path("plan").path("version").asLong()).isEqualTo(v);
  assertThat(p.path("stats").path("totalTasks").asInt()).isEqualTo(4);assertThat(get(a,path+"/records").path("total").asInt()).isEqualTo(1);assertThat(get(a,path+"/adjustments").path("total").asInt()).isEqualTo(1);
  ObjectNode q=obj("recordId",r.path("record").path("id"),"question","动作困难时如何核对已有步骤？");int calls=providerCalls;JsonNode ctx=post(a,path+"/ai-context",q);assertThat(providerCalls).isEqualTo(calls);
  ObjectNode explain=q.deepCopy();explain.put("confirmed",true);explain.set("contextHash",ctx.path("contextHash"));post(a,path+"/ai-explain",explain);assertThat(captured.getScene()).isEqualTo("coach_chat");assertThat(capturedModel).isEqualTo("local-coach");
  failProvider=true;request(a,HttpMethod.POST,path+"/ai-explain",explain,503);failProvider=false;assertThat(get(a,path+"/statistics").path("practiceCount").asInt()).isEqualTo(1);
  a=actor();p=create(a);path=url(p);r=post(a,path+"/feedback",feedback(p,0,"COMPLETED",5,"DISCOMFORT","TIME_SHORTAGE"));s=r.path("suggestions").get(0);assertThat(s.path("type").asText()).isEqualTo("PAUSE");assertThat(r.path("safetyAlerts")).isNotEmpty();
  p=decision(a,r.path("plan"),s,"REJECT").path("plan");assertThat(p.path("status").asText()).isEqualTo("IN_PROGRESS");
  p=post(a,path+"/state",obj("requestKey",uuid(),"expectedVersion",p.path("version"),"action","PAUSE"));
  ObjectNode resume=obj("requestKey",uuid(),"expectedVersion",p.path("version"),"action","RESUME","selfLevel","BEGINNER","safetyAcknowledged",false);request(a,HttpMethod.POST,path+"/state",resume,400);resume.put("safetyAcknowledged",true);p=post(a,path+"/state",resume);assertThat(p.path("blockedReasons").toString()).contains("SAFETY_REASSESSMENT_REQUIRED");
  ObjectNode invalid=feedback(p,0,"PARTIAL",241);request(a,HttpMethod.POST,path+"/feedback",invalid,400);invalid.put("actualMinutes",1);invalid.put("practiceDate","2026-10-23");request(a,HttpMethod.POST,path+"/feedback",invalid,400);
 }
 List<JsonNode> parallel(Callable<JsonNode> a,Callable<JsonNode> b)throws Exception{var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);try{var x=pool.submit(()->{gate.await();return a.call();});var y=pool.submit(()->{gate.await();return b.call();});gate.countDown();return List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}}
 @Test @Order(3) void mysqlConcurrentRequestsCompletionAndRollback()throws Exception{
  Actor a=actor();JsonNode p=create(a);ObjectNode b=feedback(p,0,"COMPLETED",5);CurrentUser u=new CurrentUser(a.id(),"USER","local");long id=p.path("id").asLong();
  var same=parallel(()->training.feedback(u,id,b),()->training.feedback(u,id,b));assertThat(JSON.readTree(same.get(0).toString())).isEqualTo(JSON.readTree(same.get(1).toString()));
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select exp from tb_user where user_id=?",Integer.class,a.id())).isEqualTo(8);
  ObjectNode changed=b.deepCopy();changed.put("actualMinutes",6);request(a,HttpMethod.POST,url(p)+"/feedback",changed,409);
  JsonNode current=training.plan(u,id);ObjectNode other=feedback(current,0,"COMPLETED",2);training.feedback(u,id,other);assertThat(jdbc.queryForObject("select exp from tb_user where user_id=?",Integer.class,a.id())).isEqualTo(8);
  Actor fresh=actor();JsonNode f=create(fresh);CurrentUser fu=new CurrentUser(fresh.id(),"USER","local");
  assertThatThrownBy(()->new TransactionTemplate(txManager).execute(s->{training.feedback(fu,f.path("id").asLong(),feedback(f,0,"COMPLETED",4));throw new IllegalStateException("forced rollback");})).isInstanceOf(IllegalStateException.class);
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,f.path("id").asLong())).isZero();assertThat(users.findById(fresh.id()).orElseThrow().getExp()).isZero();
 }
 @Test @Order(4) void actualBrowserAgainstSameMysqlHttp()throws Exception{
  Assumptions.assumeTrue(Boolean.getBoolean("review.browser"),"Browser execution requires explicit review.browser=true");
  Actor a=actor(),reject=actor(),unknown=actor();jdbc.update("insert into tb_admin(admin_id,account,password,real_name,role) values(910001,'review_admin',?,'本地测试','1')",encoder.encode("LocalReview2026!"));
  assertThat(users.existsById(910001L)).isFalse();
  Path privateTokens=EV.resolve(".local-browser-identities.json");Files.writeString(privateTokens,obj("user",a.token(),"reject",reject.token(),"unknown",unknown.token(),"admin",jwt.createToken(910001L,"ADMIN","local admin")).toString());
  List<String> args=new ArrayList<>(List.of(System.getProperty("review.node","node"),"node_modules/@playwright/test/cli.js","test","--config","playwright.training-http.config.cjs"));if(System.getenv("T08_BROWSER_GREP")!=null){args.add("--grep");args.add(System.getenv("T08_BROWSER_GREP"));}
  ProcessBuilder b=new ProcessBuilder(args);b.directory(Path.of("frontend").toFile());b.redirectErrorStream(true);b.redirectOutput(EV.resolve("browser.log").toFile());
  b.environment().put("REF_BACKEND",http.getRootUri());b.environment().put("REF_DIST",Path.of("frontend/dist").toAbsolutePath().toString());b.environment().put("REVIEW_IDENTITIES",privateTokens.toAbsolutePath().toString());b.environment().put("REVIEW_EVIDENCE",EV.toAbsolutePath().toString());b.environment().put("PLAYWRIGHT_CHANNEL","msedge");
  try{var process=b.start();if(!process.waitFor(240,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("browser timeout");}assertThat(process.exitValue()).as("See browser.log").isZero();}finally{Files.deleteIfExists(privateTokens);}
 }
 @AfterAll void evidence()throws Exception{Files.createDirectories(EV);Files.writeString(EV.resolve("http-observations.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(observations));Files.writeString(EV.resolve("migration.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(MIGRATION));}

 @Test @Order(5) void mysqlActivitySlotCrossModeAndDifferentKeyVersionRaces()throws Exception{
  Actor a=actor();CurrentUser u=new CurrentUser(a.id(),"USER","local");ObjectNode body=createBody();
  var outcomes=parallel(()->{try{return training.create(u,body);}catch(GuidanceException e){return obj("failure",e.errorCode);}},()->{try{legacy.join(u,810001L);return obj("legacyJoined",true);}catch(RuntimeException e){return obj("failure",e.getClass().getSimpleName(),"message",e.getMessage());}});
  assertThat(outcomes.stream().filter(x->!x.has("failure")).count()).as("cross-mode outcomes: %s",outcomes).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from tb_user_training_plan where user_id=? and status in ('IN_PROGRESS','PAUSED')",Long.class,a.id())).isEqualTo(1);
  Actor b=actor();CurrentUser bu=new CurrentUser(b.id(),"USER","local");JsonNode p=create(b);long id=p.path("id").asLong();ObjectNode first=feedback(p,0,"COMPLETED",2),second=feedback(p,0,"COMPLETED",3);
  var requests=parallel(()->{try{return training.feedback(bu,id,first);}catch(GuidanceException e){return obj("failure",e.errorCode);}},()->{try{return training.feedback(bu,id,second);}catch(GuidanceException e){return obj("failure",e.errorCode);}});
  assertThat(requests.stream().filter(x->!x.has("failure")).count()).isEqualTo(1);assertThat(requests.stream().filter(x->x.path("failure").asText().equals("PLAN_VERSION_CONFLICT")).count()).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,id)).isEqualTo(1);assertThat(users.findById(b.id()).orElseThrow().getExp()).isEqualTo(8);
 }
 @Test @Order(6) void mysqlFourStagesExplicitAdvanceUniquePlanRewardAndDateBoundaries(){
  Actor a=actor();JsonNode p=create(a);String path=url(p);
  request(a,HttpMethod.POST,path+"/feedback",feedback(p,1,"COMPLETED",5),400);
  JsonNode r=post(a,path+"/feedback",feedback(p,0,"NOT_STARTED",0));assertThat(r.path("record").path("expAwarded").asInt()).isZero();p=r.path("plan");
  for(int i=0;i<4;i++){
   CLOCK.day(p.path("tasks").get(i).path("date").asText());r=post(a,path+"/feedback",feedback(p,i,"COMPLETED",i==0?240:5));p=r.path("plan");assertThat(r.path("record").path("expAwarded").asInt()).isEqualTo(8);
   if(i<3){assertThat(p.path("tasks").get(i+1).path("unlocked").asBoolean()).isFalse();p=decision(a,p,r.path("suggestions").get(0),"ACCEPT").path("plan");assertThat(p.path("tasks").get(i+1).path("unlocked").asBoolean()).isTrue();}
  }
  assertThat(p.path("status").asText()).isEqualTo("COMPLETED");assertThat(p.path("stats").path("completionRate").asDouble()).isEqualTo(1);assertThat(users.findById(a.id()).orElseThrow().getExp()).isEqualTo(62);
  assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,p.path("id").asLong())).isEqualTo(4);
  request(a,HttpMethod.POST,path+"/feedback",feedback(p,3,"COMPLETED",5),409);
  CLOCK.instant=Instant.parse("2026-10-22T16:00:00Z");assertThat(training.today().toString()).isEqualTo("2026-10-23");
  Actor rest=actor();ObjectNode b=createBody();b.set("trainingDays",JSON.valueToTree(List.of(5)));JsonNode rp=post(rest,API+"/plans",b);
  assertThat(get(rest,url(rp)+"/today?date=2026-10-24").path("restDay").asBoolean()).isTrue();
 }
 @Test @Order(7) void realGatewayRateLimitAndContextBindingPrivacy(){
  Actor a=actor(),other=actor();JsonNode p=create(a);String path=url(p);JsonNode r=post(a,path+"/feedback",feedback(p,0,"PARTIAL",2));
  ObjectNode q=obj("recordId",r.path("record").path("id"),"question","按原文如何核对姿势？");JsonNode c=post(a,path+"/ai-context",q);request(other,HttpMethod.POST,path+"/ai-context",q,404);
  ObjectNode explain=q.deepCopy();explain.put("confirmed",true);explain.put("contextHash","0".repeat(64));request(a,HttpMethod.POST,path+"/ai-explain",explain,409);explain.set("contextHash",c.path("contextHash"));
  int before=providerCalls;for(int i=0;i<30;i++)post(a,path+"/ai-explain",explain);request(a,HttpMethod.POST,path+"/ai-explain",explain,429);assertThat(providerCalls-before).isEqualTo(30);
  assertThat(captured.getUserPrompt()).doesNotContain(a.token(),other.token(),"password","phone","apiKey");assertThat(capturedModel).isEqualTo("local-coach");assertThat(get(a,path+"/statistics").path("practiceCount").asInt()).isEqualTo(1);
 }
 // Terminal fixture mutation: disabling the shared seed follows every scenario that adopts it.
 @Test @Order(11) void adminSeparateDomainInvalidSourcesVersionAndSafetyCorrection(){
  long adminId=920001L;jdbc.update("insert into tb_admin(admin_id,account,password,real_name,role) values(?,'review_admin2',?,'本地验收','1')",adminId,encoder.encode("LocalReview2026!"));assertThat(users.existsById(adminId)).isFalse();Actor admin=new Actor(adminId,jwt.createToken(adminId,"ADMIN","local"));
  Actor a=actor();JsonNode p=create(a);JsonNode r=post(a,url(p)+"/feedback",feedback(p,0,"PARTIAL",2,"TIME_SHORTAGE"));JsonNode s=r.path("suggestions").get(0);
  JsonNode original=get(admin,ADMIN+"/templates/basic-foundations?version=1");ObjectNode edit=obj("requestKey",uuid(),"expectedVersion",1,"document",original.deepCopy());JsonNode draft=request(admin,HttpMethod.PUT,ADMIN+"/templates/basic-foundations",edit,200);assertThat(draft.path("version").asInt()).isEqualTo(2);
  assertThat(get(a,url(p)).path("template").path("version").asInt()).isEqualTo(1);
  ObjectNode invalid=(ObjectNode)original.deepCopy();((ObjectNode)invalid.path("tasks").get(0).path("sources").get(0)).put("locator","docs/nonexistent.md:1-2");JsonNode bad=post(admin,ADMIN+"/templates",obj("requestKey",uuid(),"document",invalid));
  request(admin,HttpMethod.POST,ADMIN+"/templates/"+bad.path("id").asText()+"/publication",obj("requestKey",uuid(),"expectedVersion",1,"status","PUBLISHED","reason","must reject invented source"),400);
  post(admin,ADMIN+"/templates/basic-foundations/publication",obj("requestKey",uuid(),"expectedVersion",2,"status","DISABLED","reason","isolated safety correction","safetyCorrection",true));
  assertThat(get(a,url(p)).path("contentAvailable").asBoolean()).isFalse();request(a,HttpMethod.POST,url(p)+"/suggestions/"+s.path("id").asText()+"/decision",obj("requestKey",uuid(),"expectedVersion",r.path("plan").path("version"),"decision","ACCEPT"),409);
  assertThat(get(a,url(p)+"/records/"+r.path("record").path("id").asText()).path("record").path("taskSnapshot").path("sources")).isNotEmpty();
 }
 @Test @Order(9) void oldTrainingHttpJoinCompleteRetryRejoinQuitPreservesRewardSemantics(){
  Actor a=actor();get(null,"/api/skate/training-plans/810001");post(a,"/api/skate/training-plans/810001/join",obj());JsonNode current=get(a,"/api/user/training-plans/current");assertThat(current.path("userPlan").path("mode").asText()).isEqualTo("LEGACY");
  ObjectNode body=obj("practiceMinutes",9,"content","isolated legacy smoke");JsonNode done=post(a,"/api/user/training-tasks/810001/complete",body);assertThat(done.path("taskExpAwarded").asInt()).isEqualTo(8);assertThat(done.path("planExpAwarded").asInt()).isEqualTo(30);
  JsonNode retry=post(a,"/api/user/training-tasks/810001/complete",body);assertThat(retry.path("taskExpAwarded").asInt()).isZero();assertThat(retry.path("planExpAwarded").asInt()).isZero();assertThat(users.findById(a.id()).orElseThrow().getExp()).isEqualTo(38);
  JsonNode joined=post(a,"/api/skate/training-plans/810001/join",obj());long id=joined.path("userPlan").path("userPlanId").asLong();assertThat(id).isPositive();post(a,"/api/skate/training-plans/"+id+"/quit",obj());assertThat(get(a,"/api/user/training-records")).hasSize(1);
 }
 @Test @Order(10) void mysqlDifferentRootsConcurrentCompletionHasNoLostExperience()throws Exception{
  Actor a=actor();CurrentUser u=new CurrentUser(a.id(),"USER","local");JsonNode p=create(a);long id=p.path("id").asLong();JsonNode r=post(a,url(p)+"/feedback",feedback(p,0,"COMPLETED",5));p=decision(a,r.path("plan"),r.path("suggestions").get(0),"ACCEPT").path("plan");CLOCK.day(p.path("tasks").get(1).path("date").asText());
  ObjectNode one=feedback(p,0,"COMPLETED",2),two=feedback(p,1,"COMPLETED",3);
  var results=parallel(()->{try{return training.feedback(u,id,one);}catch(GuidanceException e){return obj("failure",e.errorCode);}},()->{try{return training.feedback(u,id,two);}catch(GuidanceException e){return obj("failure",e.errorCode);}});
  assertThat(results.stream().filter(x->!x.has("failure")).count()).isEqualTo(1);ObjectNode retry=results.get(0).has("failure")?one:two;retry.set("expectedVersion",training.plan(u,id).path("version"));training.feedback(u,id,retry);
  assertThat(users.findById(a.id()).orElseThrow().getExp()).isEqualTo(16);assertThat(jdbc.queryForObject("select count(*) from tb_guidance_completion where user_plan_id=?",Long.class,id)).isEqualTo(2);assertThat(training.statistics(u,id).path("practiceCount").asInt()).isEqualTo(3);
 }
}
