package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.skatehub.board.BoardDatasetValidator.*;

/** One calculation path for public requests, snapshots and admin trials. No external calls. */
@Service @RequiredArgsConstructor
public class BoardCalculationService {
    private final BoardCatalogService catalog;
    private final BoardDatasetValidator json;
    static final int MAX_REQUEST_EVALUATIONS=2000;
    // ponytail: retain only 2048 recent quote digests for precise conflict wording; unknown tokens
    // still fail closed as CATALOG_CHANGED after restart/eviction, requiring another confirmation.
    private final Map<String,String> recentPrices=Collections.synchronizedMap(new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>2048;}
    });
    static final List<String> REQUIRED=List.of("DECK","TRUCK","WHEEL","BEARING","GRIP","HARDWARE");
    static final List<String> HARD=List.of("COMPLETENESS","DECK_TRUCK_FIT","WHEEL_TRUCK_RISER","RISER_HARDWARE_LENGTH","GRIP_COVERAGE");
    static final Map<String,Integer> QUANTITY=Map.of("DECK",1,"TRUCK",2,"WHEEL",4,"BEARING",8,"GRIP",1,"HARDWARE",8,"RISER",2,"COMPLETE",1);
    public static void fields(JsonNode n,String allowed) {
        if(n==null||!n.isObject())bad("需要JSON对象");
        Set<String> names=words(allowed);n.fieldNames().forEachRemaining(k->{if(!names.contains(k))bad("未知字段："+k);});
    }
    static void bad(String message){throw new BoardRequestException(400,message);}
    static String key(JsonNode n,String field){String s=text(n,field);if(s==null||!stableKey(s))bad("标识格式错误："+field);return s;}
    public ObjectNode demand(JsonNode n) {
        fields(n,"budgetCents purpose preference recommendationType shoeSize");
        if(!integer(n.get("budgetCents"),1)||n.path("budgetCents").longValue()>1_000_000_000_000L)bad("预算须为正整数分且不超过上限");
        for(var entry:Map.of("purpose","ACTION CRUISE BALANCED","preference","EASY_START COMFORT VALUE UNSURE","recommendationType","COMPLETE ASSEMBLY BOTH").entrySet())if(!words(entry.getValue()).contains(text(n,entry.getKey())))bad("需求枚举错误："+entry.getKey());
        ObjectNode result=n.deepCopy();if(!result.has("shoeSize"))result.putNull("shoeSize");
        if(result.hasNonNull("shoeSize")) {
            JsonNode shoe=result.get("shoeSize");fields(shoe,"value system note");
            if(!shoe.path("value").isNumber()||shoe.path("value").decimalValue().signum()<=0||shoe.path("value").decimalValue().compareTo(new BigDecimal("100"))>0||!words("CN EU US UK").contains(text(shoe,"system"))||(shoe.hasNonNull("note")&&(!shoe.path("note").isTextual()||shoe.path("note").asText().length()>200)))bad("鞋码格式错误");
        }return result;
    }
    ObjectNode selection(JsonNode n) {
        fields(n,"kind templateKey items");String kind=text(n,"kind");if(!words("COMPLETE ASSEMBLY").contains(kind))bad("方案类型错误");
        if(n.hasNonNull("templateKey"))key(n,"templateKey");
        JsonNode items=n.path("items");if(!items.isArray()||items.size()>7)bad("配件列表错误");
        TreeMap<String,String> selected=new TreeMap<>();
        for(JsonNode item:items){fields(item,"category variantKey");String c=text(item,"category");if(!CATEGORIES.contains(c)||selected.put(c,key(item,"variantKey"))!=null)bad("类别错误或重复");if(kind.equals("COMPLETE")?!c.equals("COMPLETE"):c.equals("COMPLETE"))bad("类别与方案类型不符");}
        if(kind.equals("COMPLETE")&&selected.size()!=1)bad("成品方案须含一个成品");
        ObjectNode result=json.object().put("kind",kind);result.set("templateKey",n.path("templateKey").isMissingNode()?NullNode.instance:n.get("templateKey"));ArrayNode normalized=json.array();selected.forEach((c,v)->normalized.add(json.object().put("category",c).put("variantKey",v)));result.set("items",normalized);return result;
    }
    public ObjectNode evaluate(JsonNode input) {
        fields(input,"demand selection expectedCalculationVersion");ObjectNode result=evaluate(demand(input.path("demand")),selection(input.path("selection")),catalog.calculationCatalog());
        if(input.has("expectedCalculationVersion")){versionToken(input,"expectedCalculationVersion");if(!input.path("expectedCalculationVersion").asText().equals(result.path("calculationVersion").asText())){ObjectNode conflict=json.object().put("errorCode",changeCode(input.path("expectedCalculationVersion").asText(),result)).put("requiresConfirmation",true);conflict.set("evaluation",result);return conflict;}}
        return result;
    }
    public static void versionToken(JsonNode n,String field){if(text(n,field)==null||!text(n,field).matches("[a-f0-9]{64}"))bad("版本摘要必填："+field);}
    private Map<String,JsonNode> rows(JsonNode data,String group){Map<String,JsonNode> m=new TreeMap<>();for(JsonNode n:data.path(group))m.put(text(n,"key"),n);return m;}
    private boolean enabled(JsonNode n){if(n==null||!n.path("active").asBoolean())return false;if(reference(n))return recordedSources(n)&&(!n.has("verificationStatus")||words("RECORDED VERIFIED").contains(text(n,"verificationStatus")));if(n.has("verificationStatus")&&!"VERIFIED".equals(text(n,"verificationStatus")))return false;if(n.path("sources").isEmpty())return false;for(JsonNode s:n.path("sources"))if(!"VERIFIED".equals(text(s,"verificationStatus")))return false;return true;}
    private boolean quoteEligible(JsonNode q,String category,Instant now) {
        boolean exactMarket=!reference(q)&&enabled(q)&&"EXACT".equals(text(q,"priceKind"))&&"CN".equals(text(q,"market"))&&"CNY".equals(text(q,"currency"));
        boolean knownCosts=q.path("landedPriceConfirmed").asBoolean()&&integer(q.get("amountCents"),0)&&integer(q.get("shippingCents"),0)&&integer(q.get("taxCents"),0);
        String unit=text(q,"packageUnit");
        String requiredUnit=category.equals("HARDWARE")?"BOLT_NUT_PAIR":category.equals("GRIP")?"SHEET":category;
        boolean physicalPackage=integer(q.get("packageQuantity"),1)&&("PIECE".equals(unit)&&!category.equals("HARDWARE")||Objects.equals(unit,requiredUnit));
        Instant collected=time(q.get("collectedAt")),validUntil=time(q.get("validUntil"));
        boolean current=collected!=null&&!collected.isAfter(now)&&validUntil!=null&&validUntil.isAfter(now);
        return exactMarket&&knownCosts&&physicalPackage&&current;
    }
    ObjectNode evaluate(ObjectNode demand,ObjectNode selection,JsonNode data) {
        Map<String,JsonNode> products=rows(data,"products"),variants=rows(data,"variants"),templates=rows(data,"templates");
        Map<String,JsonNode> picked=new TreeMap<>();ArrayNode missing=json.array(),checks=json.array(),items=json.array(),used=json.array(),quotes=json.array();
        boolean assembly=selection.path("kind").asText().equals("ASSEMBLY"),referenceMode=false;ArrayNode assumptions=json.array();JsonNode template=selection.hasNonNull("templateKey")?templates.get(text(selection,"templateKey")):null;
        if(selection.hasNonNull("templateKey")&&(!enabled(template)||!assembly))bad("模板不可用");
        if(template!=null){used.add(template);referenceMode|=reference(template);if(!Objects.equals(text(template,"purpose"),text(demand,"purpose")+"_BEGINNER"))missing.add("TEMPLATE_PURPOSE_MISMATCH");}
        for(JsonNode ref:selection.path("items")) {
            JsonNode v=variants.get(text(ref,"variantKey"));JsonNode p=v==null?null:products.get(text(v,"productKey"));String c=text(ref,"category");
            if(v==null||p==null||!Objects.equals(c,text(p,"category")))bad("所选规格不存在或类别错误");
            picked.put(c,v);used.add(v);used.add(p);referenceMode|=reference(v)||reference(p);if(!enabled(v)||!enabled(p))missing.add(c+":INACTIVE_OR_UNVERIFIED");
            if(template!=null&&!contains(template.path("selectionConditions").path("eligibleVariantKeys"),text(v,"key")))missing.add(c+":OUTSIDE_TEMPLATE");
        }
        if(assembly)for(String c:REQUIRED)if(!picked.containsKey(c))missing.add(c+":MISSING_COMPONENT");
        List<JsonNode> ruleList=new ArrayList<>();
        if(assembly)for(JsonNode r:data.path("rules"))if(enabled(r)&&scope(r,picked,variants,products)) {
            ruleList.add(r);used.add(r);
        }
        for(JsonNode r:ruleList)referenceMode|=reference(r);ruleList.sort(Comparator.comparing(r->text(r,"key")));
        if(assembly)for(String type:HARD) {
            List<JsonNode> matched=ruleList.stream().filter(r->type.equals(text(r,"type"))).filter(r->covers(r,picked)).toList();
            if(matched.isEmpty()){checks.add(check(null,type,"INSUFFICIENT_DATA","缺少适用规则或输入范围依据"));continue;}
            Set<String> definitions=new HashSet<>();for(JsonNode r:matched)definitions.add(json.canonical(r.path("conditions")));
            if(definitions.size()>1){for(JsonNode r:matched)checks.add(check(r,type,"INSUFFICIENT_DATA","RULE_CONFLICT：匹配规则条件冲突"));missing.add("RULE_CONFLICT:"+type);continue;}
            for(JsonNode r:matched)checks.add(check(r,type,reference(r)?referenceRuleState(r,picked):ruleState(r,picked),r.path("explanation").asText()));
        }
        // Usage advice applies to manufacturer completes too; it never fabricates internal hard checks.
        if(!assembly)for(JsonNode r:data.path("rules"))if(enabled(r)&&"USAGE_ADVICE".equals(text(r,"type"))&&scope(r,picked,variants,products)) {ruleList.add(r);used.add(r);}
        for(JsonNode r:ruleList)referenceMode|=reference(r);
        int purposeScore=0,preferenceScore=0;
        if(referenceMode)for(JsonNode v:picked.values()) {
            JsonNode p=products.get(text(v,"productKey"));String tags=p.path("suitabilityTags").toString(),levels=p.path("levelTags").toString();
            String purpose=text(demand,"purpose");if(purpose.equals("ACTION")&&tags.matches(".*(街式|滑板场|动作).*" )||purpose.equals("CRUISE")&&tags.matches(".*(刷街|巡航|通勤).*" )||purpose.equals("BALANCED")&&tags.contains("综合"))purposeScore++;
            String pref=text(demand,"preference");if(pref.equals("EASY_START")&&(tags.contains("初学")||levels.contains("初学"))||pref.equals("COMFORT")&&tags.contains("舒适")||pref.equals("VALUE")&&tags.contains("性价比"))preferenceScore++;
        }
        for(JsonNode r:ruleList)if("USAGE_ADVICE".equals(text(r,"type"))&&Objects.equals(text(r.path("conditions"),"purpose"),text(demand,"purpose"))&&picked.values().stream().anyMatch(v->contains(r.path("conditions").path("variantKeys"),text(v,"key")))){
            purposeScore++;if(!words("UNSURE VALUE").contains(text(demand,"preference"))&&Objects.equals(text(demand,"preference"),text(r.path("conditions"),"preference")))preferenceScore++;
            checks.add(check(r,"USAGE_ADVICE","ADVICE",r.path("conditions").path("message").asText()));
        }
        long subtotal=0;boolean priceComplete=true;Instant now=Instant.now();
        for(var entry:picked.entrySet()) {
            String c=entry.getKey();JsonNode v=entry.getValue(),p=products.get(text(v,"productKey"));int quantity=QUANTITY.get(c);
            ObjectNode line=json.object().put("category",c).put("variantKey",text(v,"key")).put("productKey",text(v,"productKey")).put("label",v.path("label").asText()).put("requiredQuantity",quantity);line.set("specs",v.path("specs"));line.set("sources",v.path("sources"));
            JsonNode best=null;long cost=Long.MAX_VALUE;
            for(JsonNode q:data.path("quotes"))if((Objects.equals(text(q,"variantKey"),text(v,"key"))||reference(q)&&!q.hasNonNull("variantKey"))&&Objects.equals(text(q,"sourceProductKey"),text(p,"key"))) {
                used.add(q);
                if(quoteEligible(q,c,now)||referenceQuote(q))try{long value=lineCost(q,quantity);if(best==null||value<cost||value==cost&&text(q,"key").compareTo(text(best,"key"))<0){best=q;cost=value;}}catch(ArithmeticException ignored){missing.add(c+":PRICE_OVERFLOW");}
            }
            if(best==null){priceComplete=false;missing.add(c+":VALID_QUOTE_REQUIRED");for(String f:List.of("packageQuantity","packageCount","quoteKey","quoteVersion","amountCents","lineTotalCents","shippingCents","taxCents"))line.putNull(f);}
            else {
                boolean refPrice=reference(best);referenceMode|=refPrice;long pack=best.path("packageQuantity").asLong(0);line.put("packageCount",pack>0?1+(quantity-1)/pack:1).put("quoteKey",text(best,"key")).put("quoteVersion",version(best)).put("lineTotalCents",cost);
                line.put("pricingMode",refPrice?"REFERENCE":"EXACT");for(String f:List.of("packageQuantity","amountCents","shippingCents","taxCents"))line.set(f,best.path(f));if(refPrice){for(JsonNode a:best.path("priceAssumptions"))assumptions.add(text(v,"key")+": "+a.asText());line.set("priceAssumptions",best.path("priceAssumptions"));}line.set("quoteSources",best.path("sources"));quotes.add(best);
                try{subtotal=Math.addExact(subtotal,cost);}catch(ArithmeticException e){bad("金额累计超出整数上限");}
            }items.add(line);
        }
        if(assembly&&!picked.keySet().containsAll(REQUIRED))priceComplete=false;
        boolean incompatible=false,insufficient=!missing.isEmpty();for(JsonNode c:checks){incompatible|="INCOMPATIBLE".equals(text(c,"state"));insufficient|="INSUFFICIENT_DATA".equals(text(c,"state"));}
        if(!assembly&&!referenceMode&&!picked.get("COMPLETE").path("specs").path("manufacturerComplete").asBoolean()){insufficient=true;missing.add("MANUFACTURER_COMPLETE_FACT_REQUIRED");}
        String state=incompatible?"INCOMPATIBLE":insufficient||!priceComplete?"INSUFFICIENT_DATA":referenceMode?"REFERENCE_ESTIMATE":assembly?"VERIFIED":"MANUFACTURER_COMPLETE";
        ObjectNode out=json.object().put("pricingMode",referenceMode?"REFERENCE":"EXACT");out.set("priceAssumptions",assumptions);out.set("selection",selection);out.set("demand",demand);out.put("catalogVersion",data.path("catalogVersion").asText());out.put("kind",text(selection,"kind"));out.set("templateKey",selection.path("templateKey"));if(template==null)out.putNull("templateVersion");else out.put("templateVersion",version(template));
        out.set("items",items);out.set("checks",checks);out.set("missing",missing);out.set("reasons",template==null?json.array():template.path("reasonCodes"));out.put("knownSubtotalCents",subtotal).put("priceComplete",priceComplete).put("validationState",state).put("requiresConfirmation",false);out.set("proposedAdjustments",json.array());
        if(priceComplete){out.put("totalCents",subtotal).put("budgetDeltaCents",demand.path("budgetCents").longValue()-subtotal);}else{out.putNull("totalCents");out.putNull("budgetDeltaCents");}
        ObjectNode fingerprint=json.object();fingerprint.set("demand",demand);fingerprint.set("selection",selection);fingerprint.set("used",used);fingerprint.set("checks",checks);fingerprint.set("items",items);fingerprint.put("algorithmVersion",2);out.put("calculationVersion",json.hash(fingerprint));
        // Saved privately in snapshots; public controller strips this evidence bundle.
        ObjectNode templateVersions=json.object(),ruleVersions=json.object();if(template!=null)templateVersions.put(text(template,"key"),version(template));for(JsonNode r:ruleList)ruleVersions.put(text(r,"key"),version(r));out.set("templateVersions",templateVersions);out.set("ruleVersions",ruleVersions);
        out.put("purposeScore",purposeScore).put("preferenceScore",preferenceScore);out.set("calculationSources",used);out.set("quoteSnapshots",quotes);recentPrices.put(out.path("calculationVersion").asText(),priceDigest(out));return out;
    }
    private String priceDigest(JsonNode e){ObjectNode p=json.object();p.set("quotes",e.path("quoteSnapshots"));p.set("total",e.path("totalCents"));p.put("priceComplete",e.path("priceComplete").asBoolean());return json.hash(p);}
    String changeCode(String expected,JsonNode current){String previous=recentPrices.get(expected);return previous!=null&&!previous.equals(priceDigest(current))?"PRICE_CHANGED":"CATALOG_CHANGED";}
    private static long lineCost(JsonNode q,int quantity){if(reference(q)){long pack=q.path("packageQuantity").asLong(0),count=pack>0?1+(quantity-1)/pack:1;return Math.multiplyExact(count,q.path("amountCents").longValue());}long count=1+(quantity-1)/q.path("packageQuantity").longValue();return Math.addExact(Math.addExact(Math.multiplyExact(count,q.path("amountCents").longValue()),q.path("shippingCents").longValue()),q.path("taxCents").longValue());}
    private static boolean contains(JsonNode list,String s){for(JsonNode n:list)if(n.isTextual()&&Objects.equals(n.asText(),s))return true;return false;}
    private boolean scope(JsonNode r,Map<String,JsonNode> selected,Map<String,JsonNode> variants,Map<String,JsonNode> products) {
        JsonNode s=r.path("scope");if("COMPLETENESS".equals(text(r,"type"))&&s.path("productKeys").isEmpty()&&s.path("variantKeys").isEmpty())return true;
        if(s.path("productKeys").isEmpty()&&s.path("variantKeys").isEmpty())return false;
        // All explicitly scoped components must belong to this combination; alternatives within a category are OR.
        Map<String,Set<String>> scopedVariants=new HashMap<>(),scopedProducts=new HashMap<>();
        for(JsonNode k:s.path("variantKeys")){JsonNode v=variants.get(k.asText());if(v==null)return false;JsonNode p=products.get(text(v,"productKey"));if(p==null)return false;scopedVariants.computeIfAbsent(text(p,"category"),ignored->new HashSet<>()).add(k.asText());}
        if(reference(r))for(var entry:scopedVariants.entrySet()){JsonNode chosen=selected.get(entry.getKey());if(chosen==null||!entry.getValue().contains(text(chosen,"key")))return false;}
        for(JsonNode k:s.path("productKeys")){JsonNode p=products.get(k.asText());if(p==null)return false;scopedProducts.computeIfAbsent(text(p,"category"),ignored->new HashSet<>()).add(k.asText());}
        Set<String> categories=new HashSet<>(scopedVariants.keySet());categories.addAll(scopedProducts.keySet());
        for(String c:categories){JsonNode v=selected.get(c);if(v==null||!scopedVariants.getOrDefault(c,Set.of()).contains(text(v,"key"))&&!scopedProducts.getOrDefault(c,Set.of()).contains(text(v,"productKey")))return false;}
        boolean hit=false;for(JsonNode v:selected.values()) {
            boolean matches=contains(s.path("variantKeys"),text(v,"key"))||contains(s.path("productKeys"),text(v,"productKey"));
            if(matches){hit=true;if(s.hasNonNull("construction")&&v.path("specs").has("construction")&&!Objects.equals(text(s,"construction"),text(v.path("specs"),"construction")))return false;}
        }
        String type=text(r,"type");if(words("DECK_TRUCK_FIT WHEEL_TRUCK_RISER RISER_HARDWARE_LENGTH").contains(type)) {
            JsonNode truck=selected.get("TRUCK");return truck!=null&&(contains(s.path("variantKeys"),text(truck,"key"))||contains(s.path("productKeys"),text(truck,"productKey")))&&(!s.hasNonNull("construction")||Objects.equals(text(s,"construction"),text(truck.path("specs"),"construction")));
        }return hit;
    }
    private BigDecimal spec(Map<String,JsonNode> p,String category,String field){JsonNode n=p.get(category);return n!=null&&n.path("specs").path(field).isNumber()?n.path("specs").path(field).decimalValue():null;}
    private static boolean in(BigDecimal n,JsonNode c,String min,String max){if(n==null||!c.path(min).isNumber()||!c.path(max).isNumber()||!c.path("minInclusive").isBoolean()||!c.path("maxInclusive").isBoolean())return false;int lo=n.compareTo(c.path(min).decimalValue()),hi=n.compareTo(c.path(max).decimalValue());return(lo>0||lo==0&&c.path("minInclusive").asBoolean())&&(hi<0||hi==0&&c.path("maxInclusive").asBoolean());}
    private boolean covers(JsonNode r,Map<String,JsonNode> p){JsonNode c=r.path("conditions");return switch(text(r,"type")){case "WHEEL_TRUCK_RISER" -> in(spec(p,"WHEEL","diameterMm"),c,"wheelMinMm","wheelMaxMm");case "RISER_HARDWARE_LENGTH" -> in(p.containsKey("RISER")?spec(p,"RISER","heightMm"):BigDecimal.ZERO,c,"riserMinMm","riserMaxMm");default -> true;};}
    private String ruleState(JsonNode r,Map<String,JsonNode> p) {
        JsonNode c=r.path("conditions");String type=text(r,"type");
        String numbers=switch(type){case "DECK_TRUCK_FIT"->"deckMinInch deckMaxInch";case "RISER_HARDWARE_LENGTH"->"riserMinMm riserMaxMm hardwareMinMm hardwareMaxMm";case "WHEEL_TRUCK_RISER"->"wheelMinMm wheelMaxMm";default->"";};
        if(!numbers.isEmpty()){for(String f:numbers.split(" "))if(!c.path(f).isNumber())return "INSUFFICIENT_DATA";if(!c.path("minInclusive").isBoolean()||!c.path("maxInclusive").isBoolean())return "INSUFFICIENT_DATA";}
        if(type.equals("GRIP_COVERAGE")&&(!c.path("compareWidth").asBoolean()||!c.path("compareLength").asBoolean()))return "INSUFFICIENT_DATA";
        if(type.equals("COMPLETENESS")){if(!"BOLT_NUT_PAIR".equals(text(c,"hardwareUnit"))||!"SOURCE_DEPENDENT".equals(text(c,"riserRequirement"))||c.path("requiredQuantities").size()!=6)return "INSUFFICIENT_DATA";for(String cat:REQUIRED)if(c.path("requiredQuantities").path(cat).asInt(-1)!=QUANTITY.get(cat))return "INSUFFICIENT_DATA";}
        if(type.equals("WHEEL_TRUCK_RISER")&&(!words("REQUIRED OPTIONAL NONE").contains(text(c,"riserMode"))||"REQUIRED".equals(text(c,"riserMode"))&&!c.path("riserMinMm").isNumber()))return "INSUFFICIENT_DATA";
        switch(type) {
            case "COMPLETENESS":
                if(!p.keySet().containsAll(REQUIRED))return "INSUFFICIENT_DATA";
                JsonNode nuts=p.get("HARDWARE").path("specs").path("nutsIncluded");
                if(!nuts.isBoolean())return "INSUFFICIENT_DATA";
                return nuts.asBoolean()?"PASS":"INCOMPATIBLE";
            case "DECK_TRUCK_FIT":if(spec(p,"DECK","widthInch")==null)return "INSUFFICIENT_DATA";return in(spec(p,"DECK","widthInch"),c,"deckMinInch","deckMaxInch")?"PASS":"INCOMPATIBLE";
            case "WHEEL_TRUCK_RISER":
                if(!"REQUIRED".equals(text(c,"riserMode")))return "PASS";
                BigDecimal height=spec(p,"RISER","heightMm");
                if(height==null)return p.containsKey("RISER")?"INSUFFICIENT_DATA":"INCOMPATIBLE";
                return height.compareTo(c.path("riserMinMm").decimalValue())>=0?"PASS":"INCOMPATIBLE";
            case "RISER_HARDWARE_LENGTH":BigDecimal length=spec(p,"HARDWARE","lengthMm");if(length==null)return "INSUFFICIENT_DATA";return in(length,c,"hardwareMinMm","hardwareMaxMm")?"PASS":"INCOMPATIBLE";
            case "GRIP_COVERAGE":for(String f:List.of("widthInch","lengthInch"))if(spec(p,"GRIP",f)==null||spec(p,"DECK",f)==null)return "INSUFFICIENT_DATA";for(String f:List.of("widthInch","lengthInch"))if(spec(p,"GRIP",f).compareTo(spec(p,"DECK",f))<0)return "INCOMPATIBLE";return "PASS";
            default:return "INSUFFICIENT_DATA";
        }
    }
    private String referenceRuleState(JsonNode r,Map<String,JsonNode> p) {
        JsonNode c=r.path("conditions");String type=text(r,"type");
        if(type.equals("DECK_TRUCK_FIT")) {
            BigDecimal width=spec(p,"DECK","widthInch");
            if(width!=null&&c.path("deckMinInch").isNumber()&&c.path("deckMaxInch").isNumber()&&(width.compareTo(c.path("deckMinInch").decimalValue())<0||width.compareTo(c.path("deckMaxInch").decimalValue())>0))return "INCOMPATIBLE";
        }
        String state=ruleState(r,p);
        return state.equals("PASS")?"RECORDED_COMPATIBLE":state;
    }
    private ObjectNode check(JsonNode r,String type,String state,String explanation){ObjectNode c=json.object().put("type",type).put("state",state).put("explanation",explanation);if(r==null){c.putNull("ruleKey");c.putNull("ruleVersion");c.set("sources",json.array());}else{c.put("ruleKey",text(r,"key")).put("ruleVersion",version(r));c.set("sources",r.path("sources"));}return c;}
    public ObjectNode recommend(JsonNode input){return recommend(demand(input),catalog.calculationCatalog());}
    ObjectNode recommend(ObjectNode d,JsonNode data) {
        ObjectNode result=json.object();result.set("demand",d);result.put("catalogVersion",data.path("catalogVersion").asText());
        int[] count={0};boolean requestTruncated=false;
        for(String kind:List.of("COMPLETE","ASSEMBLY")) {
            List<ObjectNode> evaluated=new ArrayList<>();boolean[] truncated={false};String request=text(d,"recommendationType");
            if(!request.equals("BOTH")&&!request.equals(kind)){ObjectNode skipped=bucket(List.of(),false,d,"NOT_REQUESTED");skipped.put("evaluatedCount",0);result.set(kind.toLowerCase(),skipped);continue;}
            Map<String,List<JsonNode>> candidates=new TreeMap<>();Map<String,JsonNode> products=rows(data,"products");
            for(JsonNode v:rows(data,"variants").values())if(enabled(v)&&enabled(products.get(text(v,"productKey"))))candidates.computeIfAbsent(text(products.get(text(v,"productKey")),"category"),k->new ArrayList<>()).add(v);
            // ponytail: prioritize recorded priced variants within the existing 2000-evaluation ceiling.
            for(List<JsonNode> list:candidates.values())list.sort(Comparator.comparingLong((JsonNode v)->reference(v)?referenceVariantCost(v,data):0).thenComparing(v->text(v,"key")));
            for(var entry:candidates.entrySet())if((kind.equals("COMPLETE")?entry.getKey().equals("COMPLETE"):!entry.getKey().equals("COMPLETE"))&&entry.getValue().size()>200){entry.getValue().subList(200,entry.getValue().size()).clear();truncated[0]=true;}
            if(kind.equals("COMPLETE"))for(JsonNode v:candidates.getOrDefault("COMPLETE",List.of())) {if(count[0]>=MAX_REQUEST_EVALUATIONS){truncated[0]=true;break;}ObjectNode s=json.object().put("kind",kind);s.putNull("templateKey");s.set("items",json.array().add(json.object().put("category",kind).put("variantKey",text(v,"key"))));evaluated.add(evaluate(d,selection(s),data));count[0]++;}
            else for(JsonNode t:rows(data,"templates").values())if(enabled(t)&&Objects.equals(text(t,"purpose"),text(d,"purpose")+"_BEGINNER")) {
                List<List<JsonNode>> dimensions=new ArrayList<>();List<String> cats=new ArrayList<>(REQUIRED);
                for(String c:REQUIRED)dimensions.add(candidates.getOrDefault(c,List.of()).stream().filter(v->contains(t.path("selectionConditions").path("eligibleVariantKeys"),text(v,"key"))).toList());
                List<JsonNode> risers=new ArrayList<>();risers.add(NullNode.instance);for(JsonNode v:candidates.getOrDefault("RISER",List.of()))if(contains(t.path("selectionConditions").path("eligibleVariantKeys"),text(v,"key")))risers.add(v);cats.add("RISER");dimensions.add(risers);
                if(dimensions.stream().anyMatch(List::isEmpty))continue;
                boolean[] templateTruncated={false};
                enumerate(0,cats,dimensions,json.array(),t,d,data,evaluated,count,templateTruncated);
                if(templateTruncated[0]){truncated[0]=true;break;}
            }
            boolean hasData=kind.equals("COMPLETE")?products.values().stream().anyMatch(p->"COMPLETE".equals(text(p,"category"))):data.path("templates").size()>0;
            ObjectNode current=bucket(evaluated,truncated[0],d,evaluated.isEmpty()?hasData?"DATA_INSUFFICIENT":"NO_CANDIDATE":null);current.put("evaluatedCount",evaluated.size());result.set(kind.toLowerCase(),current);requestTruncated|=truncated[0];
        }return result.put("searchTruncated",requestTruncated).put("evaluatedCount",count[0]).put("evaluationLimit",MAX_REQUEST_EVALUATIONS);
    }
    private void enumerate(int index,List<String> categories,List<List<JsonNode>> dimensions,ArrayNode items,
                           JsonNode template,ObjectNode demand,JsonNode data,List<ObjectNode> results,
                           int[] requestCount,boolean[] truncated) {
        if(requestCount[0]>=MAX_REQUEST_EVALUATIONS){truncated[0]=true;return;}
        if(index==dimensions.size()){
            ObjectNode choice=json.object().put("kind","ASSEMBLY").put("templateKey",text(template,"key"));
            choice.set("items",items.deepCopy());
            results.add(evaluate(demand,selection(choice),data));requestCount[0]++;return;
        }
        for(JsonNode variant:dimensions.get(index)){
            boolean selected=!variant.isNull();
            if(selected)items.add(json.object().put("category",categories.get(index)).put("variantKey",text(variant,"key")));
            enumerate(index+1,categories,dimensions,items,template,demand,data,results,requestCount,truncated);
            if(selected)items.remove(items.size()-1);
            if(truncated[0])break;
        }
    }
    private boolean feasible(JsonNode e){return e.path("priceComplete").asBoolean()&&words("VERIFIED MANUFACTURER_COMPLETE REFERENCE_ESTIMATE").contains(text(e,"validationState"));}
    private ObjectNode bucket(List<ObjectNode> all,boolean truncated,ObjectNode d,String forced) {
        Comparator<ObjectNode> byPrice=Comparator.comparingLong((ObjectNode e)->e.path("totalCents").longValue()).thenComparing(e->json.canonical(e.path("selection")));
        List<ObjectNode> valid=all.stream().filter(this::feasible).sorted(Comparator.comparingInt((ObjectNode e)->e.path("purposeScore").asInt()).reversed().thenComparing(Comparator.comparingInt((ObjectNode e)->e.path("preferenceScore").asInt()).reversed()).thenComparing(byPrice)).toList();
        ObjectNode counts=json.object();for(ObjectNode e:all)if(!feasible(e)){String code=text(e,"validationState");counts.put(code,counts.path(code).asInt()+1);}else if(e.path("totalCents").longValue()>d.path("budgetCents").longValue())counts.put("OVER_BUDGET",counts.path("OVER_BUDGET").asInt()+1);
        ArrayNode plans=json.array();Set<String> identities=new HashSet<>();for(ObjectNode e:valid)if(e.path("totalCents").longValue()<=d.path("budgetCents").longValue()&&identities.add(identity(e))&&plans.size()<3)plans.add(e);
        if(plans.isEmpty()&&valid.isEmpty())all.stream().filter(e->"REFERENCE".equals(text(e,"pricingMode"))&&"INSUFFICIENT_DATA".equals(text(e,"validationState"))).sorted(Comparator.comparingLong((ObjectNode e)->pricedItems(e)).reversed().thenComparingLong(e->e.path("knownSubtotalCents").longValue()).thenComparing(e->json.canonical(e.path("selection")))).limit(3).forEach(plans::add);
        String state=forced!=null?forced:!plans.isEmpty()&&!valid.isEmpty()?"OK":!valid.isEmpty()?"BUDGET_INSUFFICIENT":all.stream().anyMatch(e->"INSUFFICIENT_DATA".equals(text(e,"validationState")))?"DATA_INSUFFICIENT":"NO_CANDIDATE";
        ObjectNode result=json.object().put("state",state).put("searchTruncated",truncated);result.set("plans",plans);result.set("excludedCounts",counts);ArrayNode missing=json.array();Set<String> seen=new TreeSet<>();for(ObjectNode e:all)for(JsonNode m:e.path("missing"))seen.add(m.asText());seen.forEach(missing::add);if("DATA_INSUFFICIENT".equals(state)&&missing.isEmpty())missing.add("ACTIVE_TEMPLATE_OR_VERIFIED_DATA_REQUIRED");result.set("missing",missing);
        if(state.equals("BUDGET_INSUFFICIENT")&&!valid.isEmpty()){ObjectNode minimum=valid.stream().min(byPrice).orElseThrow();result.set("minimumFeasiblePlan",minimum);result.put("shortfallCents",minimum.path("totalCents").longValue()-d.path("budgetCents").longValue());}else{result.putNull("minimumFeasiblePlan");result.putNull("shortfallCents");}return result;
    }
    private String identity(JsonNode e){
        ObjectNode identity=json.object();ArrayNode items=json.array();
        for(JsonNode line:e.path("items")){String category=text(line,"category");ObjectNode n=json.object().put("category",category),spec=json.object();if("REFERENCE".equals(text(e,"pricingMode"))&&category.equals("COMPLETE"))n.set("sourceProduct",line.path("productKey"));for(String field:SPECS.get(category).split(" "))spec.set(field,line.path("specs").path(field));n.set("specs",spec);items.add(n);}
        Set<String> facts=new TreeSet<>();for(JsonNode source:e.path("calculationSources"))if("USAGE_ADVICE".equals(text(source,"type"))){ObjectNode fact=json.object();fact.set("purpose",source.path("conditions").path("purpose"));fact.set("preference",source.path("conditions").has("preference")?source.path("conditions").get("preference"):NullNode.instance);facts.add(json.canonical(fact));}
        ArrayNode usage=json.array();facts.forEach(f->usage.add(json.parse(f)));identity.set("items",items);identity.set("usageFacts",usage);return json.hash(identity);
    }
    private long pricedItems(JsonNode e){long count=0;for(JsonNode line:e.path("items"))if(line.hasNonNull("lineTotalCents"))count++;return count;}
    private long referenceVariantCost(JsonNode v,JsonNode data){
        long cost=Long.MAX_VALUE;for(JsonNode q:data.path("quotes"))if(referenceQuote(q)&&Objects.equals(text(q,"sourceProductKey"),text(v,"productKey"))&&(!q.hasNonNull("variantKey")||Objects.equals(text(q,"variantKey"),text(v,"key"))))cost=Math.min(cost,q.path("amountCents").longValue());return cost;
    }
    public ObjectNode compare(JsonNode body){fields(body,"plans");JsonNode plans=body.path("plans");if(!plans.isArray()||plans.isEmpty()||plans.size()>3)bad("比较须为1至3套");JsonNode data=catalog.calculationCatalog();ArrayNode evaluations=json.array();for(JsonNode p:plans){fields(p,"demand selection");evaluations.add(evaluate(demand(p.path("demand")),selection(p.path("selection")),data));}ObjectNode result=json.object();result.set("evaluations",evaluations);result.set("comparisonFields",json.array().add("kind").add("totalCents").add("budgetDeltaCents").add("validationState").add("pricingMode").add("knownSubtotalCents").add("priceAssumptions").add("items").add("checks").add("missing"));return result;}
    public ObjectNode replacements(JsonNode body){
        fields(body,"demand selection category page size");
        ObjectNode d=demand(body.path("demand")),s=selection(body.path("selection"));String category=text(body,"category");
        if(!CATEGORIES.contains(category)||!containsCategories(s,category))bad("替换类别须已在方案中");
        int page=paging(body,"page",1,10000),size=paging(body,"size",20,50);JsonNode data=catalog.calculationCatalog();
        List<JsonNode> result=new ArrayList<>();Map<String,JsonNode> products=rows(data,"products");
        ObjectNode excluded=json.object();int visited=0;int[] adjustmentBudget={0};boolean truncated=false;
        for(JsonNode v:rows(data,"variants").values())if(enabled(v)&&enabled(products.get(text(v,"productKey")))&&Objects.equals(category,text(products.get(text(v,"productKey")),"category"))){
            if(visited++>=200){truncated=true;break;}
            ObjectNode replaced=s.deepCopy();for(JsonNode item:replaced.path("items"))if(category.equals(text(item,"category")))((ObjectNode)item).put("variantKey",text(v,"key"));
            ObjectNode e=evaluate(d,replaced,data);
            if(!feasible(e)){String code=text(e,"validationState");excluded.put(code,excluded.path(code).asInt()+1);}
            if("INCOMPATIBLE".equals(text(e,"validationState"))){e.put("requiresConfirmation",true);e.set("proposedAdjustments",adjustments(d,replaced,data,category,adjustmentBudget));}
            result.add(e);
        }
        ObjectNode out=json.object().put("total",result.size()).put("searchTruncated",truncated||adjustmentBudget[0]>=2000);ArrayNode list=json.array();
        result.stream().skip((long)(page-1)*size).limit(size).forEach(list::add);out.set("list",list);out.set("excludedCounts",excluded);return out;
    }
    private ArrayNode adjustments(ObjectNode demand,ObjectNode selection,JsonNode data,String replacedCategory,int[] attempts){
        Map<String,JsonNode> products=rows(data,"products");Map<String,Integer> visited=new HashMap<>();
        for(JsonNode v:rows(data,"variants").values()){
            JsonNode p=products.get(text(v,"productKey"));String category=p==null?null:text(p,"category");
            if(category==null||category.equals(replacedCategory)||category.equals("COMPLETE")||!enabled(v)||!enabled(p)||visited.merge(category,1,Integer::sum)>200)continue;
            if(attempts[0]>=2000)break;
            ObjectNode proposal=selection.deepCopy();boolean found=false;String previous=null;
            for(JsonNode item:proposal.path("items"))if(category.equals(text(item,"category"))){previous=text(item,"variantKey");((ObjectNode)item).put("variantKey",text(v,"key"));found=true;}
            if(!found){if(!category.equals("RISER"))continue;((ArrayNode)proposal.path("items")).add(json.object().put("category",category).put("variantKey",text(v,"key")));}
            if(Objects.equals(previous,text(v,"key")))continue;attempts[0]++;
            ObjectNode candidate=evaluate(demand,selection(proposal),data);
            if(feasible(candidate)){ObjectNode suggestion=json.object().put("category",category).put("variantKey",text(v,"key")).put("reasonCode","ADJUSTMENT_REQUIRED");suggestion.set("selection",candidate.path("selection"));return json.array().add(suggestion);}
        }
        return json.array().add(json.object().put("reasonCode","ADJUSTMENT_REQUIRED").put("message","未找到可验证的单项联动，请选择关联部件后提交整套重算"));
    }
    private boolean containsCategories(JsonNode s,String c){for(JsonNode i:s.path("items"))if(c.equals(text(i,"category")))return true;return false;}
    static int paging(JsonNode n,String field,int defaultValue,int max){if(!n.has(field))return defaultValue;if(!integer(n.get(field),1)||n.path(field).longValue()>max)bad("分页参数错误");return n.path(field).intValue();}
    /** Strip internal evidence pointers from anonymous responses, preserving source URLs and facts. */
    public JsonNode publicView(JsonNode node){JsonNode copy=node.deepCopy();strip(copy);return copy;}
    private void strip(JsonNode n){if(n.isObject()){ObjectNode o=(ObjectNode)n;if(o.has("evidenceRef"))o.remove("note");o.remove(List.of("calculationSources","quoteSnapshots","evidenceRef","purposeScore","preferenceScore"));}for(JsonNode child:n)strip(child);}
}
