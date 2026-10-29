package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.board.entity.*;
import com.skatehub.board.repository.*;
import com.skatehub.service.AdminAuditLogService;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.skatehub.board.BoardDatasetValidator.*;
import static com.skatehub.board.BoardDtos.*;

@Slf4j
@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class BoardCatalogService {
    private java.time.Clock clock=java.time.Clock.systemUTC();
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void calculationClock(java.time.Clock value) { this.clock=value; }

    private final BoardProductRepository products;
    private final BoardVariantRepository variants;
    private final BoardQuoteRepository quotes;
    private final BoardRuleRepository rules;
    private final BoardTemplateRepository templates;
    private final BoardCatalogMetaRepository metadata;
    private final BoardDatasetValidator validator;
    private final AdminAuditLogService audit;
    @Value("${app.board-assistant.import-enabled:false}") private boolean importEnabled;

    public String catalogVersion() { return requiredMetadata(false).getCatalogVersion(); }

    private BoardCatalogMeta requiredMetadata(boolean lock) {
        BoardCatalogMeta meta=lock?metadata.lockCatalog():metadata.findById(1L).orElse(null);
        if(meta==null){
            log.error("Board catalog metadata missing: tb_board_catalog_meta id=1; verify V9 migration/schema ownership; no automatic repair performed");
            throw new BoardRequestException(503,"器材目录暂时不可用，请稍后重试");
        }
        return meta;
    }

    private List<? extends BoardRecord> records(String group) {
        return switch(group) {
            case "products" -> products.findAll();case "variants" -> variants.findAll();
            case "quotes" -> quotes.findLatest();case "rules" -> rules.findAll();case "templates" -> templates.findAll();
            default -> throw new IllegalArgumentException(group);
        };
    }
    Map<String,BoardRecord> current(String group) {
        Map<String,BoardRecord> result=new TreeMap<>();
        for(BoardRecord r:records(group))result.merge(r.getKey(),r,(a,b)->a.getVersion()>b.getVersion()?a:b);
        return result;
    }
    ObjectNode stored() {
        ObjectNode result=validator.object();
        for(String group:GROUPS){ArrayNode list=validator.array();for(BoardRecord r:current(group).values())list.add(validator.parse(r.getContentJson()));result.set(group,list);}
        return result;
    }
    /** Short independent serializable snapshot: callers never hold a catalog write lock while calculating. */
    @Transactional(readOnly=true, isolation=Isolation.SERIALIZABLE, propagation=Propagation.REQUIRES_NEW)
    public ObjectNode calculationCatalog() {
        // Metadata first matches every writer. MySQL SERIALIZABLE may take shared locks until this short read ends.
        String version=requiredMetadata(false).getCatalogVersion();
        ObjectNode result=stored();result.put("catalogVersion",version);return result;
    }
    /** Save/admin mutations join the outer write transaction and retain its version barrier until commit. */
    @Transactional
    public ObjectNode lockedCalculationCatalog() {
        BoardCatalogMeta meta=requiredMetadata(true);
        ObjectNode result=stored();result.put("catalogVersion",meta.getCatalogVersion());return result;
    }
    public org.springframework.data.domain.Page<? extends BoardRecord> currentPage(String group,int page,int size) {
        var pageable=PageRequest.of(page-1,size,Sort.by("key"));
        return switch(group){
            case "products" -> products.findAll(pageable);
            case "variants" -> variants.findAll(pageable);
            case "quotes" -> quotes.findLatestPage(pageable);
            case "rules" -> rules.findAll(pageable);
            case "templates" -> templates.findAll(pageable);
            default -> throw new IllegalArgumentException(group);
        };
    }
    public ValidationReport validate(JsonNode dataset) {
        ValidationReport report=validator.validate(dataset,stored());
        List<Issue> errors=new ArrayList<>(report.errors());
        conflicts(dataset,errors);
        return new ValidationReport(errors.isEmpty(),List.copyOf(errors),report.gaps(),catalogVersion());
    }
    private void conflicts(JsonNode dataset,List<Issue> errors) {
        for(String group:GROUPS){Map<String,BoardRecord> existing=current(group);
            for(JsonNode item:dataset.path(group)) {
                BoardRecord old=existing.get(text(item,"key"));if(old==null||!item.isObject())continue;
                if(version(item)<old.getVersion())errors.add(new Issue(text(item,"key"),"OLDER_VERSION",group));
                else if(version(item)==old.getVersion()&&!validator.canonical(validator.versioned(item)).equals(old.getContentJson()))errors.add(new Issue(text(item,"key"),"SAME_VERSION_DIFFERENT_CONTENT",group));
            }
        }
        if(dataset.isObject()&&text(dataset,"datasetVersion")!=null){
            JsonNode fingerprints=validator.parse(requiredMetadata(false).getDatasetFingerprints());
            String old=text(fingerprints,text(dataset,"datasetVersion"));
            if(old!=null&&!old.equals(validator.hash(dataset)))errors.add(new Issue("datasetVersion","SAME_VERSION_DIFFERENT_CONTENT","dataset fingerprint"));
        }
    }
    @Transactional
    public ImportResult importDataset(JsonNode dataset,String expectedCatalogVersion,Long adminId) {
        if(!importEnabled)throw new BoardRequestException(403,"当前环境未开放器材导入");
        return writeDataset(dataset,expectedCatalogVersion,adminId,"IMPORT");
    }
    @Transactional
    public ImportResult writeDataset(JsonNode dataset,String expectedCatalogVersion,Long adminId,String action) {
        BoardCatalogMeta meta=requiredMetadata(true);
        if(!Objects.equals(expectedCatalogVersion,meta.getCatalogVersion()))return new ImportResult(0,0,0,0,List.of(),dataset.path("gaps"),"CATALOG_CHANGED",meta.getCatalogVersion());
        ValidationReport report=validate(dataset);
        if(!report.valid()) {
            String errorCode=report.errors().stream().anyMatch(e->e.code().equals("OLDER_VERSION")||e.code().equals("SAME_VERSION_DIFFERENT_CONTENT"))?"VERSION_CONFLICT":"SELECTION_INVALID";
            long count=GROUPS.stream().mapToLong(g->dataset.path(g).size()).sum();
            return new ImportResult(0,0,0,count,report.errors(),report.gaps(),errorCode,meta.getCatalogVersion());
        }
        long created=0,updated=0,unchanged=0;
        Map<String,BoardProduct> productMap=products.findAll().stream().collect(Collectors.toMap(BoardProduct::getKey,Function.identity()));
        Map<String,BoardVariant> variantMap=variants.findAll().stream().collect(Collectors.toMap(BoardVariant::getKey,Function.identity()));
        for(String group:GROUPS) {
            Map<String,BoardRecord> oldRows=current(group);
            for(JsonNode item:dataset.path(group)) {
                String key=text(item,"key"),content=validator.canonical(validator.versioned(item));BoardRecord old=oldRows.get(key);
                if(old!=null&&old.getVersion()==version(item)){unchanged++;continue;}
                BoardRecord row;
                switch(group) {
                    case "products" -> {
                        BoardProduct p=old==null?new BoardProduct():(BoardProduct)old;
                        base(p,item,content);p.setSourceRecordId(text(item,"sourceRecordId"));p.setCategory(text(item,"category"));p.setBrand(text(item,"brand"));p.setModel(text(item,"model"));
                        row=products.save(p);productMap.put(key,p);
                    }
                    case "variants" -> {
                        BoardVariant v=old==null?new BoardVariant():(BoardVariant)old;
                        base(v,item,content);v.setProductKey(text(item,"productKey"));v.setProduct(productMap.get(v.getProductKey()));v.setCategory(v.getProduct().getCategory());v.setVerificationStatus(text(item,"verificationStatus"));
                        JsonNode spec=item.path("specs");v.setWidthInch(decimal(spec,"widthInch"));v.setLengthInch(decimal(spec,"lengthInch"));v.setAxleWidthInch(decimal(spec,"axleWidthInch"));v.setHeightMm(decimal(spec,"heightMm"));v.setDiameterMm(decimal(spec,"diameterMm"));
                        row=variants.save(v);variantMap.put(key,v);
                    }
                    case "quotes" -> {
                        if(old!=null){old.setActive(false);quotes.save((BoardQuote)old);}
                        BoardQuote q=new BoardQuote();base(q,item,content);
                        q.setSourceProductKey(text(item,"sourceProductKey"));q.setProduct(productMap.get(q.getSourceProductKey()));q.setVariantKey(text(item,"variantKey"));q.setVariant(variantMap.get(q.getVariantKey()));
                        q.setCurrency(text(item,"currency"));q.setAmountCents(amount(item,"amountCents"));q.setShippingCents(amount(item,"shippingCents"));q.setTaxCents(amount(item,"taxCents"));q.setPackageQuantity(amount(item,"packageQuantity"));q.setPackageUnit(text(item,"packageUnit"));
                        q.setPriceKind(text(item,"priceKind"));q.setMarket(text(item,"market"));q.setVerificationStatus(text(item,"verificationStatus"));q.setLandedPriceConfirmed(item.path("landedPriceConfirmed").asBoolean());q.setCollectedAt(time(item.get("collectedAt")));q.setValidUntil(time(item.get("validUntil")));
                        row=quotes.save(q);
                    }
                    case "rules" -> {BoardRule r=old==null?new BoardRule():(BoardRule)old;base(r,item,content);r.setType(text(item,"type"));r.setSeverity(text(item,"severity"));r.setVerificationStatus(text(item,"verificationStatus"));row=rules.save(r);}
                    case "templates" -> {BoardTemplate t=old==null?new BoardTemplate():(BoardTemplate)old;base(t,item,content);t.setPurpose(text(item,"purpose"));t.setVerificationStatus(text(item,"verificationStatus"));row=templates.save(t);}
                    default -> throw new IllegalArgumentException(group);
                }
                oldRows.put(key,row);if(old==null)created++;else updated++;
            }
        }
        // Flush before metadata/audit: any SQL failure rolls back the entire import.
        products.flush();variants.flush();quotes.flush();rules.flush();templates.flush();
        ArrayNode published=validator.array();
        for(String group:GROUPS)for(BoardRecord row:current(group).values())if(row.isActive()){ObjectNode entry=validator.object();entry.put("resource",group);entry.set("content",validator.parse(row.getContentJson()));published.add(entry);}
        String catalogVersion=validator.hash(published);meta.setCatalogVersion(catalogVersion);
        if(action.equals("IMPORT")){ObjectNode fingerprints=(ObjectNode)validator.parse(meta.getDatasetFingerprints());fingerprints.put(text(dataset,"datasetVersion"),validator.hash(dataset));meta.setDatasetFingerprints(validator.canonical(fingerprints));}
        meta.setUpdatedAt(clock.instant());metadata.saveAndFlush(meta);
        audit.record(adminId,"BOARD_CATALOG",null,action,"SUCCESS","created="+created+", updated="+updated+", unchanged="+unchanged);
        return new ImportResult(created,updated,unchanged,0,List.of(),report.gaps(),null,catalogVersion);
    }
    private static void base(BoardRecord row,JsonNode item,String content){row.setKey(text(item,"key"));row.setVersion(version(item));row.setActive(item.path("active").asBoolean());row.setContentJson(content);}
    private static Long amount(JsonNode node,String field){return node.hasNonNull(field)?node.get(field).longValue():null;}
    private static BigDecimal decimal(JsonNode node,String field){return node.hasNonNull(field)?node.get(field).decimalValue():null;}

    public PageResult<JsonNode> catalog(String category,int page,int size) {
        if(page<1||page>10000||size<1||size>50||category!=null&&!CATEGORIES.contains(category))throw new BoardRequestException(400,"分类或分页参数错误");
        var pageable=PageRequest.of(page-1,size,Sort.by("key"));var result=category==null?products.findByActiveTrue(pageable):products.findByActiveTrueAndCategory(category,pageable);
        List<String> keys=result.getContent().stream().map(BoardProduct::getKey).toList();
        Map<String,List<BoardVariant>> v=variants.findByProductKeyIn(keys).stream().filter(BoardVariant::isActive).collect(Collectors.groupingBy(BoardVariant::getProductKey));
        List<BoardQuote> q=quotes.findLatestBySourceProductKeyIn(keys);
        List<JsonNode> list=new ArrayList<>();
        for(BoardProduct p:result){ObjectNode item=publicProduct(p);ArrayNode variantList=validator.array();v.getOrDefault(p.getKey(),List.of()).stream().sorted(Comparator.comparing(BoardVariant::getKey)).forEach(row->variantList.add(publicVariant(row)));item.set("variants",variantList);item.set("displayQuotes",displayQuotes(q,p.getKey(),v.getOrDefault(p.getKey(),List.of())));list.add(item);}
        return new PageResult<>(result.getTotalElements(),list);
    }
    public JsonNode variant(String key) {
        BoardVariant v=variants.findByKey(key).filter(row->row.isActive()||validator.parse(row.getContentJson()).path("historicalOnly").asBoolean()).orElseThrow(()->new BoardRequestException(404,"规格不存在"));
        if(!v.getProduct().isActive())throw new BoardRequestException(404,"规格不存在");
        ObjectNode result=validator.object();result.set("variant",publicVariant(v));result.set("product",publicProduct(v.getProduct()));result.set("displayQuotes",displayQuotes(quotes.findLatestBySourceProductKeyIn(List.of(v.getProductKey())),v.getProductKey(),List.of(v)));
        ObjectNode eligibility=validator.object();eligibility.put("exactPriceEligible",v.isActive()&&eligible(quotes.findLatestBySourceProductKeyIn(List.of(v.getProductKey())),v.getKey()));eligibility.put("referencePriceEligible",v.isActive()&&referenceEligible(quotes.findLatestBySourceProductKeyIn(List.of(v.getProductKey())),v.getKey()));eligibility.put("verificationStatus",v.getVerificationStatus());result.set("eligibility",eligibility);result.set("sources",publicSources(validator.parse(v.getContentJson()).path("sources")));result.put("catalogVersion",catalogVersion());return result;
    }
    private ObjectNode publicProduct(BoardProduct p){return view(validator.parse(p.getContentJson()),"key legacyEquipmentId sourceRecordId category brand model active description suitabilityTags levelTags imageSource imageLicenseStatus version pricingMode fieldEvidence");}
    private ObjectNode publicVariant(BoardVariant v){return view(validator.parse(v.getContentJson()),"key productKey label active specs rawSpec verificationStatus version pricingMode identity replacements historicalOnly fieldEvidence compatibilityAdvice packaging");}
    private ObjectNode view(JsonNode node,String fields){ObjectNode result=validator.object();for(String f:fields.split(" "))if(node.has(f)) {
            JsonNode value=node.get(f).deepCopy();
            if(f.equals("fieldEvidence"))for(JsonNode fact:value)if(fact.isObject())((ObjectNode)fact).remove(List.of("evidenceRef","responseSha256"));
            if(f.equals("promotion")&&value.isObject())((ObjectNode)value).remove("evidenceRef");
            result.set(f,value);
        }if(node.has("sources"))result.set("sources",publicSources(node.path("sources")));return result;}
    private ArrayNode publicSources(JsonNode sources){ArrayNode result=validator.array();for(JsonNode s:sources)result.add(view(s,"url title collectedAt collectedDate verificationStatus checkedAt appliesTo fields factNature"));return result;}
    private ArrayNode displayQuotes(List<BoardQuote> rows,String productKey,List<BoardVariant> visible) {
        Set<String> keys=visible.stream().map(BoardVariant::getKey).collect(Collectors.toSet());Map<String,BoardQuote> latest=new TreeMap<>();
        for(BoardQuote q:rows)if(q.getSourceProductKey().equals(productKey))latest.merge(q.getKey(),q,(a,b)->a.getVersion()>b.getVersion()?a:b);
        ArrayNode result=validator.array();for(BoardQuote q:latest.values())if(q.getVariantKey()==null||keys.contains(q.getVariantKey())){
            ObjectNode item=view(validator.parse(q.getContentJson()),"key variantKey version currency amountCents displayPrice priceKind market purchaseScope packageQuantity packageUnit shippingCents taxCents landedPriceConfirmed collectedAt collectedDate validUntil verificationStatus pricingMode priceAssumptions promotion");
            String temporal=referenceTimeStatus(validator.parse(q.getContentJson()),clock.instant());
            item.put("referenceTemporalStatus",temporal);item.put("historicalOnly","EXPIRED".equals(temporal)||visible.stream().noneMatch(BoardVariant::isActive));
            item.put("active",q.isActive());item.put("exactPriceEligible",eligible(List.of(q),q.getVariantKey()));item.put("referencePriceEligible",q.isActive()&&visible.stream().anyMatch(v->v.isActive()&&(q.getVariantKey()==null||q.getVariantKey().equals(v.getKey())))&&referenceQuote(validator.parse(q.getContentJson()),clock.instant()));result.add(item);
        }return result;
    }
    private boolean referenceEligible(List<BoardQuote> rows,String variantKey) {
        Map<String,BoardQuote> latest=new HashMap<>();for(BoardQuote q:rows)latest.merge(q.getKey(),q,(a,b)->a.getVersion()>b.getVersion()?a:b);
        return latest.values().stream().anyMatch(q->q.isActive()&&(q.getVariantKey()==null||variantKey.equals(q.getVariantKey()))&&referenceQuote(validator.parse(q.getContentJson()),clock.instant()));
    }
    private boolean eligible(List<BoardQuote> rows,String variantKey) {
        if(variantKey==null)return false;Map<String,BoardQuote> latest=new HashMap<>();for(BoardQuote q:rows)latest.merge(q.getKey(),q,(a,b)->a.getVersion()>b.getVersion()?a:b);
        return latest.values().stream().anyMatch(q->variantKey.equals(q.getVariantKey())&&q.isActive()&&!reference(validator.parse(q.getContentJson()))&&"VERIFIED".equals(q.getVerificationStatus())&&"EXACT".equals(q.getPriceKind())&&"CNY".equals(q.getCurrency())&&"CN".equals(q.getMarket())&&q.isLandedPriceConfirmed()&&q.getAmountCents()!=null&&q.getPackageQuantity()!=null&&q.getPackageQuantity()>0&&q.getPackageUnit()!=null&&q.getShippingCents()!=null&&q.getTaxCents()!=null&&q.getValidUntil()!=null&&q.getValidUntil().isAfter(clock.instant())&&currentReferenceTime(validator.parse(q.getContentJson()),clock.instant()));
    }
}
