package com.skatehub.board;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.skatehub.board.BoardDtos.*;

/** File/API trust boundary; never executes Python, URLs, or rule expressions. */
@Component
public class BoardDatasetValidator {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final List<String> GROUPS = List.of("products", "variants", "quotes", "rules", "templates");
    public static final Set<String> CATEGORIES = words("DECK TRUCK WHEEL BEARING GRIP HARDWARE RISER COMPLETE");
    private static final Set<String> STATUSES = words("RECORDED VERIFIED INSUFFICIENT");
    private static final Map<String,String> REQUIRED = Map.of(
            "products", "key legacyEquipmentId sourceRecordId category brand model active description suitabilityTags levelTags sources imageSource imageLicenseStatus",
            "variants", "key productKey label active specs rawSpec sources verificationStatus",
            "quotes", "key variantKey sourceProductKey version currency amountCents displayPrice priceKind market purchaseScope packageQuantity packageUnit shippingCents taxCents landedPriceConfirmed collectedAt validUntil verificationStatus active sources",
            "rules", "key version type severity scope conditions outcome explanation sources verificationStatus active",
            "templates", "key version purpose requiredCategories selectionConditions reasonCodes sources verificationStatus active");
    static final Map<String,String> SPECS = Map.of(
            "DECK", "widthInch lengthInch wheelbaseInch", "TRUCK", "sizeCode construction axleWidthInch heightMm deckMinInch deckMaxInch",
            "WHEEL", "diameterMm hardnessValue hardnessScale widthMm contactPatchMm shape", "BEARING", "standard",
            "GRIP", "widthInch lengthInch", "HARDWARE", "lengthMm headType nutsIncluded", "RISER", "heightMm",
            "COMPLETE", "deckWidthInch manufacturerComplete");
    private static final Map<String,String> CONDITIONS = Map.of(
            "DECK_TRUCK_FIT", "deckMinInch deckMaxInch minInclusive maxInclusive",
            "WHEEL_TRUCK_RISER", "wheelMinMm wheelMaxMm minInclusive maxInclusive riserMode riserMinMm",
            "RISER_HARDWARE_LENGTH", "riserMinMm riserMaxMm minInclusive maxInclusive hardwareMinMm hardwareMaxMm",
            "GRIP_COVERAGE", "compareWidth compareLength", "COMPLETENESS", "requiredQuantities hardwareUnit riserRequirement",
            "USAGE_ADVICE", "purpose preference variantKeys message");
    private final ObjectMapper json;

    public BoardDatasetValidator(ObjectMapper mapper) {
        json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(100).maxStringLength(MAX_BYTES).build());
    }
    public JsonNode read(InputStream stream) {
        try {
            byte[] body = stream.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) throw new BoardRequestException(400, "导入内容超过1MiB");
            JsonNode node = json.readTree(body);
            if (node == null) throw new BoardRequestException(400, "请求体不能为空");
            return node;
        } catch (BoardRequestException e) { throw e; }
        catch (Exception e) { throw new BoardRequestException(400, "请求体不是合法JSON"); }
    }
    public JsonNode parse(String value) {
        try { return storedNumbers(json.readTree(value)); }
        catch (Exception e) { throw new IllegalStateException("目录存储JSON损坏", e); }
    }
    /** Canonical JSON can use exponent notation for integral values. Only internal storage is normalized;
     * read(InputStream) keeps strict JSON integer checks at the API/file boundary. */
    private JsonNode storedNumbers(JsonNode n){
        if(n.isNumber()&&n.decimalValue().stripTrailingZeros().scale()<=0&&n.canConvertToLong())return LongNode.valueOf(n.decimalValue().longValueExact());
        if(n.isObject()){ObjectNode o=(ObjectNode)n;List<String> names=new ArrayList<>();o.fieldNames().forEachRemaining(names::add);for(String name:names)o.set(name,storedNumbers(o.get(name)));}
        else if(n.isArray()){ArrayNode a=(ArrayNode)n;for(int i=0;i<a.size();i++)a.set(i,storedNumbers(a.get(i)));}return n;
    }
    public String canonical(JsonNode value) { return ordered(value).toString(); }
    private JsonNode ordered(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = json.createObjectNode();
            List<String> keys = new ArrayList<>(); value.fieldNames().forEachRemaining(keys::add); Collections.sort(keys);
            for (String key : keys) result.set(key, ordered(value.get(key)));
            return result;
        }
        if (value.isArray()) { ArrayNode result = json.createArrayNode(); value.forEach(v -> result.add(ordered(v))); return result; }
        if (value.isNumber()) return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
        return value;
    }
    public String hash(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public ObjectNode object() { return json.createObjectNode(); }
    public ArrayNode array() { return json.createArrayNode(); }
    public static Set<String> words(String value) { return new HashSet<>(List.of(value.split(" "))); }
    public static String text(JsonNode node, String field) { return node.path(field).isTextual() ? node.path(field).textValue() : null; }
    public static boolean integer(JsonNode node, long min) { return node != null && node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= min; }
    public static Instant time(JsonNode node) {
        try { return node != null && node.isTextual() && node.textValue().contains("T") ? OffsetDateTime.parse(node.textValue()).toInstant() : null; }
        catch (Exception e) { return null; }
    }
    public static long version(JsonNode node) { return node.has("version") ? node.path("version").longValue() : 1L; }
    public static boolean reference(JsonNode node) { return node!=null && "REFERENCE".equals(text(node,"pricingMode")); }
    public static boolean recordedSources(JsonNode node) {
        if(!node.path("sources").isArray()||node.path("sources").isEmpty())return false;
        for(JsonNode s:node.path("sources"))if(!words("RECORDED VERIFIED").contains(text(s,"verificationStatus")))return false;
        return true;
    }
    public static boolean referenceQuote(JsonNode node) {
        return reference(node)&&node.path("active").asBoolean()&&recordedSources(node)&&words("RECORDED VERIFIED").contains(text(node,"verificationStatus"))&&"EXACT".equals(text(node,"priceKind"))&&"CNY".equals(text(node,"currency"))&&"CN".equals(text(node,"market"))&&integer(node.get("amountCents"),0)&&!node.path("priceAssumptions").isEmpty()&&!"TRUCK-001--cn-price".equals(text(node,"key"));
    }
    public JsonNode versioned(JsonNode input) {
        ObjectNode result = input.deepCopy(); if (!result.has("version")) result.put("version", 1); return result;
    }
    public ValidationReport validate(JsonNode dataset) { return validate(dataset, object()); }

    public ValidationReport validate(JsonNode dataset, JsonNode stored) {
        List<Issue> errors = new ArrayList<>();
        JsonNode gaps = dataset.path("gaps");
        if (!dataset.isObject()) return new ValidationReport(false, List.of(new Issue("$", "INVALID_ROOT", "expected object")), array(), null);
        if (canonical(dataset).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) add(errors, "$", "SIZE_LIMIT", "at most 1MiB");
        require(dataset, "schemaVersion datasetVersion products variants quotes rules templates mapping gaps", "$", errors);
        if (!dataset.path("schemaVersion").isIntegralNumber() || !dataset.path("schemaVersion").canConvertToInt() || dataset.path("schemaVersion").intValue()!=1) add(errors, "$", "SCHEMA_VERSION", "expected integer 1");
        if (!key(text(dataset,"datasetVersion"))) add(errors, "$", "INVALID_VERSION", "datasetVersion");
        Map<String,Map<String,JsonNode>> input = new LinkedHashMap<>(), effective = new LinkedHashMap<>();
        for (String group : GROUPS) {
            Map<String,JsonNode> rows = new LinkedHashMap<>(), merged = new LinkedHashMap<>();
            for (JsonNode old : stored.path(group)) merged.put(text(old,"key"), old);
            JsonNode list = dataset.path(group);
            if (!list.isArray()) add(errors,group,"INVALID_COLLECTION","expected array");
            if ((group.equals("products") && list.size()>1000) || (group.equals("variants") && list.size()>5000)) add(errors,group,"COUNT_LIMIT","import count limit");
            for (JsonNode node : list) {
                String id = text(node,"key");
                if (!node.isObject() || !key(id)) { add(errors,group,"INVALID_KEY","object with stable key required"); continue; }
                if (rows.putIfAbsent(id,node)!=null) add(errors,id,"DUPLICATE_KEY",group);
                merged.put(id,node);
                if (canonical(versioned(node)).getBytes(StandardCharsets.UTF_8).length > 65535) add(errors,id,"RECORD_SIZE_LIMIT","TEXT record exceeds 65535 bytes");
                require(node,REQUIRED.get(group),id,errors);
                if (!node.path("active").isBoolean()) add(errors,id,"INVALID_ACTIVE","boolean required");
                if (node.has("version") && !integer(node.get("version"),1)) add(errors,id,"INVALID_VERSION","positive Long version");
                if (!group.equals("products") && !STATUSES.contains(text(node,"verificationStatus"))) add(errors,id,"INVALID_STATUS","verificationStatus");
                boolean verified = sources(node,id,errors);
                if(node.has("pricingMode")&&!words("REFERENCE EXACT").contains(text(node,"pricingMode")))add(errors,id,"INVALID_PRICING_MODE","REFERENCE/EXACT or omit");
                if(node.has("priceAssumptions"))stringArray(node.get("priceAssumptions"),id+".priceAssumptions",errors);
                if (node.path("active").asBoolean() && (reference(node)? !recordedSources(node)||(!group.equals("products")&&!words("RECORDED VERIFIED").contains(text(node,"verificationStatus"))):!verified || (!group.equals("products") && !"VERIFIED".equals(text(node,"verificationStatus"))))) add(errors,id,"UNVERIFIED_ACTIVE","explicit reference needs recorded evidence; exact needs verified evidence");
            }
            input.put(group,rows); effective.put(group,merged);
        }
        Map<String,JsonNode> products=effective.get("products"), variants=effective.get("variants"), quotes=effective.get("quotes");
        for (var entry : effective.get("products").entrySet()) {
            String id=entry.getKey(); JsonNode p=entry.getValue();
            if (!CATEGORIES.contains(text(p,"category"))) add(errors,id,"INVALID_CATEGORY","category whitelist");
            for (String f : List.of("brand","model","description","sourceRecordId","imageLicenseStatus")) if (!p.path(f).isTextual()) add(errors,id,"INVALID_TEXT",f);
            if (!key(text(p,"sourceRecordId")) || p.path("brand").asText().length()>120 || p.path("model").asText().length()>255) add(errors,id,"TEXT_LIMIT","product fields");
            if (!p.path("legacyEquipmentId").isNull() && !integer(p.get("legacyEquipmentId"),1)) add(errors,id,"INVALID_LEGACY_ID","positive Long or null");
            for (String f : List.of("suitabilityTags","levelTags")) stringArray(p.get(f),id+"."+f,errors);
        }
        for (var entry : variants.entrySet()) {
            String id=entry.getKey();JsonNode v=entry.getValue(), p=products.get(text(v,"productKey"));
            if(p==null) { add(errors,id,"DANGLING_REFERENCE","productKey");continue; }
            JsonNode spec=v.path("specs");String category=text(p,"category");String fields=category==null?"":SPECS.getOrDefault(category,"");
            if(!spec.isObject()) {add(errors,id,"INVALID_SPEC","spec object");continue;}
            require(spec,fields,id+".specs",errors);
            spec.fields().forEachRemaining(f -> {
                JsonNode n=f.getValue();String name=f.getKey();
                if(!n.isNull() && (name.endsWith("Inch")||name.endsWith("Mm")||name.equals("hardnessValue")) && !number(n, false)) add(errors,id,"INVALID_DIMENSION",name);
            });
            if(!spec.path("hardnessScale").isMissingNode() && !spec.path("hardnessScale").isNull() && !words("A B").contains(text(spec,"hardnessScale"))) add(errors,id,"INVALID_HARDNESS_SCALE","A/B");
            for(String f:List.of("nutsIncluded","manufacturerComplete")) if(spec.hasNonNull(f)&&!spec.get(f).isBoolean()) add(errors,id,"INVALID_SPEC_BOOLEAN",f);
            if(v.path("active").asBoolean()&&reference(v)&&!p.path("active").asBoolean())add(errors,id,"ACTIVE_SPEC_INCOMPLETE","active parent required");
            if(v.path("active").asBoolean()&&!reference(v)) {
                String critical="COMPLETE".equals(text(p,"category"))?"manufacturerComplete":fields;
                if(!p.path("active").asBoolean() || Arrays.stream(critical.split(" ")).anyMatch(f -> !spec.hasNonNull(f))) add(errors,id,"ACTIVE_SPEC_INCOMPLETE","active parent and known critical facts");
                if("COMPLETE".equals(text(p,"category"))&&!spec.path("manufacturerComplete").asBoolean()) add(errors,id,"COMPLETE_FACT_MISSING","manufacturer fact");
            }
        }
        for(var entry:quotes.entrySet()) {
            String id=entry.getKey();JsonNode q=entry.getValue();String vk=text(q,"variantKey"),pk=text(q,"sourceProductKey");
            if(!products.containsKey(pk)|| (q.hasNonNull("variantKey")&&!variants.containsKey(vk))) add(errors,id,"DANGLING_REFERENCE","quote product/variant");
            if(vk!=null&&variants.containsKey(vk)&&!Objects.equals(pk,text(variants.get(vk),"productKey"))) add(errors,id,"QUOTE_PRODUCT_MISMATCH","source differs from variant");
            if(id.equals("TRUCK-001--cn-price") && vk!=null) add(errors,id,"MODEL_MISMATCH","139 Mid source is not Standard");
            for(String f:List.of("amountCents","shippingCents","taxCents")) if(q.hasNonNull(f)&&!integer(q.get(f),0)) add(errors,id,"INVALID_CENTS",f);
            if(q.hasNonNull("packageQuantity")&&!integer(q.get("packageQuantity"),1)) add(errors,id,"INVALID_PACKAGE","positive Long");
            String kind=text(q,"priceKind");
            if(!words("EXACT RANGE FROM UNKNOWN").contains(kind)||!words("CN OVERSEAS UNKNOWN").contains(text(q,"market"))) add(errors,id,"INVALID_PRICE_KIND","whitelist");
            if(text(q,"currency")==null||!text(q,"currency").matches("[A-Z]{3}")) add(errors,id,"INVALID_CURRENCY","ISO code");
            if("EXACT".equals(kind)?!integer(q.get("amountCents"),0):q.hasNonNull("amountCents")) add(errors,id,"INVALID_PRICE_AMOUNT","exact integer cents; other kinds null");
            if(!q.path("landedPriceConfirmed").isBoolean()) add(errors,id,"INVALID_LANDED_FLAG","boolean");
            for(String f:List.of("collectedAt","validUntil")) if(q.hasNonNull(f)&&time(q.get(f))==null) add(errors,id,"INVALID_TIME",f);
            Instant collected=time(q.get("collectedAt")), until=time(q.get("validUntil"));
            if(collected!=null&&until!=null&&!until.isAfter(collected)) add(errors,id,"INVALID_TIME_RANGE","expiry follows collection");
            if(q.path("active").asBoolean()&&reference(q)) {
                if(!products.getOrDefault(pk,object()).path("active").asBoolean()||(vk!=null&&!variants.getOrDefault(vk,object()).path("active").asBoolean()))add(errors,id,"ACTIVE_QUOTE_VARIANT","reference needs active product/selected variant");
                if(!"EXACT".equals(kind)||!"CNY".equals(text(q,"currency"))||!"CN".equals(text(q,"market")))add(errors,id,"ACTIVE_QUOTE_MARKET","numeric single CN reference only");
                stringArray(q.get("priceAssumptions"),id+".priceAssumptions",errors);
                if(q.path("priceAssumptions").isEmpty())add(errors,id,"REFERENCE_ASSUMPTIONS_REQUIRED","estimate scope required");
                if(id.equals("TRUCK-001--cn-price"))add(errors,id,"MODEL_MISMATCH","Mid price cannot apply to Standard reference variants");
            }
            if(q.path("active").asBoolean()&&!reference(q)) {
                if(vk==null || !variants.getOrDefault(vk,object()).path("active").asBoolean()) add(errors,id,"ACTIVE_QUOTE_VARIANT","active concrete variant");
                if(!"EXACT".equals(kind)||!"CNY".equals(text(q,"currency"))||!"CN".equals(text(q,"market"))) add(errors,id,"ACTIVE_QUOTE_MARKET","EXACT CNY CN only");
                if(!integer(q.get("packageQuantity"),1)||!words("PIECE BEARING WHEEL TRUCK SHEET BOLT_NUT_PAIR RISER COMPLETE").contains(text(q,"packageUnit"))) add(errors,id,"ACTIVE_QUOTE_PACKAGE","physical package required");
                JsonNode bound=variants.get(vk);if(bound!=null){String category=text(products.getOrDefault(text(bound,"productKey"),object()),"category");String unit=text(q,"packageUnit");String expected="HARDWARE".equals(category)?"BOLT_NUT_PAIR":"GRIP".equals(category)?"SHEET":"DECK".equals(category)?"PIECE":category;if(!Objects.equals(unit,expected)&&!(Objects.equals(unit,"PIECE")&&!Objects.equals(category,"HARDWARE")))add(errors,id,"ACTIVE_QUOTE_PACKAGE","unit must count the selected physical category");}
                if(!q.path("landedPriceConfirmed").asBoolean()||!q.path("purchaseScope").isTextual()||q.path("purchaseScope").asText().isBlank()||!integer(q.get("shippingCents"),0)||!integer(q.get("taxCents"),0)) add(errors,id,"ACTIVE_QUOTE_COSTS","confirmed costs required");
                Instant now=Instant.now();if(collected==null||until==null||collected.isAfter(now)||!until.isAfter(now)) add(errors,id,"ACTIVE_QUOTE_TIME","unexpired timezone validity");
            }
        }
        for(var entry:effective.get("rules").entrySet()) rule(entry.getKey(),entry.getValue(),effective,errors);
        for(var entry:effective.get("templates").entrySet()) template(entry.getKey(),entry.getValue(),effective,errors);
        if(!dataset.path("mapping").isArray()||!gaps.isArray()) add(errors,"$","INVALID_COLLECTION","mapping/gaps arrays");
        Set<String> mapped=new HashSet<>(), mappedProducts=new HashSet<>(), mappedVariants=new HashSet<>();
        for(JsonNode m:dataset.path("mapping")) {
            String rid=text(m,"sourceRecordId"),pk=text(m,"productKey");
            require(m,"sourceRecordId sourcePath productKey variantKeys quoteKeys ruleKeys basis sourceRecordSha256","mapping",errors);
            if(!key(rid)||!mapped.add(rid)) add(errors,"mapping","DUPLICATE_KEY","sourceRecordId");
            mappedProducts.add(pk);
            if(!products.containsKey(pk)||!Objects.equals(rid,text(products.getOrDefault(pk,object()),"sourceRecordId"))) add(errors,rid,"MAPPING_PRODUCT_MISMATCH","source/product identity");
            for(String field:List.of("variantKeys","quoteKeys","ruleKeys")) {
                String group=field.equals("variantKeys")?"variants":field.equals("quoteKeys")?"quotes":"rules";
                refs(m.path(field),effective.get(group),rid,errors);
            }
            for(JsonNode ref:m.path("variantKeys")) {
                JsonNode v=variants.get(ref.asText());if(v!=null&&!Objects.equals(pk,text(v,"productKey"))) add(errors,rid,"MAPPING_PRODUCT_MISMATCH","mapped variant");else if(v!=null)mappedVariants.add(ref.asText());
            }
            for(JsonNode ref:m.path("quoteKeys")) {
                JsonNode q=quotes.get(ref.asText());if(q==null)continue;
                String vk=text(q,"variantKey");
                if(!Objects.equals(pk,text(q,"sourceProductKey"))||(vk!=null&&!Objects.equals(pk,text(variants.getOrDefault(vk,object()),"productKey")))) add(errors,ref.asText(),"MAPPING_QUOTE_MISMATCH","mapped source/variant belongs to another product");
                for(JsonNode s:q.path("sources")) {
                    String ev=text(s,"evidenceRef");String path=text(m,"sourcePath");
                    if(ev!=null&&ev.startsWith("equipment.json#")&&(path==null||!ev.startsWith(path+"/"))) add(errors,ref.asText(),"SOURCE_QUOTE_MISMATCH","transcription pointer disagrees with mapping");
                }
            }
            if(text(m,"sourceRecordSha256")==null||!text(m,"sourceRecordSha256").matches("[a-f0-9]{64}")) add(errors,rid,"INVALID_SOURCE_HASH","canonical record hash required");
        }
        for(var p:input.get("products").values()) if(!mappedProducts.contains(text(p,"key"))) add(errors,text(p,"key"),"SOURCE_COVERAGE","imported product needs its own mapping");
        for(var v:input.get("variants").values()) if(!mappedVariants.contains(text(v,"key"))) add(errors,text(v,"key"),"MAPPING_VARIANT_COVERAGE","imported split variant needs its product mapping");
        for(var q:input.get("quotes").values()) {
            String pk=text(q,"sourceProductKey"), qk=text(q,"key");boolean found=false;
            for(JsonNode m:dataset.path("mapping")) if(Objects.equals(pk,text(m,"productKey")))for(JsonNode ref:m.path("quoteKeys"))if(Objects.equals(qk,ref.asText()))found=true;
            if(!found)add(errors,qk,"MAPPING_QUOTE_COVERAGE","imported quote needs its source product mapping");
        }
        Set<String> gapKeys=new HashSet<>();
        for(JsonNode g:gaps) {
            require(g,"key entityKey code fields note","gaps",errors);
            if(!key(text(g,"key"))||!gapKeys.add(text(g,"key"))) add(errors,"gaps","DUPLICATE_KEY","gap key");
            String entity=text(g,"entityKey");if(!"DATASET".equals(entity)&&effective.values().stream().noneMatch(map->map.containsKey(entity))) add(errors,text(g,"key"),"DANGLING_REFERENCE","gap entity");
            stringArray(g.path("fields"),"gaps.fields",errors);
        }
        return new ValidationReport(errors.isEmpty(),List.copyOf(errors),gaps.isArray()?gaps:array(),null);
    }
    private void rule(String id,JsonNode r,Map<String,Map<String,JsonNode>> data,List<Issue> errors) {
        String type=text(r,"type");JsonNode scope=r.path("scope"),cond=r.path("conditions");
        if(type==null||!CONDITIONS.containsKey(type)||!words("HARD ADVICE").contains(text(r,"severity"))) {add(errors,id,"INVALID_RULE_TYPE","type/severity");return;}
        if(!scope.isObject()||!cond.isObject()) {add(errors,id,"INVALID_RULE_STRUCTURE","objects required");return;}
        allowed(scope,r.path("active").asBoolean()&&!reference(r)?"productKeys variantKeys construction":"kind productKeys variantKeys construction",id,errors);
        allowed(cond,CONDITIONS.get(type),id,errors);
        if(type.equals("USAGE_ADVICE")&&cond.hasNonNull("preference")&&!words("EASY_START COMFORT VALUE UNSURE").contains(text(cond,"preference")))add(errors,id,"INVALID_PREFERENCE","structured preference whitelist");
        for(String f:List.of("productKeys","variantKeys")) if(scope.has(f)) refs(scope.path(f),data.get(f.equals("productKeys")?"products":"variants"),id,errors);
        if(!r.path("active").asBoolean())return;
        for(JsonNode k:scope.path("variantKeys"))if(!data.get("variants").getOrDefault(k.asText(),object()).path("active").asBoolean())add(errors,id,"RULE_DEPENDENCY_INACTIVE","scoped variant");
        for(JsonNode k:scope.path("productKeys"))if(!data.get("products").getOrDefault(k.asText(),object()).path("active").asBoolean())add(errors,id,"RULE_DEPENDENCY_INACTIVE","scoped product");
        if(!Objects.equals(text(r,"severity"),type.equals("USAGE_ADVICE")?"ADVICE":"HARD"))add(errors,id,"INVALID_RULE_SEVERITY","hard checks cannot be advice");
        if(!type.equals("COMPLETENESS")&&(scope.path("productKeys").isEmpty()&&scope.path("variantKeys").isEmpty())) add(errors,id,"RULE_SCOPE_MISSING","cannot apply manufacturer rule globally");
        if(reference(r))return; // Recorded ranges may have unknown endpoints; calculation reports pending facts.
        switch(type) {
            case "DECK_TRUCK_FIT" -> {range(cond,"deckMinInch","deckMaxInch",false,id,errors);endpoints(cond,id,errors);if(!scope.hasNonNull("construction")||scope.path("variantKeys").isEmpty())add(errors,id,"RULE_SCOPE_MISSING","specific truck construction");}
            case "WHEEL_TRUCK_RISER" -> {range(cond,"wheelMinMm","wheelMaxMm",false,id,errors);endpoints(cond,id,errors);if(!words("REQUIRED OPTIONAL NONE").contains(text(cond,"riserMode"))||("REQUIRED".equals(text(cond,"riserMode"))&&!number(cond.path("riserMinMm"),false)))add(errors,id,"RULE_RANGE_INCOMPLETE","riser source conditions");}
            case "RISER_HARDWARE_LENGTH" -> {range(cond,"riserMinMm","riserMaxMm",true,id,errors);range(cond,"hardwareMinMm","hardwareMaxMm",false,id,errors);endpoints(cond,id,errors);}
            case "GRIP_COVERAGE" -> {if(!cond.path("compareWidth").isBoolean()||!cond.path("compareWidth").asBoolean()||!cond.path("compareLength").isBoolean()||!cond.path("compareLength").asBoolean())add(errors,id,"RULE_RANGE_INCOMPLETE","width and length coverage");}
            case "COMPLETENESS" -> {JsonNode q=cond.path("requiredQuantities");Map<String,Integer> quantities=Map.of("DECK",1,"TRUCK",2,"WHEEL",4,"BEARING",8,"GRIP",1,"HARDWARE",8);if(q.size()!=6||quantities.entrySet().stream().anyMatch(e->!integer(q.get(e.getKey()),1)||q.path(e.getKey()).longValue()!=e.getValue())||!"BOLT_NUT_PAIR".equals(text(cond,"hardwareUnit"))||!"SOURCE_DEPENDENT".equals(text(cond,"riserRequirement")))add(errors,id,"COMPLETENESS_INVALID","contract quantities");}
            case "USAGE_ADVICE" -> {if(!words("ACTION CRUISE BALANCED").contains(text(cond,"purpose"))||!cond.path("message").isTextual()||cond.path("variantKeys").isEmpty())add(errors,id,"RULE_RANGE_INCOMPLETE","usage facts");refs(cond.path("variantKeys"),data.get("variants"),id,errors);}
        }
        if(words("DECK_TRUCK_FIT WHEEL_TRUCK_RISER RISER_HARDWARE_LENGTH").contains(type)){
            boolean truck=false;for(JsonNode k:scope.path("variantKeys")){JsonNode v=data.get("variants").get(k.asText());if(v!=null&&"TRUCK".equals(text(data.get("products").getOrDefault(text(v,"productKey"),object()),"category")))truck=true;}
            for(JsonNode k:scope.path("productKeys"))if("TRUCK".equals(text(data.get("products").getOrDefault(k.asText(),object()),"category")))truck=true;
            if(!truck)add(errors,id,"RULE_SCOPE_MISSING","installation rule must explicitly scope a truck");
        }
    }
    private void template(String id,JsonNode t,Map<String,Map<String,JsonNode>> data,List<Issue> errors) {
        if(!words("ACTION_BEGINNER CRUISE_BEGINNER BALANCED_BEGINNER").contains(text(t,"purpose")))add(errors,id,"INVALID_TEMPLATE_PURPOSE","purpose");
        JsonNode cond=t.path("selectionConditions");if(!cond.isObject()){add(errors,id,"INVALID_TEMPLATE_CONDITIONS","object");return;}
        allowed(cond,"kind eligibleVariantKeys ruleKeys sourceScopePending",id,errors);
        refs(cond.path("eligibleVariantKeys"),data.get("variants"),id,errors);refs(cond.path("ruleKeys"),data.get("rules"),id,errors);
        stringArray(t.path("requiredCategories"),id,errors);stringArray(t.path("reasonCodes"),id,errors);
        Set<String> required=new HashSet<>();for(JsonNode category:t.path("requiredCategories"))if(!category.isTextual()||!CATEGORIES.contains(category.asText())||!required.add(category.asText()))add(errors,id,"INVALID_TEMPLATE_CATEGORIES","unique known categories");
        if(t.path("active").asBoolean()) {
            if(!required.equals(words("DECK TRUCK WHEEL BEARING GRIP HARDWARE")))add(errors,id,"INVALID_TEMPLATE_CATEGORIES","exact six required categories");
            if(!"ASSEMBLY".equals(text(cond,"kind"))||!cond.path("sourceScopePending").isBoolean()||!reference(t)&&(cond.path("sourceScopePending").asBoolean()||cond.path("ruleKeys").isEmpty()))add(errors,id,"TEMPLATE_SOURCE_PENDING","resolved assembly conditions or explicit reference pending scope");
            Set<String> categories=new HashSet<>();
            for(JsonNode key:cond.path("eligibleVariantKeys")) {JsonNode v=data.get("variants").get(key.asText());if(v!=null){categories.add(text(data.get("products").getOrDefault(text(v,"productKey"),object()),"category"));if(!v.path("active").asBoolean())add(errors,id,"TEMPLATE_DEPENDENCY_INACTIVE","variant");}}
            for(JsonNode key:cond.path("ruleKeys")) if(!data.get("rules").getOrDefault(key.asText(),object()).path("active").asBoolean())add(errors,id,"TEMPLATE_DEPENDENCY_INACTIVE","rule");
            Set<String> types=new HashSet<>();for(JsonNode key:cond.path("ruleKeys"))types.add(text(data.get("rules").getOrDefault(key.asText(),object()),"type"));if(!reference(t)&&!types.containsAll(words("COMPLETENESS DECK_TRUCK_FIT WHEEL_TRUCK_RISER RISER_HARDWARE_LENGTH GRIP_COVERAGE")))add(errors,id,"TEMPLATE_SOURCE_PENDING","all five hard rule types required");
            if(!categories.containsAll(words("DECK TRUCK WHEEL BEARING GRIP HARDWARE")))add(errors,id,"TEMPLATE_SOURCE_PENDING","all required categories need candidates");
        }
    }
    private boolean sources(JsonNode node,String id,List<Issue> errors) {
        JsonNode sources=node.path("sources");if(!sources.isArray()||sources.isEmpty()){add(errors,id,"MISSING_SOURCE","nonempty sources");return false;}
        boolean verified=true;
        for(JsonNode s:sources) {
            require(s,"url title collectedAt verificationStatus evidenceRef note",id,errors);
            if(!s.isObject()||!STATUSES.contains(text(s,"verificationStatus"))||!s.path("title").isTextual()||s.path("title").asText().isBlank()||!s.path("evidenceRef").isTextual()||s.path("evidenceRef").asText().isBlank())add(errors,id,"MISSING_EVIDENCE","source identity/evidence");
            if(s.hasNonNull("url")&&(text(s,"url")==null||!text(s,"url").matches("https?://[^/\\s]+.*")))add(errors,id,"INVALID_SOURCE_URL","http(s) or null");
            if(s.hasNonNull("collectedAt")&&time(s.get("collectedAt"))==null)add(errors,id,"INVALID_TIME","source time");
            if(s.hasNonNull("collectedDate"))try{LocalDate.parse(s.path("collectedDate").asText());}catch(Exception e){add(errors,id,"INVALID_DATE","YYYY-MM-DD");}
            boolean status="VERIFIED".equals(text(s,"verificationStatus"));verified&=status;
            if(status&&(time(s.get("collectedAt"))==null||s.path("evidenceRef").asText().matches("(?:equipment\\.json|sources\\.md)(?:#.*)?")))add(errors,id,"TRANSCRIPTION_NOT_EVIDENCE","verified field evidence/time needed");
        }
        return verified;
    }
    private static boolean key(String value) {return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}");}
    private static boolean number(JsonNode node,boolean zero) {return node!=null&&node.isNumber()&&(zero?node.decimalValue().signum()>=0:node.decimalValue().signum()>0)&&node.decimalValue().compareTo(BigDecimal.valueOf(100000))<=0&&node.decimalValue().stripTrailingZeros().scale()<=6;}
    private static void range(JsonNode n,String low,String high,boolean zero,String id,List<Issue> errors) {if(!number(n.path(low),zero)||!number(n.path(high),zero)||(n.path(low).isNumber()&&n.path(high).isNumber()&&n.path(low).decimalValue().compareTo(n.path(high).decimalValue())>0))add(errors,id,"RULE_RANGE_INCOMPLETE",low+"/"+high);}
    private static void endpoints(JsonNode n,String id,List<Issue> errors) {if(!n.path("minInclusive").isBoolean()||!n.path("maxInclusive").isBoolean())add(errors,id,"RULE_RANGE_INCOMPLETE","explicit endpoints");}
    private static void add(List<Issue> errors,String path,String code,String detail) {errors.add(new Issue(path,code,detail));}
    private static void require(JsonNode node,String fields,String id,List<Issue> errors) {for(String f:fields.split(" "))if(!f.isBlank()&&!node.has(f))add(errors,id,"MISSING_FIELD",f);}
    private static void stringArray(JsonNode node,String id,List<Issue> errors) {if(node==null||!node.isArray()){add(errors,id,"INVALID_COLLECTION","string array");return;}for(JsonNode n:node)if(!n.isTextual())add(errors,id,"INVALID_TEXT","string array values");}
    private static void refs(JsonNode refs,Map<String,JsonNode> values,String id,List<Issue> errors) {if(!refs.isArray()){add(errors,id,"INVALID_COLLECTION","reference array");return;}Set<String> seen=new HashSet<>();for(JsonNode ref:refs)if(!ref.isTextual()||!values.containsKey(ref.asText()))add(errors,id,"DANGLING_REFERENCE","reference");else if(!seen.add(ref.asText()))add(errors,id,"DUPLICATE_REFERENCE",ref.asText());}
    private static void allowed(JsonNode node,String fields,String id,List<Issue> errors) {Set<String> names=words(fields);node.fieldNames().forEachRemaining(f->{if(!names.contains(f))add(errors,id,"UNKNOWN_RULE_CONDITION",f);});}
}
