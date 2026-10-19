package com.skatehub.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class BoardDatasetValidatorTest {
    private final BoardDatasetValidator validator=new BoardDatasetValidator(new ObjectMapper());
    @Test void realDraftsHaveTheSameBaselineAndNoActivation() {
        var report=validator.validate(BoardTestData.real(validator));assertThat(report.valid()).as(report.errors().toString()).isTrue();assertThat(report.gaps().size()).isEqualTo(299);
        assertThat(validator.validate(BoardTestData.synthetic(validator)).valid()).isTrue();
    }
    @Test void readerRejectsDuplicateJsonFieldsOversizeAndTrailingTokens() {
        for(String invalid:List.of("{\"schemaVersion\":1,\"schemaVersion\":2}","{} {}","{\"amountCents\":NaN}"))assertThatThrownBy(()->validator.read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8)))).isInstanceOf(BoardRequestException.class);
        assertThatThrownBy(()->validator.read(new ByteArrayInputStream(new byte[BoardDatasetValidator.MAX_BYTES+1]))).isInstanceOf(BoardRequestException.class);
        var exponent=validator.read(new ByteArrayInputStream("{\"amountCents\":1e309}".getBytes(StandardCharsets.UTF_8)));
        assertThat(validator.validate(exponent).valid()).isFalse();
    }
    record Case(String name,String error,boolean synthetic,Consumer<ObjectNode> mutate) {}
    @TestFactory Stream<DynamicTest> rejectRepresentativeMalformedImports() {
        List<Case> cases=new ArrayList<>();
        Consumer<Case> add=cases::add;
        add.accept(new Case("schema version overflow","SCHEMA_VERSION",false,d->d.put("schemaVersion",4294967297L)));
        add.accept(new Case("oversize TEXT record","RECORD_SIZE_LIMIT",true,d->((ObjectNode)d.path("products").get(0)).put("description","x".repeat(65536))));
        add.accept(new Case("unknown template category","INVALID_TEMPLATE_CATEGORIES",false,d->((ObjectNode)d.path("templates").get(0)).set("requiredCategories",validator.array().add("UNKNOWN"))));
        add.accept(new Case("duplicate key","DUPLICATE_KEY",false,d->((ArrayNode)d.path("products")).add(d.path("products").get(0).deepCopy())));
        add.accept(new Case("dangling product","DANGLING_REFERENCE",false,d->((ObjectNode)d.path("variants").get(0)).put("productKey","MISSING")));
        add.accept(new Case("dangling variant","DANGLING_REFERENCE",false,d->((ObjectNode)d.path("quotes").get(0)).put("variantKey","MISSING")));
        add.accept(new Case("fraction cents","INVALID_CENTS",true,d->quote(d).put("amountCents",100.1)));
        add.accept(new Case("boolean cents","INVALID_CENTS",true,d->quote(d).put("amountCents",true)));
        add.accept(new Case("negative cents","INVALID_CENTS",true,d->quote(d).put("amountCents",-1)));
        add.accept(new Case("Long overflow","INVALID_CENTS",true,d->quote(d).set("amountCents",BigIntegerNode.valueOf(java.math.BigInteger.ONE.shiftLeft(63)))));
        for(String kind:List.of("RANGE","FROM"))add.accept(new Case(kind+" active","ACTIVE_QUOTE_MARKET",true,d->{quote(d).put("priceKind",kind);quote(d).putNull("amountCents");}));
        add.accept(new Case("nonexact numeric amount","INVALID_PRICE_AMOUNT",true,d->quote(d).put("priceKind","RANGE")));
        add.accept(new Case("package missing","ACTIVE_QUOTE_PACKAGE",true,d->quote(d).putNull("packageQuantity")));
        add.accept(new Case("package zero","INVALID_PACKAGE",true,d->quote(d).put("packageQuantity",0)));
        add.accept(new Case("expiry missing","ACTIVE_QUOTE_TIME",true,d->quote(d).putNull("validUntil")));
        add.accept(new Case("expired","ACTIVE_QUOTE_TIME",true,d->quote(d).put("validUntil",Instant.now().minusSeconds(1).toString())));
        add.accept(new Case("timezone missing","INVALID_TIME",true,d->quote(d).put("validUntil","2026-10-13T00:00:00")));
        add.accept(new Case("future collection","ACTIVE_QUOTE_TIME",true,d->quote(d).put("collectedAt",Instant.now().plusSeconds(300).toString())));
        add.accept(new Case("source missing","MISSING_SOURCE",true,d->quote(d).set("sources",validator.array())));
        add.accept(new Case("evidence missing","MISSING_EVIDENCE",true,d->((ObjectNode)quote(d).path("sources").get(0)).putNull("evidenceRef")));
        add.accept(new Case("transcription not evidence","TRANSCRIPTION_NOT_EVIDENCE",true,d->((ObjectNode)quote(d).path("sources").get(0)).put("evidenceRef","equipment.json#/records/0/cn_price")));
        add.accept(new Case("recorded activation","UNVERIFIED_ACTIVE",true,d->quote(d).put("verificationStatus","RECORDED")));
        add.accept(new Case("unknown shipping","ACTIVE_QUOTE_COSTS",true,d->quote(d).putNull("shippingCents")));
        add.accept(new Case("unknown tax","ACTIVE_QUOTE_COSTS",true,d->quote(d).putNull("taxCents")));
        add.accept(new Case("unconfirmed landed","ACTIVE_QUOTE_COSTS",true,d->quote(d).put("landedPriceConfirmed",false)));
        add.accept(new Case("overseas CNY","ACTIVE_QUOTE_MARKET",true,d->quote(d).put("market","OVERSEAS")));
        add.accept(new Case("foreign currency","ACTIVE_QUOTE_MARKET",true,d->quote(d).put("currency","USD")));
        add.accept(new Case("unknown complete fact","COMPLETE_FACT_MISSING",true,d->((ObjectNode)d.path("variants").get(0).path("specs")).putNull("manufacturerComplete")));
        add.accept(new Case("cross-product quote mapping swap","MAPPING_QUOTE_MISMATCH",false,d->{var a=BoardTestData.find(d,"mapping","DECK-001");var b=BoardTestData.find(d,"mapping","DECK-002");var refs=a.get("quoteKeys");a.set("quoteKeys",b.get("quoteKeys"));b.set("quoteKeys",refs);}));
        add.accept(new Case("unbound quote attribution","MAPPING_QUOTE_MISMATCH",false,d->BoardTestData.find(d,"quotes","DECK-003--cn-price").put("sourceProductKey","DECK-001")));
        add.accept(new Case("bound quote attribution","QUOTE_PRODUCT_MISMATCH",false,d->BoardTestData.find(d,"quotes","DECK-001--overseas-price").put("variantKey",d.path("variants").get(1).path("key").asText())));
        add.accept(new Case("quote mapping dropped","MAPPING_QUOTE_COVERAGE",false,d->BoardTestData.find(d,"mapping","DECK-003").set("quoteKeys",validator.array())));
        add.accept(new Case("source pointer mismatch","SOURCE_QUOTE_MISMATCH",false,d->((ObjectNode)BoardTestData.find(d,"quotes","DECK-003--cn-price").path("sources").get(0)).put("evidenceRef","equipment.json#/records/0/cn_price")));
        add.accept(new Case("Mid Standard mismatch","MODEL_MISMATCH",false,d->BoardTestData.find(d,"quotes","TRUCK-001--cn-price").put("variantKey",d.path("variants").findParents("productKey").stream().filter(v->v.path("productKey").asText().equals("TRUCK-001")).findFirst().orElseThrow().path("key").asText())));
        add.accept(new Case("unknown rule type","INVALID_RULE_TYPE",false,d->((ObjectNode)d.path("rules").get(0)).put("type","EVAL")));
        add.accept(new Case("expression rejected","UNKNOWN_RULE_CONDITION",false,d->((ObjectNode)d.path("rules").get(0).path("conditions")).put("script","pass()")));
        add.accept(new Case("template dangling rule","DANGLING_REFERENCE",false,d->((ObjectNode)d.path("templates").get(0).path("selectionConditions")).set("ruleKeys",validator.array().add("MISSING"))));
        add.accept(new Case("template active unverified","UNVERIFIED_ACTIVE",false,d->((ObjectNode)d.path("templates").get(0)).put("active",true)));
        add.accept(new Case("product mapping missing","SOURCE_COVERAGE",false,d->((ArrayNode)d.path("mapping")).remove(0)));
        add.accept(new Case("split variant mapping incomplete","MAPPING_VARIANT_COVERAGE",false,d->BoardTestData.find(d,"mapping","DECK-001").set("variantKeys",validator.array())));
        add.accept(new Case("product cannot borrow another source mapping","SOURCE_COVERAGE",false,d->{var p=BoardTestData.find(d,"products","DECK-002");p.put("sourceRecordId","DECK-001");((ArrayNode)d.path("mapping")).remove(1);}));
        return cases.stream().map(c->DynamicTest.dynamicTest(c.name(),()->{
            ObjectNode data=c.synthetic()?BoardTestData.synthetic(validator):(ObjectNode)BoardTestData.real(validator);c.mutate().accept(data);
            var result=validator.validate(data);assertThat(result.valid()).as(c.name()).isFalse();assertThat(result.errors().stream().map(BoardDtos.Issue::code)).as(result.errors().toString()).contains(c.error());
        }));
    }
    private static ObjectNode quote(ObjectNode data){return (ObjectNode)data.path("quotes").get(0);}
}
