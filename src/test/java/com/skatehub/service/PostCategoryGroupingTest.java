package com.skatehub.service;

import com.skatehub.dao.*;
import com.skatehub.pojo.Post;
import com.skatehub.pojo.post.PostCreateRequest;
import com.skatehub.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=PublicReadContractTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.config.location=optional:classpath:public-read-contract-no-config.properties",
 "spring.datasource.url=jdbc:h2:mem:public-category-groups;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","logging.level.root=WARN"})
class PostCategoryGroupingTest {
 @Autowired PostService posts;@Autowired PostRepository repository;
 @MockBean CommunityBulletinService bulletins;@MockBean UserGrowthService growth;@MockBean MessageNotifyService notify;@MockBean ModerationRuleService moderation;
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @BeforeEach void clear(){repository.deleteAllInBatch();}
 Post seed(String label,int likes){Post p=new Post();p.setUserId(999L);p.setTitle("Synthetic group "+likes);p.setContent("same keyword content");p.setCategory(label);p.setLikeCount(likes);return repository.saveAndFlush(p);}
 @Test void strictAliasesExpandTheSameSetForKeywordSortCountAndPages(){
  for(var alias:PostCategoryOptions.ALIASES.entrySet()){
   Post a=seed(alias.getKey(),1),b=seed(alias.getValue(),2);
   for(String sort:List.of("latest","hot","likes","comments")){
    var canonical=posts.list("same",alias.getValue(),sort,1,1);var legacy=posts.list("same",alias.getKey(),sort,1,1);
    assertThat(canonical.getTotal()).isEqualTo(2);assertThat(legacy.getTotal()).isEqualTo(2);assertThat(canonical.getList().get(0).get("postId")).isEqualTo(legacy.getList().get(0).get("postId"));
    var second=posts.list("same",alias.getKey(),sort,2,1);assertThat(second.getList()).hasSize(1);assertThat(second.getList().get(0).get("postId")).isNotEqualTo(legacy.getList().get(0).get("postId"));
   }
   assertThat(repository.findById(a.getPostId()).orElseThrow().getCategory()).isEqualTo(alias.getKey());assertThat(repository.findById(b.getPostId()).orElseThrow().getCategory()).isEqualTo(alias.getValue());
  }
 }
 @Test void organizationIsNotAnAliasAndUnknownOrUncategorizedPostsRemainReachable(){
  for(String label:List.of("刷街","康复","拍摄","文化","碗池","街式","赛事","进阶","长板","未来新标签","经验分享"))seed(label,1);
  seed(null,0);seed(" ",0);
  assertThat(posts.list("same","经验分享","hot",1,50).getTotal()).isEqualTo(13);
  assertThat(posts.list("same","文化","hot",1,50).getTotal()).isEqualTo(1);assertThat(posts.list("same","未来新标签","latest",1,50).getTotal()).isEqualTo(1);
  var data=mapper.valueToTree(posts.categories());assertThat(data.path("filterOptions")).hasSize(8);assertThat(data.path("aliasMap").has("文化")).isFalse();assertThat(data.path("groupMap").path("文化").asText()).isEqualTo("经验分享");assertThat(data.path("legacyFilterOptions").toString()).contains("未来新标签");
  var all=posts.list(null,"","latest",1,50);assertThat(all.getTotal()).isEqualTo(13);
 }
 @Test void createAcceptsTheOldInputAndPreservesItsRawCategory(){
  PostCreateRequest body=new PostCreateRequest();body.setTitle("Synthetic old publish input");body.setContent("fixture");body.setCategory("技巧");Post saved=posts.create(new CurrentUser(999L,"USER","local"),body);
  assertThat(saved.getCategory()).isEqualTo("技巧");assertThat(posts.list(null,"技巧交流","latest",1,20).getTotal()).isEqualTo(1);assertThat(posts.detail(saved.getPostId()).get("categoryGroup")).isEqualTo("技巧交流");
 }
 @Test void categoryExamplesAreActualServiceResults()throws Exception{
  seed("入门",1);seed("新手入门",2);seed("文化",3);seed("拍摄",1);seed("未来新标签",0);
  String output=System.getProperty("remediation.evidenceDir");if(output==null)return;
  Map<String,Object> samples=new LinkedHashMap<>();samples.put("layer","REAL_SERVICE_ISOLATED_H2_NOT_HTTP_MYSQL_PRODUCTION");samples.put("GET /api/public/posts/categories",posts.categories());
  for(String category:List.of("入门","新手入门","经验分享","文化"))samples.put("GET /api/public/posts?category="+category+"&sort=hot&page=1&size=2",posts.list(null,category,"hot",1,2));
  Path path=Path.of(output,"category-examples.json");Files.createDirectories(path.getParent());Files.writeString(path,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(samples));
 }
}
