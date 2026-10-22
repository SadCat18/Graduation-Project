package com.skatehub.service.training;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class GuidanceMigrationTest {
 @Test void realV1TrainingSchemaAndLegacyFactsSurviveIncrementalV11ThroughV13() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:guidance-migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");
  var pop=new ResourceDatabasePopulator();
  for(String file:new String[]{"db/migration/V1__init_schema.sql","db/migration/V8__create_missing_application_tables.sql"}) {
   String sql=new String(new ClassPathResource(file).getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("\uFEFF","");
   pop.addScript(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
  }
  pop.setSqlScriptEncoding("UTF-8");pop.execute(ds);
  // V1/V8 form a complete old training-table fixture; V2-V7 are MySQL-specific and are not claimed as executed.
  Flyway.configure().dataSource(ds).baselineVersion("8").baselineOnMigrate(true).target("10").load().migrate();
  var db=new JdbcTemplate(ds);
  db.update("insert into tb_user(user_id,username,password) values(91,'synthetic-migration','LOCAL-ONLY')");
  db.update("insert into tb_training_plan(plan_id,plan_name,duration_days,status) values(92,'legacy',1,'1')");
  db.update("insert into tb_training_task(task_id,plan_id,day_no,task_title) values(93,92,1,'legacy-task')");
  db.update("insert into tb_user_training_plan(user_plan_id,user_id,plan_id,status,completed_days) values(94,91,92,'COMPLETED',1)");
  db.update("insert into tb_user_training_record(record_id,user_plan_id,task_id,user_id,practice_minutes,exp_awarded) values(95,94,93,91,15,8)");
  String record=db.queryForMap("select * from tb_user_training_record where record_id=95").toString();
  var result=Flyway.configure().dataSource(ds).load().migrate();assertThat(result.migrationsExecuted).isEqualTo(3);
  assertThat(db.queryForMap("select * from tb_user_training_record where record_id=95").toString()).isEqualTo(record);
  assertThat(db.queryForObject("select mode from tb_user_training_plan where user_plan_id=94",String.class)).isEqualTo("LEGACY");
  assertThat(db.queryForObject("select plan_id from tb_user_training_plan where user_plan_id=94",Long.class)).isEqualTo(92);
  assertThat(db.queryForObject("select count(*) from tb_guidance_practice",Long.class)).isZero();
  assertThat(db.queryForObject("select count(*) from tb_guidance_source where status='AVAILABLE'",Long.class)).isEqualTo(4);
  assertThatThrownBy(()->db.update("insert into tb_user_training_record(user_plan_id,task_id,user_id) values(94,93,91)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("insert into tb_user_training_plan(user_id,plan_id,mode) values(91,null,'LEGACY')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("insert into tb_user_training_plan(user_id,plan_id,mode) values(91,0,'GUIDED')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(Flyway.configure().dataSource(ds).load().migrate().migrationsExecuted).isZero();
 }
}
