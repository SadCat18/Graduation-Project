package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.SkateHubApplication;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.board.repository.*;
import com.skatehub.pojo.Admin;
import com.skatehub.pojo.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real local HTTP through the production controllers/security chain; all database rows are test fixtures. */
@SpringBootTest(
        classes = SkateHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "spring.config.location=classpath:t07-http.properties")
@Import(BoardT07HttpIntegrationTest.T07Bootstrap.class)
class BoardT07HttpIntegrationTest {
    private static final String BASE = "http://127.0.0.1:8080";
    private static final String USER_PASSWORD = "T07-Synthetic-Only-2026";
    private static final String ADMIN_PASSWORD = "T07-Admin-Synthetic-2026";
    private static final Path EVIDENCE = Path.of("target/test-evidence/board");

    @TestConfiguration(proxyBeanMethods = false)
    static class T07Bootstrap {
        private Resource bomless(String location) {
            try (var input = new ClassPathResource(location).getInputStream()) {
                byte[] bytes = input.readAllBytes();
                int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                        && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
                byte[] sql = java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
                return new ByteArrayResource(sql) {
                    @Override public String getFilename() { return location.substring(location.lastIndexOf('/') + 1); }
                };
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read test-only H2 bootstrap " + location, e);
            }
        }

        @Bean(name = "stringRedisTemplate")
        StringRedisTemplate t07CaptchaStore() {
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> operations = mock(ValueOperations.class);
            when(operations.get(anyString())).thenReturn("T07X");
            return new StringRedisTemplate(mock(RedisConnectionFactory.class)) {
                @Override public ValueOperations<String, String> opsForValue() { return operations; }
                @Override public Boolean delete(String key) { return true; }
            };
        }

        @Bean
        FlywayMigrationStrategy t07OnlyMigrations() {
            return flyway -> {
                ResourceDatabasePopulator baseline = new ResourceDatabasePopulator();
                baseline.addScript(bomless("db/migration/V1__init_schema.sql"));
                baseline.addScript(bomless("db/migration/V8__create_missing_application_tables.sql"));
                baseline.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
                baseline.execute(flyway.getConfiguration().getDataSource());
                try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                     var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS token_version INT DEFAULT 0");
                    statement.execute("ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS exp INT DEFAULT 0");
                    // These are test-only fixtures for the old-route smoke, not Flyway V2-V7 execution evidence.
                    statement.execute("ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS place_id BIGINT");
                    statement.execute("ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS arrival_confirmed CHAR(1) DEFAULT '0'");
                    statement.execute("ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS return_confirmed CHAR(1) DEFAULT '0'");
                    statement.execute("ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS arrival_confirm_time DATETIME NULL");
                    statement.execute("ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS return_confirm_time DATETIME NULL");
                    statement.execute("CREATE TABLE IF NOT EXISTS tb_product_event ("
                            + "event_id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type VARCHAR(50) NOT NULL, "
                            + "user_id BIGINT NULL, target_type VARCHAR(30) NULL, target_id BIGINT NULL, "
                            + "source_page VARCHAR(80) NULL, create_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
                } catch (Exception e) {
                    throw new IllegalStateException("T07 H2 baseline bootstrap failed", e);
                }
                // The checked-in V1/V8 schema is a local H2 fixture; baseline 8 is only the prerequisite
                // marker for exercising the real V9/V10 files. This does not claim V2-V7 ran on H2.
                flyway.baseline();
                flyway.migrate();
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired BoardDatasetValidator json;
    @Autowired BoardCatalogService catalog;
    @Autowired BoardPlanService plans;
    @Autowired BoardCatalogMetaRepository meta;
    @Autowired BoardProductRepository products;
    @Autowired BoardVariantRepository variants;
    @Autowired BoardQuoteRepository quotes;
    @Autowired BoardRuleRepository rules;
    @Autowired BoardTemplateRepository templates;
    @Autowired BoardPlanSnapshotRepository snapshots;
    @Autowired UserRepository users;
    @Autowired AdminRepository admins;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    private final List<ObjectNode> observations = new ArrayList<>();
    private ObjectNode evidence;
    private ObjectNode dataset;
    private ObjectNode lastImportedDataset;
    private User owner;
    private User other;
    private Admin admin;
    private String ownerToken;
    private String otherToken;
    private String adminToken;

    private void record(String name, String method, String path, ResponseEntity<JsonNode> response) {
        ObjectNode row = mapper.createObjectNode()
                .put("scenario", name)
                .put("method", method)
                .put("path", path)
                .put("httpStatus", response.getStatusCode().value())
                .put("apiCode", response.getBody() == null ? -1 : response.getBody().path("code").asInt(-1))
                .put("apiMessage", response.getBody() == null ? "" : response.getBody().path("message").asText(""));
        observations.add(row);
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(token)) headers.setBearerAuth(token);
        return http.exchange(BASE + path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode ok(String scenario, HttpMethod method, String path, Object body, String token) {
        ResponseEntity<JsonNode> response = request(method, path, body, token);
        record(scenario, method.name(), path, response);
        assertThat(response.getStatusCode()).as(scenario + " HTTP status").isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).as(scenario + " response").isNotNull();
        assertThat(response.getBody().path("code").asInt())
                .as(scenario + " API response: " + response.getBody().toPrettyString()).isEqualTo(200);
        return response.getBody().path("data");
    }

    private User newUser(String username, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(USER_PASSWORD));
        user.setStatus("0");
        return users.saveAndFlush(user);
    }

    private String login(String account, String password, String loginType, String label) {
        ObjectNode body = mapper.createObjectNode()
                .put("loginType", loginType)
                .put("account", account)
                .put("password", password)
                .put("captchaId", "t07-" + label)
                .put("captchaCode", "T07X");
        JsonNode data = ok("real auth login: " + label, HttpMethod.POST, "/api/auth/login/" + label,
                body, null);
        return data.path("token").asText();
    }

    private void seedCatalog() {
        snapshots.deleteAllInBatch();
        quotes.deleteAllInBatch();
        variants.deleteAllInBatch();
        rules.deleteAllInBatch();
        templates.deleteAllInBatch();
        products.deleteAllInBatch();
        jdbc.update("update tb_board_catalog_meta set catalog_version=?,dataset_fingerprints='{}' where id=1",
                json.hash(json.array()));
        dataset = BoardCalculationTest.assembly(json);
        dataset.remove("catalogVersion");
        ObjectNode alternativeDeck = BoardTestData.find(dataset, "variants", "SYN-V-DECK").deepCopy();
        alternativeDeck.put("key", "SYN-V-DECK-ALT").put("label", "SYNTHETIC DECK ALT");
        ((ObjectNode) alternativeDeck.path("specs")).put("widthInch", 8.25);
        ((com.fasterxml.jackson.databind.node.ArrayNode) dataset.path("variants")).add(alternativeDeck);
        ObjectNode alternativeDeckQuote = BoardTestData.find(dataset, "quotes", "SYN-Q-DECK").deepCopy();
        alternativeDeckQuote.put("key", "SYN-Q-DECK-ALT").put("variantKey", "SYN-V-DECK-ALT")
                .put("version", 1).put("amountCents", 151).put("displayPrice", "SYNTHETIC alternative deck");
        ((com.fasterxml.jackson.databind.node.ArrayNode) dataset.path("quotes")).add(alternativeDeckQuote);
        for (JsonNode mapping : dataset.path("mapping")) {
            if ("SYN-P-DECK".equals(mapping.path("sourceRecordId").asText())) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) mapping.path("variantKeys")).add("SYN-V-DECK-ALT");
                ((com.fasterxml.jackson.databind.node.ArrayNode) mapping.path("quoteKeys")).add("SYN-Q-DECK-ALT");
            }
        }
        ((com.fasterxml.jackson.databind.node.ArrayNode) BoardTestData.find(dataset, "templates", "SYN-T")
                .path("selectionConditions").path("eligibleVariantKeys")).add("SYN-V-DECK-ALT");
        var result = catalog.importDataset(dataset, catalog.catalogVersion(), admin.getAdminId());
        assertThat(result.errors()).isEmpty();
        lastImportedDataset = dataset.deepCopy();
    }

    private ObjectNode input() {
        ObjectNode input = mapper.createObjectNode();
        input.set("demand", BoardCalculationTest.demand(json));
        input.set("selection", BoardCalculationTest.selection(json));
        return input;
    }

    private void importSyntheticCatalog(ObjectNode next, String version) {
        next.put("datasetVersion", version);
        for (String group : List.of("products", "variants", "quotes", "rules", "templates")) {
            for (JsonNode row : next.path(group)) {
                JsonNode previous = null;
                for (JsonNode candidate : lastImportedDataset.path(group)) {
                    if (row.path("key").asText().equals(candidate.path("key").asText())) { previous = candidate; break; }
                }
                if (previous == null || !row.isObject() || !previous.isObject()) continue;
                ObjectNode nextComparable = ((ObjectNode) row).deepCopy();
                ObjectNode previousComparable = ((ObjectNode) previous).deepCopy();
                nextComparable.remove("version");
                previousComparable.remove("version");
                ((ObjectNode) row).put("version", nextComparable.equals(previousComparable)
                        ? previous.path("version").asInt(1) : previous.path("version").asInt(1) + 1);
            }
        }
        var result = catalog.importDataset(next, catalog.catalogVersion(), admin.getAdminId());
        assertThat(result.errors()).as("T07 synthetic catalog import: " + version).isEmpty();
        lastImportedDataset = next.deepCopy();
    }

    private void deactivateVariantFixture(String key) throws Exception {
        var row = variants.findByKey(key).orElseThrow();
        ObjectNode content = (ObjectNode) mapper.readTree(row.getContentJson());
        content.put("active", false);
        row.setActive(false);
        row.setContentJson(content.toString());
        variants.saveAndFlush(row);
        BoardTestData.find(lastImportedDataset, "variants", key).put("active", false);
    }

    private ObjectNode purposePreferenceFixture() {
        ObjectNode next = dataset.deepCopy();
        for (String purpose : List.of("CRUISE", "BALANCED")) {
            ObjectNode template = BoardTestData.find(next, "templates", "SYN-T").deepCopy();
            template.put("key", "SYN-T-" + purpose).put("purpose", purpose + "_BEGINNER");
            ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("templates")).add(template);
        }

        ObjectNode easyRule = BoardTestData.find(next, "rules", "SYN-R-GRIP_COVERAGE").deepCopy();
        addSyntheticUsageRule(easyRule, "SYN-T07-EASY-START", "SYNTHETIC-V", "EASY_START");
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("rules")).add(easyRule);
        ((com.fasterxml.jackson.databind.node.ArrayNode) BoardTestData.find(next, "mapping", "SYNTHETIC-P").path("ruleKeys"))
                .add("SYN-T07-EASY-START");

        ObjectNode product = BoardTestData.find(next, "products", "SYNTHETIC-P").deepCopy();
        product.put("key", "SYNTHETIC-P-COMFORT").put("sourceRecordId", "SYNTHETIC-P-COMFORT").put("model", "SYNTHETIC comfort complete");
        ObjectNode variant = BoardTestData.find(next, "variants", "SYNTHETIC-V").deepCopy();
        variant.put("key", "SYNTHETIC-V-COMFORT").put("productKey", "SYNTHETIC-P-COMFORT").put("label", "SYNTHETIC comfort complete");
        ((ObjectNode) variant.path("specs")).put("deckWidthInch", 9);
        ObjectNode quote = BoardTestData.find(next, "quotes", "SYNTHETIC-Q").deepCopy();
        quote.put("key", "SYNTHETIC-Q-COMFORT").put("sourceProductKey", "SYNTHETIC-P-COMFORT")
                .put("variantKey", "SYNTHETIC-V-COMFORT").put("amountCents", 11000).put("displayPrice", "110.00 CNY SYNTHETIC");
        ObjectNode mapping = BoardTestData.find(next, "mapping", "SYNTHETIC-P").deepCopy();
        mapping.put("sourceRecordId", "SYNTHETIC-P-COMFORT").put("productKey", "SYNTHETIC-P-COMFORT");
        mapping.set("variantKeys", mapper.createArrayNode().add("SYNTHETIC-V-COMFORT"));
        mapping.set("quoteKeys", mapper.createArrayNode().add("SYNTHETIC-Q-COMFORT"));
        mapping.set("ruleKeys", mapper.createArrayNode().add("SYN-T07-COMFORT"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("products")).add(product);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("variants")).add(variant);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("quotes")).add(quote);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("mapping")).add(mapping);
        ObjectNode comfortRule = BoardTestData.find(next, "rules", "SYN-R-GRIP_COVERAGE").deepCopy();
        addSyntheticUsageRule(comfortRule, "SYN-T07-COMFORT", "SYNTHETIC-V-COMFORT", "COMFORT");
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("rules")).add(comfortRule);
        return next;
    }

    private void addSyntheticUsageRule(ObjectNode rule, String key, String variantKey, String preference) {
        rule.put("key", key).put("type", "USAGE_ADVICE").put("severity", "ADVICE");
        ObjectNode scope = mapper.createObjectNode();
        scope.set("productKeys", mapper.createArrayNode());
        scope.set("variantKeys", mapper.createArrayNode().add(variantKey));
        scope.putNull("construction");
        rule.set("scope", scope);
        ObjectNode conditions = mapper.createObjectNode().put("purpose", "ACTION").put("preference", preference)
                .put("message", "SYNTHETIC sourced preference fact");
        conditions.set("variantKeys", mapper.createArrayNode().add(variantKey));
        rule.set("conditions", conditions);
    }

    private void verifyPurposePreferenceHttp() {
        importSyntheticCatalog(purposePreferenceFixture(), "t07-purpose-preference-v1");
        var samples = mapper.createArrayNode();
        for (var sample : List.<Object[]>of(
                new Object[]{"ACTION", 2000, "SYN-T"},
                new Object[]{"CRUISE", 11000, "SYN-T-CRUISE"},
                new Object[]{"BALANCED", 12000, "SYN-T-BALANCED"})) {
            String purpose = (String) sample[0];
            int budget = (int) sample[1];
            String templateKey = (String) sample[2];
            ObjectNode demand = BoardCalculationTest.demand(json).put("purpose", purpose).put("budgetCents", budget);
            JsonNode result = ok("scenario 2 real HTTP synthetic purpose/budget " + purpose,
                    HttpMethod.POST, "/api/skate/board-assistant/recommend", demand, null);
            assertThat(result.path("assembly").path("plans").get(0).path("templateKey").asText()).isEqualTo(templateKey);
            assertThat(result.path("complete").path("state").asText()).isNotEqualTo("NOT_REQUESTED");
            samples.add(mapper.createObjectNode().put("purpose", purpose).put("budgetCents", budget)
                    .put("templateKey", templateKey).put("assemblyState", result.path("assembly").path("state").asText())
                    .put("assemblyPlans", result.path("assembly").path("plans").size())
                    .put("completeState", result.path("complete").path("state").asText())
                    .put("completePlans", result.path("complete").path("plans").size()));
        }
        var preferenceResults = mapper.createArrayNode();
        for (String preference : List.of("VALUE", "COMFORT")) {
            ObjectNode demand = BoardCalculationTest.demand(json).put("preference", preference).put("budgetCents", 12000);
            JsonNode result = ok("scenario 2 real HTTP synthetic preference " + preference,
                    HttpMethod.POST, "/api/skate/board-assistant/recommend", demand, null);
            JsonNode first = result.path("complete").path("plans").get(0);
            String expected = preference.equals("COMFORT") ? "SYNTHETIC-V-COMFORT" : "SYNTHETIC-V";
            assertThat(first.path("selection").path("items").get(0).path("variantKey").asText()).isEqualTo(expected);
            assertThat(first.path("checks").toString()).contains("SYNTHETIC sourced preference fact");
            preferenceResults.add(mapper.createObjectNode().put("preference", preference).put("firstVariant", expected)
                    .put("supportedBySyntheticAdvice", true));
        }
        evidence.set("purposeBudgetHttpSamples", samples);
        evidence.set("preferenceHttpResults", preferenceResults);
        evidence.put("purposePreferenceFixtures", "SYNTHETIC only; template purposes and usage advice are explicit test data, not real-world guidance");
        importSyntheticCatalog(dataset.deepCopy(), "t07-purpose-preference-restored");
    }

    private ObjectNode linkedAdjustmentFixture() {
        ObjectNode next = dataset.deepCopy();
        ObjectNode wheelRule = BoardTestData.find(next, "rules", "SYN-R-WHEEL_TRUCK_RISER");
        ((ObjectNode) wheelRule.path("conditions")).put("riserMode", "REQUIRED").put("riserMinMm", 3);

        ObjectNode product = BoardTestData.find(next, "products", "SYN-P-WHEEL").deepCopy();
        product.put("key", "SYN-P-WHEEL-ALT").put("sourceRecordId", "SYN-P-WHEEL-ALT").put("model", "SYNTHETIC alternate wheel");
        ObjectNode variant = BoardTestData.find(next, "variants", "SYN-V-WHEEL").deepCopy();
        variant.put("key", "SYN-V-WHEEL-ALT").put("productKey", "SYN-P-WHEEL-ALT").put("label", "SYNTHETIC WHEEL ALT");
        ObjectNode quote = BoardTestData.find(next, "quotes", "SYN-Q-WHEEL").deepCopy();
        quote.put("key", "SYN-Q-WHEEL-ALT").put("sourceProductKey", "SYN-P-WHEEL-ALT")
                .put("variantKey", "SYN-V-WHEEL-ALT").put("amountCents", 151).put("displayPrice", "SYNTHETIC alternate wheel");
        ObjectNode mapping = BoardTestData.find(next, "mapping", "SYN-P-WHEEL").deepCopy();
        mapping.put("sourceRecordId", "SYN-P-WHEEL-ALT").put("productKey", "SYN-P-WHEEL-ALT");
        mapping.set("variantKeys", mapper.createArrayNode().add("SYN-V-WHEEL-ALT"));
        mapping.set("quoteKeys", mapper.createArrayNode().add("SYN-Q-WHEEL-ALT"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("products")).add(product);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("variants")).add(variant);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("quotes")).add(quote);
        ((com.fasterxml.jackson.databind.node.ArrayNode) next.path("mapping")).add(mapping);
        ((com.fasterxml.jackson.databind.node.ArrayNode) BoardTestData.find(next, "templates", "SYN-T")
                .path("selectionConditions").path("eligibleVariantKeys")).add("SYN-V-WHEEL-ALT");
        return next;
    }

    private void verifyFailureBoundaryHttp() throws Exception {
        ObjectNode changed = dataset.deepCopy();
        ((ObjectNode) BoardTestData.find(changed, "variants", "SYN-V-DECK").path("specs")).put("widthInch", 10);
        importSyntheticCatalog(changed, "t07-incompatible");
        JsonNode incompatible = ok("scenario 5 incompatible dimensions", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(incompatible.path("validationState").asText()).isEqualTo("INCOMPATIBLE");

        changed = dataset.deepCopy();
        BoardTestData.find(changed, "quotes", "SYN-Q-DECK").put("active", false);
        importSyntheticCatalog(changed, "t07-missing-price");
        JsonNode missingPrice = ok("scenario 5 missing price", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(missingPrice.path("totalCents").isNull()).isTrue();
        assertThat(missingPrice.path("missing").toString()).contains("DECK:VALID_QUOTE_REQUIRED");

        changed = dataset.deepCopy();
        BoardTestData.find(changed, "quotes", "SYN-Q-DECK").put("validUntil", java.time.Instant.now().plusSeconds(5).toString());
        importSyntheticCatalog(changed, "t07-expired-quote");
        try { TimeUnit.MILLISECONDS.sleep(5200); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        JsonNode expired = ok("scenario 5 expired price", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(expired.path("totalCents").isNull()).isTrue();
        assertThat(expired.path("missing").toString()).contains("DECK:VALID_QUOTE_REQUIRED");

        changed = dataset.deepCopy();
        importSyntheticCatalog(changed, "t07-missing-spec-base");
        var wheelRow = variants.findByKey("SYN-V-WHEEL").orElseThrow();
        ObjectNode wheelContent = (ObjectNode) mapper.readTree(wheelRow.getContentJson());
        ((ObjectNode) wheelContent.path("specs")).putNull("diameterMm");
        wheelRow.setContentJson(wheelContent.toString());
        variants.saveAndFlush(wheelRow);
        BoardTestData.find(lastImportedDataset, "variants", "SYN-V-WHEEL")
                .set("specs", wheelContent.path("specs").deepCopy());
        JsonNode missingSpec = ok("scenario 5 missing wheel specification", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(missingSpec.path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(missingSpec.path("checks").toString()).contains("INSUFFICIENT_DATA");

        changed = dataset.deepCopy();
        importSyntheticCatalog(changed, "t07-missing-rule-base");
        var wheelRuleRow = rules.findByKey("SYN-R-WHEEL_TRUCK_RISER").orElseThrow();
        ObjectNode wheelRuleContent = (ObjectNode) mapper.readTree(wheelRuleRow.getContentJson());
        wheelRuleContent.put("active", false);
        wheelRuleRow.setActive(false);
        wheelRuleRow.setContentJson(wheelRuleContent.toString());
        rules.saveAndFlush(wheelRuleRow);
        BoardTestData.find(lastImportedDataset, "rules", "SYN-R-WHEEL_TRUCK_RISER").put("active", false);
        JsonNode missingRule = ok("scenario 5 missing fit rule", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(missingRule.path("validationState").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(missingRule.path("checks").toString()).contains("缺少适用规则或输入范围依据");

        changed = dataset.deepCopy();
        importSyntheticCatalog(changed, "t07-delisted-variant-base");
        deactivateVariantFixture("SYN-V-DECK");
        JsonNode delisted = ok("scenario 5 delisted variant", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", input(), null);
        assertThat(delisted.path("missing").toString()).contains("DECK:INACTIVE_OR_UNVERIFIED");

        deactivateVariantFixture("SYN-V-DECK-ALT");
        ObjectNode request = mapper.createObjectNode().set("demand", BoardCalculationTest.demand(json));
        request.set("selection", BoardCalculationTest.selection(json));
        request.put("category", "DECK").put("page", 1).put("size", 20);
        JsonNode noCandidate = ok("scenario 5 no replacement candidates", HttpMethod.POST,
                "/api/skate/board-assistant/replacements", request, null);
        assertThat(noCandidate.path("total").asInt()).isZero();

        evidence.set("failureBoundaryHttp", mapper.valueToTree(List.of(
                Map.of("case", "incompatible dimensions", "state", incompatible.path("validationState").asText(), "missing", incompatible.path("missing")),
                Map.of("case", "inactive quote", "state", missingPrice.path("validationState").asText(), "missing", missingPrice.path("missing")),
                Map.of("case", "expired quote", "state", expired.path("validationState").asText(), "missing", expired.path("missing")),
                Map.of("case", "missing wheel spec", "state", missingSpec.path("validationState").asText(), "checks", missingSpec.path("checks")),
                Map.of("case", "inactive fit rule", "state", missingRule.path("validationState").asText(), "checks", missingRule.path("checks")),
                Map.of("case", "delisted selected variant", "state", delisted.path("validationState").asText(), "missing", delisted.path("missing")),
                Map.of("case", "no replacement candidate", "total", noCandidate.path("total").asInt(), "excludedCounts", noCandidate.path("excludedCounts")))));
        importSyntheticCatalog(dataset.deepCopy(), "t07-failure-boundaries-restored");
    }

    @Test
    void t07RealHttpSecurityH2AdminAndBrowserFlow() throws Exception {
        evidence = mapper.createObjectNode()
                .put("evidenceKind", "T07_LOCAL_REAL_HTTP_H2_SYNTHETIC_FIXTURES")
                .put("bindAddress", "127.0.0.1")
                .put("flywayCurrent", flyway.info().current().getVersion().toString())
                .put("flywayBaseline", "8 (synthetic H2 prerequisite; V2-V7 not claimed as executed)")
                .put("hibernateDdlMode", "none for full app HTTP bootstrap; board-only H2 suites separately use validate")
                .put("captchaBoundary", "StringRedisTemplate is mocked; login endpoint, password hash comparison, JWT issue/verification, JWT filter, Spring Security, DB/repositories, and board controllers are real")
                .put("testOnlyLegacyDdlFixtures", "Minimal place_id/activity sign confirmation columns and tb_product_event for route smoke; explicit H2 setup only, not Flyway V2-V7 or MySQL evidence");

        owner = newUser("t07_user", "13900007001");
        other = newUser("t07_other", "13900007002");
        admin = new Admin();
        admin.setAccount("t07_admin");
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRealName("T07管理员");
        admin.setRole("1");
        admin = admins.saveAndFlush(admin);

        ownerToken = login(owner.getUsername(), USER_PASSWORD, "username", "user");
        otherToken = login(other.getUsername(), USER_PASSWORD, "username", "user");
        adminToken = login(admin.getAccount(), ADMIN_PASSWORD, "username", "admin");
        assertThat(ownerToken).isNotBlank();
        assertThat(otherToken).isNotBlank();
        assertThat(adminToken).isNotBlank();
        evidence.put("authChain", "HTTP login returned server-issued JWTs for synthetic user/admin rows; no JWT was injected into the backend");

        JsonNode normalized = BoardTestData.real(json);
        var normalizedImport = catalog.importDataset(normalized, catalog.catalogVersion(), admin.getAdminId());
        assertThat(normalizedImport.errors()).as("normalized project dataset imports into isolated H2").isEmpty();
        JsonNode normalizedRecommendation = ok("real normalized catalog has no fabricated complete assembly total",
                HttpMethod.POST, "/api/skate/board-assistant/recommend",
                BoardCalculationTest.demand(json).put("budgetCents", 100000), null);
        JsonNode normalizedAssembly = normalizedRecommendation.path("assembly");
        assertThat(normalizedAssembly.path("state").asText()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(normalizedAssembly.path("plans")).isEmpty();
        assertThat(normalizedAssembly.path("minimumFeasiblePlan").isMissingNode()
                || normalizedAssembly.path("minimumFeasiblePlan").isNull()).isTrue();
        evidence.put("normalizedDatasetImported", true)
                .put("normalizedAssemblyState", normalizedAssembly.path("state").asText())
                .put("normalizedAssemblyPlanCount", normalizedAssembly.path("plans").size())
                .put("normalizedAssemblyShowsNoPriceTotal", true);

        seedCatalog();

        verifyPurposePreferenceHttp();
        verifyFailureBoundaryHttp();

        JsonNode equipment = ok("legacy equipment entry", HttpMethod.GET, "/api/skate/equipments", null, null);
        evidence.put("legacyEquipmentRows", equipment.isArray() ? equipment.size() : equipment.path("list").size());

        JsonNode catalogPage = ok("public catalog", HttpMethod.GET,
                "/api/skate/board-assistant/catalog?category=COMPLETE&page=1&size=20", null, null);
        evidence.put("syntheticCompleteCatalogRows", catalogPage.path("total").asInt(-1));

        ObjectNode demand = BoardCalculationTest.demand(json).put("budgetCents", 2000);
        JsonNode recommendation = ok("scenario 1/3/6 recommendation", HttpMethod.POST,
                "/api/skate/board-assistant/recommend", demand, null);
        assertThat(recommendation.path("assembly").path("plans").get(0).path("totalCents").asLong()).isEqualTo(1747);
        assertThat(recommendation.path("complete").path("state").asText()).isEqualTo("BUDGET_INSUFFICIENT");
        evidence.put("syntheticAssemblyTotalCents", 1747).put("lowerBudgetCompleteState", "BUDGET_INSUFFICIENT");
        assertThat(recommendation.toString()).doesNotContain("evidenceRef", "quoteSnapshots", "rawPrice");

        // Change only the budget and use the response to establish that higher budget does not remove the lower-cost assembly.
        ObjectNode larger = demand.deepCopy();
        larger.put("budgetCents", 11000);
        JsonNode increasedBudget = ok("scenario 3 higher budget", HttpMethod.POST,
                "/api/skate/board-assistant/recommend", larger, null);
        assertThat(increasedBudget.path("complete").path("plans").size()).isPositive();
        assertThat(increasedBudget.path("assembly").path("plans").get(0).path("totalCents").asLong()).isEqualTo(1747);
        evidence.put("higherBudgetAddsCompleteOptions", true).put("assemblyRemainsLowerCost", true);

        ObjectNode evaluationInput = input();
        JsonNode evaluation = ok("scenario 4 evaluate", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", evaluationInput, null);
        assertThat(evaluation.path("validationState").asText()).isEqualTo("VERIFIED");
        ObjectNode replacementRequest = mapper.createObjectNode();
        replacementRequest.set("demand", evaluationInput.path("demand").deepCopy());
        replacementRequest.set("selection", evaluationInput.path("selection").deepCopy());
        replacementRequest.put("category", "DECK").put("page", 1).put("size", 10);
        JsonNode replacements = ok("scenario 4 replacement candidates", HttpMethod.POST,
                "/api/skate/board-assistant/replacements", replacementRequest, null);
        assertThat(replacements.path("list").toString()).contains("SYN-V-DECK-ALT");
        evidence.put("replacementCandidates", replacements.path("total").asInt(-1))
                .put("actualReplacementCandidateKey", "SYN-V-DECK-ALT")
                .put("actualReplacementCandidateIsSynthetic", true);

        ObjectNode compare = mapper.createObjectNode();
        ObjectNode completeSelection = mapper.createObjectNode().put("kind", "COMPLETE");
        completeSelection.set("items", mapper.createArrayNode().add(mapper.createObjectNode()
                .put("category", "COMPLETE").put("variantKey", "SYNTHETIC-V")));
        ObjectNode completeInput = mapper.createObjectNode();
        completeInput.set("demand", evaluationInput.path("demand").deepCopy());
        completeInput.set("selection", completeSelection);
        compare.set("plans", mapper.createArrayNode().add(completeInput).add(evaluationInput.deepCopy()));
        JsonNode compared = ok("scenario 6 cross-category compare", HttpMethod.POST,
                "/api/skate/board-assistant/compare", compare, null);
        assertThat(compared.path("evaluations").size()).isEqualTo(2);
        assertThat(compared.path("evaluations").get(0).path("kind").asText()).isEqualTo("COMPLETE");
        assertThat(compared.path("evaluations").get(1).path("kind").asText()).isEqualTo("ASSEMBLY");
        evidence.put("crossCategoryCompareKinds", mapper.valueToTree(List.of("COMPLETE", "ASSEMBLY")));
        compare.withArray("plans").add(evaluationInput.deepCopy()).add(evaluationInput.deepCopy());
        ResponseEntity<JsonNode> fourCompare = request(HttpMethod.POST,
                "/api/skate/board-assistant/compare", compare, null);
        record("scenario 6 rejects fourth comparison", "POST", "/api/skate/board-assistant/compare", fourCompare);
        assertThat(fourCompare.getStatusCode().is4xxClientError()
                || fourCompare.getBody().path("code").asInt() != 200).isTrue();

        ObjectNode forged = evaluationInput.deepCopy().put("totalCents", 1).put("validationState", "VERIFIED");
        ResponseEntity<JsonNode> forgedResult = request(HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", forged, null);
        record("scenario 8 rejects forged calculation", "POST", "/api/skate/board-assistant/evaluate", forgedResult);
        assertThat(forgedResult.getStatusCode().is4xxClientError()
                || forgedResult.getBody().path("code").asInt() != 200).isTrue();

        ResponseEntity<JsonNode> anonymousPlans = request(HttpMethod.GET, "/api/user/board-plans", null, null);
        record("scenario 7 anonymous plans forbidden", "GET", "/api/user/board-plans", anonymousPlans);
        assertThat(anonymousPlans.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode ownerPlanList = ok("scenario 7 owner plan list", HttpMethod.GET,
                "/api/user/board-plans?page=1&size=20", null, ownerToken);
        assertThat(ownerPlanList.path("total").asInt()).isZero();

        ObjectNode saveBody = evaluationInput.deepCopy()
                .put("name", "T07合成HTTP方案")
                .put("note", "SYNTHETIC fixture; not a real product recommendation")
                .put("expectedCalculationVersion", evaluation.path("calculationVersion").asText());
        JsonNode saved = ok("scenario 7/8/9 save", HttpMethod.POST,
                "/api/user/board-plans", saveBody, ownerToken);
        String planKey = saved.path("key").asText();
        assertThat(planKey).isNotBlank();
        ResponseEntity<JsonNode> foreignPlan = request(HttpMethod.GET,
                "/api/user/board-plans/" + planKey, null, otherToken);
        record("scenario 7 other user's snapshot hidden", "GET", "/api/user/board-plans/" + planKey, foreignPlan);
        assertThat(foreignPlan.getStatusCode().value()).isEqualTo(404);

        ObjectNode revised = dataset.deepCopy().put("datasetVersion", "t07-synthetic-price-v2");
        BoardTestData.find(revised, "quotes", "SYN-Q-DECK").put("version", 2).put("amountCents", 201);
        importSyntheticCatalog(revised, "t07-synthetic-price-v2");
        JsonNode afterPriceChange = ok("scenario 8/9 history vs latest", HttpMethod.GET,
                "/api/user/board-plans/" + planKey, null, ownerToken);
        assertThat(afterPriceChange.path("snapshot").path("totalCents").asLong()).isEqualTo(1747);
        assertThat(afterPriceChange.path("latestEvaluation").path("totalCents").asLong()).isEqualTo(1847);
        evidence.put("snapshotCentsBeforeCatalogChange", 1747).put("latestEvaluationCentsAfterChange", 1847);

        int plansBeforeStaleSave = Math.toIntExact(snapshots.count());
        ResponseEntity<JsonNode> staleSave = request(HttpMethod.POST, "/api/user/board-plans", saveBody, ownerToken);
        record("scenario 8 stale price token is rejected without persistence", "POST", "/api/user/board-plans", staleSave);
        assertThat(staleSave.getBody().path("data").path("errorCode").asText()).isEqualTo("PRICE_CHANGED");
        assertThat(snapshots.count()).isEqualTo(plansBeforeStaleSave);
        JsonNode freshEvaluation = ok("scenario 8 recompute after price change", HttpMethod.POST,
                "/api/skate/board-assistant/evaluate", evaluationInput, null);
        assertThat(freshEvaluation.path("totalCents").asLong()).isEqualTo(1847);
        ObjectNode confirmedSave = evaluationInput.deepCopy().put("name", "T07价格变化后重新确认")
                .put("note", "SYNTHETIC fixture; recomputed after PRICE_CHANGED")
                .put("expectedCalculationVersion", freshEvaluation.path("calculationVersion").asText());
        JsonNode confirmedPlan = ok("scenario 8 recomputed token saves on explicit reconfirmation", HttpMethod.POST,
                "/api/user/board-plans", confirmedSave, ownerToken);
        String confirmedPlanKey = confirmedPlan.path("key").asText();
        assertThat(confirmedPlan.path("totalCents").asLong()).isEqualTo(1847);
        evidence.put("staleSaveErrorCode", "PRICE_CHANGED")
                .put("staleSaveDatabaseRowsUnchanged", true)
                .put("recomputedPlanSavedCents", 1847);

        JsonNode copied = ok("scenario 9 copy snapshot", HttpMethod.POST,
                "/api/user/board-plans/" + confirmedPlanKey + "/copy", mapper.createObjectNode().put("name", "T07历史副本"), ownerToken);
        String copyKey = copied.path("key").asText();
        assertThat(copyKey).isNotEqualTo(confirmedPlanKey);
        ok("scenario 9 delete own original", HttpMethod.DELETE, "/api/user/board-plans/" + confirmedPlanKey, null, ownerToken);
        JsonNode initialSnapshot = ok("scenario 9 original pre-change snapshot remains", HttpMethod.GET,
                "/api/user/board-plans/" + planKey, null, ownerToken);
        assertThat(initialSnapshot.path("snapshot").path("totalCents").asLong()).isEqualTo(1747);
        JsonNode copyDetail = ok("scenario 9 copied history survives", HttpMethod.GET,
                "/api/user/board-plans/" + copyKey, null, ownerToken);
        assertThat(copyDetail.path("snapshot").path("totalCents").asLong()).isEqualTo(1847);

        ResponseEntity<JsonNode> userAdmin = request(HttpMethod.GET,
                "/api/admin/board-assistant/products?page=1&size=20", null, ownerToken);
        record("admin route rejects user role", "GET", "/api/admin/board-assistant/products", userAdmin);
        assertThat(userAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        int adminResources = 0;
        for (String resource : List.of("products", "variants", "quotes", "rules", "templates")) {
            ok("admin list " + resource, HttpMethod.GET,
                    "/api/admin/board-assistant/" + resource + "?page=1&size=20", null, adminToken);
            adminResources++;
        }
        ok("admin trial", HttpMethod.POST, "/api/admin/board-assistant/trial", demand, adminToken);
        evidence.put("adminResourceListsPassed", adminResources).put("adminTrialPassed", true);

        ObjectNode browserBaseline = dataset.deepCopy().put("datasetVersion", "t07-synthetic-browser-v3");
        BoardTestData.find(browserBaseline, "quotes", "SYN-Q-DECK").put("version", 3).put("amountCents", 101);
        importSyntheticCatalog(browserBaseline, "t07-synthetic-browser-v3");
        evidence.put("browserFixturePriceRestoredCents", 1747);
        if (Boolean.getBoolean("board.browser-tests")) runEdgeBrowser();
        importSyntheticCatalog(linkedAdjustmentFixture(), "t07-linked-wheel-adjustment");
        ObjectNode linkedRequest = mapper.createObjectNode();
        linkedRequest.set("demand", BoardCalculationTest.demand(json));
        linkedRequest.set("selection", BoardCalculationTest.selection(json));
        linkedRequest.put("category", "WHEEL").put("page", 1).put("size", 10);
        JsonNode linkedReplacementRequest = ok("scenario 4 actual HTTP wheel replacement with linked riser proposal",
                HttpMethod.POST, "/api/skate/board-assistant/replacements", linkedRequest, null);
        JsonNode linkedCandidate = null;
        for (JsonNode candidate : linkedReplacementRequest.path("list")) {
            if (candidate.path("selection").toString().contains("SYN-V-WHEEL-ALT")) linkedCandidate = candidate;
        }
        assertThat(linkedCandidate).as("alternate wheel replacement candidate is returned by real HTTP").isNotNull();
        assertThat(linkedCandidate.path("requiresConfirmation").asBoolean()).isTrue();
        assertThat(linkedCandidate.path("proposedAdjustments").toString()).contains("SYN-V-RISER");
        ObjectNode linkedConfirmation = mapper.createObjectNode();
        linkedConfirmation.set("demand", BoardCalculationTest.demand(json));
        linkedConfirmation.set("selection", linkedCandidate.path("proposedAdjustments").get(0).path("selection").deepCopy());
        JsonNode linkedHttpRecalculation = ok("scenario 4 fresh HTTP recalculation of explicitly confirmed linked selection",
                HttpMethod.POST, "/api/skate/board-assistant/evaluate", linkedConfirmation, null);
        assertThat(linkedHttpRecalculation.path("validationState").asText()).isEqualTo("VERIFIED");
        evidence.put("wheelReplacementRequiresRiserConfirmation", true)
                .put("wheelReplacementCandidate", "SYN-V-WHEEL-ALT")
                .put("linkedProposalIncludesRiser", true)
                .put("freshHttpRecalculationWithoutStaleCandidateToken", linkedHttpRecalculation.path("validationState").asText());
        if (Boolean.getBoolean("board.browser-tests")) runEdgeLinkedAdjustmentBrowser();
        evidence.set("httpObservations", mapper.valueToTree(observations));
        evidence.put("h2UserRows", jdbc.queryForObject("select count(*) from tb_user", Integer.class));
        evidence.put("h2PlanRowsAfterCopy", snapshots.count());
        evidence.put("externalServices", "none; no CDB, Redis, AI provider, email, Workbench, or SSH connection");
    }

    private void runEdgeBrowser() throws Exception {
        Files.createDirectories(EVIDENCE);
        Path log = EVIDENCE.resolve("T07-edge-playwright.log").toAbsolutePath();
        String cli = "node_modules/@playwright/test/cli.js";
        ProcessBuilder runner = new ProcessBuilder("node", cli, "test", "--config", "playwright.board.config.cjs",
                "e2e/board-t07-real-http.spec.cjs", "--grep", "T07 real browser auth continuation");
        runner.directory(Path.of("frontend").toAbsolutePath().toFile());
        runner.redirectErrorStream(true);
        runner.redirectOutput(log.toFile());
        runner.environment().put("T07_USER_NAME", owner.getUsername());
        runner.environment().put("T07_USER_PASSWORD", USER_PASSWORD);
        runner.environment().put("T07_ADMIN_ACCOUNT", admin.getAccount());
        runner.environment().put("T07_ADMIN_PASSWORD", ADMIN_PASSWORD);
        runner.environment().put("T07_EVIDENCE_DIR", EVIDENCE.toAbsolutePath().toString());
        Process process = runner.start();
        if (!process.waitFor(150, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("T07 Playwright timed out; see " + log);
        }
        assertThat(process.exitValue()).as("T07 real HTTP browser flow log: " + log).isZero();
    }

    private void runEdgeLinkedAdjustmentBrowser() throws Exception {
        Files.createDirectories(EVIDENCE);
        Path log = EVIDENCE.resolve("T07-linked-edge-playwright.log").toAbsolutePath();
        ProcessBuilder runner = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test",
                "--config", "playwright.board.config.cjs", "e2e/board-t07-real-http.spec.cjs",
                "--grep", "T07 linked wheel adjustment");
        runner.directory(Path.of("frontend").toAbsolutePath().toFile());
        runner.redirectErrorStream(true);
        runner.redirectOutput(log.toFile());
        runner.environment().put("T07_USER_NAME", owner.getUsername());
        runner.environment().put("T07_USER_PASSWORD", USER_PASSWORD);
        runner.environment().put("T07_EVIDENCE_DIR", EVIDENCE.toAbsolutePath().toString());
        Process process = runner.start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("T07 linked-adjustment Playwright timed out; see " + log);
        }
        assertThat(process.exitValue()).as("T07 linked-adjustment real HTTP browser log: " + log).isZero();
    }

    @AfterEach
    void writeEvidence() throws Exception {
        if (evidence == null) evidence = mapper.createObjectNode().put("evidenceKind", "T07_LOCAL_REAL_HTTP_H2_SYNTHETIC_FIXTURES");
        evidence.set("httpObservations", mapper.valueToTree(observations));
        Files.createDirectories(EVIDENCE);
        Files.writeString(EVIDENCE.resolve("T07-http-evidence.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence), StandardCharsets.UTF_8);
    }
}
