package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.SkateHubApplication;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.User;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual local HTTP/security/H2; fixture rows are synthetic and never production facts. */
@SpringBootTest(classes=SkateHubApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.config.location=classpath:t07-http.properties","server.port=0",
 "spring.datasource.url=jdbc:h2:mem:community-race-http-review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@Import(BoardT07HttpIntegrationTest.T07Bootstrap.class)
class CommunityReadRaceHttpReviewTest {
 @Autowired TestRestTemplate http; @Autowired PostRepository posts; @Autowired UserRepository users;
 static final Path EV=Path.of("target/test-evidence/community-race-http-review");
 static final String CATEGORY="本地审查竞态";
 @Test void exactReadContractAndOptionalRealResponseOrderingBrowser()throws Exception{
  Files.createDirectories(EV);
  User user=users.findAll().stream().findFirst().orElseGet(()->{
   User u=new User();u.setUsername("race_review_user");u.setPhone("13900009131");u.setPassword("NO_LOGIN_LOCAL_FIXTURE");u.setStatus("0");return users.saveAndFlush(u);
  });
  for(int i=0;i<8;i++){
   Post p=new Post();p.setUserId(user.getUserId());p.setTitle("ISOLATED HTTP RACE "+i);p.setContent("Synthetic local row; not source data");p.setCategory(i<6?"本地旧分类":CATEGORY);p.setCreateTime(LocalDateTime.now().plusSeconds(i));posts.saveAndFlush(p);
  }
  JsonNode all=http.getForObject("/api/public/posts?page=1&size=6&sort=latest",JsonNode.class);
  JsonNode filtered=http.getForObject("/api/public/posts?page=1&size=6&sort=latest&category="+CATEGORY,JsonNode.class);
  assertThat(all.path("code").asInt()).isEqualTo(200);assertThat(all.path("data").path("list").size()).isEqualTo(6);
  assertThat(filtered.path("code").asInt()).isEqualTo(200);assertThat(filtered.path("data").path("total").asInt()).isEqualTo(2);assertThat(filtered.path("data").path("list").size()).isEqualTo(2);
  Files.writeString(EV.resolve("http-counts.json"),"{\"layer\":\"REAL_LOCAL_HTTP_SECURITY_H2_SYNTHETIC_ROWS\",\"allPageRows\":6,\"filteredTotal\":2}");
  if(Boolean.getBoolean("community.race.browser")){
   ProcessBuilder runner=new ProcessBuilder("node","node_modules/@playwright/test/cli.js","test","--config","playwright.community-http.config.cjs");
   runner.directory(Path.of("frontend").toAbsolutePath().toFile());runner.redirectErrorStream(true);runner.redirectOutput(EV.resolve("browser.log").toFile());
   runner.environment().put("REF_BACKEND",http.getRootUri());runner.environment().put("REF_DIST",Path.of("frontend/dist").toAbsolutePath().toString());runner.environment().put("COMMUNITY_REVIEW_CATEGORY",CATEGORY);runner.environment().put("COMMUNITY_REVIEW_EVIDENCE",EV.toAbsolutePath().toString());
   Process browser=runner.start();if(!browser.waitFor(120,TimeUnit.SECONDS)){browser.destroyForcibly();throw new AssertionError("Bounded community HTTP browser timeout");}
   assertThat(browser.exitValue()).as("real-response browser; see target evidence").isZero();
  }
 }
}
