package com.skatehub.board;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.SkateHubApplication;
import com.skatehub.dao.*;
import com.skatehub.pojo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(classes=SkateHubApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.config.location=classpath:t07-http.properties","server.port=0","spring.datasource.url=jdbc:h2:mem:reference-acceptance;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@Import(BoardT07HttpIntegrationTest.T07Bootstrap.class)
class BoardReferenceAcceptanceHttpTest {
 @Autowired TestRestTemplate http; @Autowired ObjectMapper json; @Autowired AdminRepository admins; @Autowired UserRepository users; @Autowired PasswordEncoder encoder;
 static final Path EV=Path.of("target/test-evidence/board/REFERENCE-01");
 ArrayNode observations;String admin,owner,other;
 ResponseEntity<JsonNode> req(HttpMethod method,String path,JsonNode body,String token){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(token!=null)h.setBearerAuth(token);var r=http.exchange(path,method,new HttpEntity<>(body,h),JsonNode.class);ObjectNode o=observations.addObject().put("method",method.name()).put("path",path).put("status",r.getStatusCode().value());if(!path.startsWith("/api/auth"))o.set("response",r.getBody());return r;}
 JsonNode ok(HttpMethod m,String path,JsonNode b,String t){var r=req(m,path,b,t);assertThat(r.getStatusCode().value()).as(path).isEqualTo(200);assertThat(r.getBody().path("code").asInt()).isEqualTo(200);return r.getBody().path("data");}
 ObjectNode obj(){return json.createObjectNode();}ObjectNode demand(String p,long amount){return obj().put("budgetCents",amount).put("purpose",p).put("preference","UNSURE").put("recommendationType","BOTH");}
 String login(String who,String type){return ok(HttpMethod.POST,"/api/auth/login/"+type,obj().put("loginType","username").put("account",who).put("password","Reference-Local-2026").put("captchaId","reference").put("captchaCode","T07X"),null).path("token").asText();}
 String user(String name,String phone){User u=new User();u.setUsername(name);u.setPhone(phone);u.setPassword(encoder.encode("Reference-Local-2026"));u.setStatus("0");users.saveAndFlush(u);return login(name,"user");}
 ObjectNode selectionRequest(JsonNode e){ObjectNode b=obj();b.set("demand",e.path("demand"));b.set("selection",e.path("selection"));return b;}
 ObjectNode save(JsonNode e,String name){return selectionRequest(e).put("name",name).put("expectedCalculationVersion",e.path("calculationVersion").asText());}
 @Test void originalReferenceCatalogActualHttpAndEdgeAcceptance() throws Exception {
  http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
  Files.createDirectories(EV);observations=json.createArrayNode();ObjectNode evidence=obj().put("evidenceKind","ORIGINAL_RECORDED_CATALOG_REAL_HTTP_SECURITY_H2_AND_EDGE_NOT_PRODUCTION").put("captchaBoundary","T07 storage substitute; real login/JWT/security/controllers/services; old schema prerequisite fixture only");
  try{
   Admin a=new Admin();a.setAccount("ref_accept_admin");a.setPassword(encoder.encode("Reference-Local-2026"));a.setRealName("REF local");a.setRole("1");admins.saveAndFlush(a);admin=login(a.getAccount(),"admin");owner=user("ref_accept_user","13900008101");other=user("ref_other_user","13900008102");
   JsonNode dataset=json.readTree(Files.readString(Path.of("docs/equipment-data/normalized-equipment.reference.v1.json")));var validation=ok(HttpMethod.POST,"/api/admin/board-assistant/import/validate",dataset,admin);assertThat(validation.path("valid").asBoolean()).isTrue();ObjectNode body=obj().put("expectedCatalogVersion",validation.path("catalogVersion").asText());body.set("dataset",dataset);
   var imported=ok(HttpMethod.POST,"/api/admin/board-assistant/import",body,admin);assertThat(imported.path("created").asInt()).isEqualTo(266);body.put("expectedCatalogVersion",imported.path("catalogVersion").asText());assertThat(ok(HttpMethod.POST,"/api/admin/board-assistant/import",body,admin).path("unchanged").asInt()).isEqualTo(266);
   Map<String,Integer> counts=Map.of("products",50,"variants",114,"quotes",60,"rules",39,"templates",3);for(var entry:counts.entrySet())assertThat(ok(HttpMethod.GET,"/api/admin/board-assistant/"+entry.getKey()+"?size=1",null,admin).path("total").asInt()).isEqualTo(entry.getValue());
   JsonNode primary=null,partial=null;for(String purpose:List.of("ACTION","CRUISE","BALANCED")){JsonNode r=ok(HttpMethod.POST,"/api/skate/board-assistant/recommend",demand(purpose,60000),null);Set<Long> prices=new HashSet<>();for(JsonNode p:r.path("complete").path("plans")){prices.add(p.path("totalCents").asLong());assertThat(p.path("pricingMode").asText()).isEqualTo("REFERENCE");assertThat(p.path("validationState").asText()).isEqualTo("REFERENCE_ESTIMATE");assertThat(p.path("items").get(0).path("quoteSources").toString()).contains("2026-10-09","RECORDED");assertThat(p.path("items").get(0).path("shippingCents").isNull()).isTrue();}assertThat(prices).containsExactlyInAnyOrder(58000L,32300L,44600L);if(primary==null){primary=r.path("complete").path("plans").get(0);partial=r.path("assembly").path("plans").get(0);}for(JsonNode p:r.path("assembly").path("plans")){assertThat(p.path("totalCents").isNull()).isTrue();assertThat(p.path("budgetDeltaCents").isNull()).isTrue();assertThat(p.path("knownSubtotalCents").asLong()).isEqualTo(78620);}}
   JsonNode low=ok(HttpMethod.POST,"/api/skate/board-assistant/recommend",demand("ACTION",10000),null).path("complete");assertThat(low.path("minimumFeasiblePlan").path("totalCents").asLong()).isEqualTo(32300);assertThat(low.path("shortfallCents").asLong()).isEqualTo(22300);
   String vk=primary.path("items").get(0).path("variantKey").asText();assertThat(ok(HttpMethod.GET,"/api/skate/board-assistant/variants/"+vk,null,null).path("eligibility").path("referencePriceEligible").asBoolean()).isTrue();
   ObjectNode compare=obj();compare.set("plans",json.createArrayNode().add(selectionRequest(primary)).add(selectionRequest(partial)));assertThat(ok(HttpMethod.POST,"/api/skate/board-assistant/compare",compare,null).path("evaluations").size()).isEqualTo(2);
   ObjectNode replacement=selectionRequest(primary).put("category","COMPLETE");assertThat(ok(HttpMethod.POST,"/api/skate/board-assistant/replacements",replacement,null).path("total").asInt()).isEqualTo(4);
   JsonNode saved=ok(HttpMethod.POST,"/api/user/board-plans",save(primary,"Reference HTTP snapshot"),owner);assertThat(saved.path("snapshotState").asText()).isEqualTo("REFERENCE_ESTIMATE");String key=saved.path("key").asText();
   assertThat(ok(HttpMethod.GET,"/api/user/board-plans/"+key,null,owner).path("snapshot").path("totalCents")).isEqualTo(primary.path("totalCents"));assertThat(ok(HttpMethod.GET,"/api/user/board-plans?size=1",null,owner).path("total").asInt()).isEqualTo(1);
   assertThat(req(HttpMethod.GET,"/api/user/board-plans/"+key,null,other).getStatusCode().value()).isEqualTo(404);assertThat(req(HttpMethod.GET,"/api/user/board-plans",null,null).getStatusCode().value()).isEqualTo(401);assertThat(req(HttpMethod.GET,"/api/admin/board-assistant/products",null,owner).getStatusCode().value()).isEqualTo(403);
   JsonNode draft=ok(HttpMethod.POST,"/api/user/board-plans",save(partial,"Partial reference draft"),owner);assertThat(draft.path("snapshotState").asText()).isEqualTo("DRAFT");assertThat(draft.path("totalCents").isNull()).isTrue();
   if (Boolean.getBoolean("board.browser-tests")) { var runner=new ProcessBuilder("node","node_modules/@playwright/test/cli.js","test","--config","playwright.reference.config.cjs");runner.directory(Path.of("frontend").toAbsolutePath().toFile());runner.redirectErrorStream(true);runner.redirectOutput(EV.resolve("reference-edge.log").toFile());runner.environment().put("REF_BACKEND",http.getRootUri());runner.environment().put("REF_EVIDENCE",EV.toAbsolutePath().toString());runner.environment().put("REF_DIST",Path.of("frontend/dist").toAbsolutePath().toString());var browser=runner.start();assertThat(browser.waitFor(150,TimeUnit.SECONDS)).isTrue();assertThat(browser.exitValue()).as("real reference Edge flow").isZero(); }
   // Only isolated synthetic one-cent revision tests version confirmation; original source file is untouched.
   primary=ok(HttpMethod.POST,"/api/skate/board-assistant/evaluate",selectionRequest(primary),null); // Refresh bounded recent-price cache after 2000-evaluation browser searches.
   String qkey=primary.path("items").get(0).path("quoteKey").asText();ok(HttpMethod.PATCH,"/api/admin/board-assistant/quotes/"+qkey,obj().put("expectedVersion",2).put("amountCents",primary.path("totalCents").asLong()+1),admin);
   JsonNode conflict=ok(HttpMethod.POST,"/api/user/board-plans",save(primary,"stale confirmation"),owner);assertThat(conflict.path("errorCode").asText()).isEqualTo("PRICE_CHANGED");JsonNode hist=ok(HttpMethod.GET,"/api/user/board-plans/"+key,null,owner);assertThat(hist.path("snapshot").path("totalCents")).isEqualTo(primary.path("totalCents"));assertThat(hist.path("latestEvaluation").path("totalCents").asLong()).isEqualTo(primary.path("totalCents").asLong()+1);
   JsonNode copied=ok(HttpMethod.POST,"/api/user/board-plans/"+key+"/copy",obj().put("name","Historical reference"),owner);assertThat(copied.path("totalCents")).isEqualTo(primary.path("totalCents"));assertThat(ok(HttpMethod.POST,"/api/user/board-plans",save(conflict.path("evaluation"),"Reconfirmed reference"),owner).path("snapshotState").asText()).isEqualTo("REFERENCE_ESTIMATE");
  }finally{evidence.set("observations",observations);Files.writeString(EV.resolve("reference-http-acceptance.json"),evidence.toPrettyString());}
 }
}
