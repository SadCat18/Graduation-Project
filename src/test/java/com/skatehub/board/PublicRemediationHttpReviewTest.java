package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.SkateHubApplication;
import com.skatehub.dao.*;
import com.skatehub.pojo.*;
import com.skatehub.pojo.skate.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real listening HTTP/security/services/H2. Only the board catalog comes from recorded source;
 * supplemental public rows and captcha storage are isolated test fixtures, never production proof. */
@SpringBootTest(classes=SkateHubApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.config.location=classpath:t07-http.properties","server.port=0",
 "spring.datasource.url=jdbc:h2:mem:public-remediation-http-review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@Import(BoardT07HttpIntegrationTest.T07Bootstrap.class)
class PublicRemediationHttpReviewTest {
 @Autowired TestRestTemplate http; @Autowired ObjectMapper json;
 @Autowired AdminRepository admins; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
 @Autowired PlaceRepository places; @SpyBean PlaceReviewRepository reviews;
 @Autowired TrainingPlanRepository training; @Autowired TrainingTaskRepository tasks;
 @Autowired PostRepository posts; @Autowired SkateEquipmentRepository equipments;
 static final Path EV=Path.of("target/test-evidence/public-remediation-review");
 ArrayNode observations; String admin;
 ObjectNode obj(){return json.createObjectNode();}
 ResponseEntity<JsonNode> request(HttpMethod method,String path,JsonNode data,String token){
  HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(token!=null)h.setBearerAuth(token);
  var response=http.exchange(path,method,new HttpEntity<>(data,h),JsonNode.class);
  if(!path.startsWith("/api/auth"))observations.addObject().put("method",method.name()).put("path",path).put("status",response.getStatusCode().value()).set("body",response.getBody());
  return response;
 }
 JsonNode ok(HttpMethod method,String path,JsonNode data,String token){var response=request(method,path,data,token);assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);assertThat(response.getBody().path("code").asInt()).as(path).isEqualTo(200);return response.getBody().path("data");}
 JsonNode get(String path){return ok(HttpMethod.GET,path,null,null);}
 ObjectNode demand(int cents,String type){return obj().put("budgetCents",cents).put("purpose","ACTION").put("preference","UNSURE").put("recommendationType",type);}
 TrainingPlan plan(String name,int days,int... actual){TrainingPlan p=new TrainingPlan();p.setPlanName(name);p.setDurationDays(days);p=training.saveAndFlush(p);for(int day:actual){TrainingTask t=new TrainingTask();t.setPlanId(p.getPlanId());t.setDayNo(day);t.setTaskTitle("ISOLATED review day "+day);tasks.saveAndFlush(t);}return p;}
 Place place(String name){Place p=new Place();p.setName(name);p.setIntro("ISOLATED rail flat ground");p.setAddress("Fixture only");p.setVerificationStatus("APPROVED");p.setScore(new BigDecimal("4.9"));p.setReviewCount(203);p.setCreateTime(LocalDateTime.now());return places.saveAndFlush(p);}
 void post(String category,long userId){Post p=new Post();p.setUserId(userId);p.setTitle("ISOLATED category fixture");p.setContent("Local HTTP fixture only");p.setCategory(category);posts.saveAndFlush(p);}
 void item(String category,String status){SkateEquipment e=new SkateEquipment();e.setEquipmentName("ISOLATED "+category);e.setCategory(category);e.setBrand("Fixture");e.setStatus(status);equipments.saveAndFlush(e);}
 @Test void frozenPublicContractsAndRecordedBoardPassActualHttpAndOptionalBrowser()throws Exception{
  Files.createDirectories(EV);observations=json.createArrayNode();ObjectNode evidence=obj().put("layer","REAL_LISTENING_HTTP_SECURITY_H2; supplemental business rows SYNTHETIC; original board catalog RECORDED; no production/provider");
  http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
  try{
   Admin a=new Admin();a.setAccount("public_review_admin");a.setPassword(encoder.encode("Public-Review-Fixture-2026"));a.setRealName("Review");a.setRole("1");admins.saveAndFlush(a);
   admin=ok(HttpMethod.POST,"/api/auth/login/admin",obj().put("loginType","username").put("account",a.getAccount()).put("password","Public-Review-Fixture-2026").put("captchaId","review").put("captchaCode","T07X"),null).path("token").asText();assertThat(admin).isNotBlank();
   User u=new User();u.setUsername("public_review_user");u.setPhone("13900009121");u.setStatus("0");u.setPassword(encoder.encode("Public-Review-Fixture-2026"));u=users.saveAndFlush(u);
   TrainingPlan seven=plan("ISOLATED seven-day review",7,1,2,3),five=plan("ISOLATED five-day review",5,1,2),complete=plan("ISOLATED complete review",3,1,2,3),empty=plan("ISOLATED empty review",3),anomalous=plan("ISOLATED anomalous review",3,1,2,3,4);
   assertThat(get("/api/skate/training-plans/"+seven.getPlanId()).path("scheduleCoverage").path("missingDays").toString()).isEqualTo("[4,5,6,7]");
   assertThat(get("/api/skate/training-plans/"+five.getPlanId()).path("scheduleCoverage").path("missingDays").toString()).isEqualTo("[3,4,5]");
   assertThat(get("/api/skate/training-plans/"+complete.getPlanId()).path("scheduleCoverage").path("complete").asBoolean()).isTrue();
   assertThat(get("/api/skate/training-plans/"+empty.getPlanId()).path("scheduleCoverage").path("availableDays").isEmpty()).isTrue();
   assertThat(get("/api/skate/training-plans/"+anomalous.getPlanId()).path("scheduleCoverage").path("complete").asBoolean()).isFalse();
   Place zero=place("ISOLATED no local reviews"),rated=place("ISOLATED rated place");
   for(int score:List.of(4,5)){PlaceReview review=new PlaceReview();review.setPlaceId(rated.getPlaceId());review.setUserId(u.getUserId());review.setScore(score);reviews.saveAndFlush(review);}
   clearInvocations(reviews);JsonNode all=get("/api/public/places");verify(reviews,times(1)).aggregateLocalReviews(anyCollection());verify(reviews,never()).findByPlaceIdOrderByCreateTimeDesc(anyLong());
   JsonNode ratedList=null,zeroList=null;for(JsonNode p:all){if(p.path("placeId").asLong()==rated.getPlaceId())ratedList=p;if(p.path("placeId").asLong()==zero.getPlaceId())zeroList=p;}
   assertThat(ratedList).isNotNull();assertThat(zeroList).isNotNull();assertThat(ratedList.path("localReviewSummary").path("score").decimalValue()).isEqualByComparingTo("4.5");assertThat(ratedList.path("localReviewSummary").path("count").asInt()).isEqualTo(2);
   assertThat(get("/api/public/places/"+rated.getPlaceId()).path("localReviewSummary")).isEqualTo(ratedList.path("localReviewSummary"));assertThat(get("/api/public/places/"+rated.getPlaceId()+"/reviews").size()).isEqualTo(2);
   assertThat(zeroList.path("localReviewSummary").path("score").isNull()).isTrue();assertThat(zeroList.path("localReviewSummary").path("count").asInt()).isZero();assertThat(places.findById(zero.getPlaceId()).orElseThrow().getReviewCount()).isEqualTo(203);
   doThrow(new IllegalStateException("ISOLATED_AGGREGATE_FAILURE")).when(reviews).aggregateLocalReviews(anyCollection());var failure=request(HttpMethod.GET,"/api/public/places",null,null);assertThat(failure.getStatusCode().value()).isEqualTo(200);assertThat(failure.getBody().path("code").asInt()).isEqualTo(500);assertThat(failure.getBody().has("data")).isFalse();reset(reviews);
   for(int i=0;i<31;i++)post("ISOLATED history "+i,u.getUserId());post("装备讨论",u.getUserId());post("装备分享",u.getUserId());
   JsonNode categories=get("/api/public/posts/categories");List<String> keys=new ArrayList<>();categories.path("filterOptions").forEach(p->keys.add(p.path("key").asText()));assertThat(keys).contains("ISOLATED history 30","装备讨论","装备分享").isSorted().doesNotHaveDuplicates();assertThat(categories.path("aliasMap").isEmpty()).isTrue();
   for(String c:List.of("装备讨论","装备分享")){JsonNode result=get("/api/public/posts?category="+c+"&page=1&size=1");assertThat(result.path("total").asLong()).isGreaterThanOrEqualTo(1);assertThat(result.path("list").get(0).path("category").asText()).isEqualTo(c);}
   item("工具","1");item("砂纸","1");item("ISOLATED HIDDEN","0");JsonNode eq=get("/api/skate/equipments/categories");List<String> eqKeys=new ArrayList<>();eq.forEach(p->eqKeys.add(p.path("key").asText()));assertThat(eqKeys).contains("工具","砂纸").doesNotContain("ISOLATED HIDDEN");
   JsonNode dataset=json.readTree(Files.readString(Path.of("docs/equipment-data/normalized-equipment.reference.v1.json")));JsonNode validation=ok(HttpMethod.POST,"/api/admin/board-assistant/import/validate",dataset,admin);ObjectNode input=obj().put("expectedCatalogVersion",validation.path("catalogVersion").asText());input.set("dataset",dataset);assertThat(ok(HttpMethod.POST,"/api/admin/board-assistant/import",input,admin).path("created").asInt()).isEqualTo(266);
   for(int budget:List.of(32300,32299,10000)){JsonNode c=ok(HttpMethod.POST,"/api/skate/board-assistant/recommend",demand(budget,"COMPLETE"),null).path("complete");if(budget==32300){assertThat(c.path("state").asText()).isEqualTo("OK");assertThat(c.path("plans").get(0).path("totalCents").asInt()).isEqualTo(32300);}else{assertThat(c.path("state").asText()).isEqualTo("BUDGET_INSUFFICIENT");assertThat(c.path("shortfallCents").asInt()).isEqualTo(32300-budget);}}
   JsonNode result=ok(HttpMethod.POST,"/api/skate/board-assistant/recommend",demand(60000,"BOTH"),null);for(JsonNode p:result.path("assembly").path("plans")){assertThat(p.path("knownSubtotalCents").asInt()).isEqualTo(78620);assertThat(p.path("totalCents").isNull()).isTrue();assertThat(p.path("budgetDeltaCents").isNull()).isTrue();assertThat(p.path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");}
   assertThat(request(HttpMethod.GET,"/api/user/board-plans",null,null).getStatusCode().value()).isEqualTo(401);assertThat(request(HttpMethod.POST,"/api/user/board-plans",obj(),null).getStatusCode().value()).isEqualTo(401);assertThat(request(HttpMethod.POST,"/api/skate/equipments/categories",obj(),null).getStatusCode().value()).isEqualTo(401);
   ObjectNode ids=obj().put("seven",seven.getPlanId()).put("zeroPlace",zero.getPlaceId()).put("ratedPlace",rated.getPlaceId()).put("user",u.getUserId());Files.writeString(EV.resolve("fixture-ids.json"),ids.toPrettyString());
   if(Boolean.getBoolean("public.review.browser")){
    ProcessBuilder runner=new ProcessBuilder("node","node_modules/@playwright/test/cli.js","test","--config","playwright.public-http.config.cjs");runner.directory(Path.of("frontend").toAbsolutePath().toFile());runner.redirectErrorStream(true);runner.redirectOutput(EV.resolve("browser.log").toFile());runner.environment().put("REF_BACKEND",http.getRootUri());runner.environment().put("REF_DIST",Path.of("frontend/dist").toAbsolutePath().toString());runner.environment().put("PUBLIC_REVIEW_IDS",EV.resolve("fixture-ids.json").toAbsolutePath().toString());
    Process browser=runner.start();assertThat(browser.waitFor(180,TimeUnit.SECONDS)).as("bounded real public browser").isTrue();assertThat(browser.exitValue()).as("real public HTTP browser; see target evidence").isZero();
   }
  }finally{evidence.set("observations",observations);Files.writeString(EV.resolve("http-results.json"),evidence.toPrettyString());}
 }
}
