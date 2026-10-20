package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.board.entity.BoardRecord;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.skatehub.board.BoardCalculationService.*;
import static com.skatehub.board.BoardDatasetValidator.*;

@Service @RequiredArgsConstructor @Transactional
public class BoardAdminService {
    private final BoardCatalogService catalog;
    private final BoardDatasetValidator json;
    private final BoardCalculationService calculation;
    private void resource(String r){if(!GROUPS.contains(r))throw new BoardRequestException(404,"目录类型不存在");}
    public PageResult<JsonNode> list(String resource,int page,int size){resource(resource);if(page<1||page>10000||size<1||size>50)bad("分页参数错误");var current=catalog.currentPage(resource,page,size);List<JsonNode> result=current.getContent().stream().map(row->{ObjectNode n=(ObjectNode)json.parse(row.getContentJson());n.put("active",row.isActive());return (JsonNode)n;}).toList();return new PageResult<>(current.getTotalElements(),result);}
    public JsonNode create(String resource,JsonNode body,Long actor){resource(resource);recordFields(resource,body,false);ObjectNode n=body.deepCopy();key(n,"key");catalog.lockedCalculationCatalog();if(catalog.current(resource).containsKey(text(n,"key")))return conflict("VERSION_CONFLICT",null);if(n.has("version")&&(!integer(n.get("version"),1)||n.path("version").asLong()!=1))bad("新草稿首版须为1");n.put("version",1).put("active",false);return write(resource,n,actor,"CREATE_DRAFT");}
    public JsonNode patch(String resource,String key,JsonNode body,Long actor){resource(resource);recordFields(resource,body,true);catalog.lockedCalculationCatalog();BoardRecord old=existing(resource,key);expected(body);if(body.path("expectedVersion").asLong()!=old.getVersion())return conflict("VERSION_CONFLICT",old);
        ObjectNode n=(ObjectNode)json.parse(old.getContentJson());if(body.has("key")&&!key.equals(text(body,"key")))bad("不可更改稳定key");if(body.has("active")&&(!body.path("active").isBoolean()||body.path("active").asBoolean()!=old.isActive()))bad("启用状态须使用activation接口");
        body.fields().forEachRemaining(e->{if(!Set.of("expectedVersion","version","key","active").contains(e.getKey()))n.set(e.getKey(),e.getValue());});try{n.put("version",Math.addExact(old.getVersion(),1));}catch(ArithmeticException e){bad("版本超出上限");}return write(resource,n,actor,"UPDATE");}
    public JsonNode activate(String resource,String key,JsonNode body,Long actor){resource(resource);fields(body,"active expectedVersion");expected(body);if(!body.path("active").isBoolean())bad("active须为布尔值");catalog.lockedCalculationCatalog();BoardRecord old=existing(resource,key);if(body.path("expectedVersion").asLong()!=old.getVersion())return conflict("VERSION_CONFLICT",old);ObjectNode n=(ObjectNode)json.parse(old.getContentJson());if(old.isActive()==body.path("active").asBoolean())return n;try{n.put("version",Math.addExact(old.getVersion(),1));}catch(ArithmeticException e){bad("版本超出上限");}n.put("active",body.path("active").asBoolean());return write(resource,n,actor,body.path("active").asBoolean()?"ACTIVATE":"DEACTIVATE");}
    private BoardRecord existing(String resource,String key){return Optional.ofNullable(catalog.current(resource).get(key)).orElseThrow(()->new BoardRequestException(404,"目录记录不存在"));}
    private void expected(JsonNode n){if(!integer(n.get("expectedVersion"),1))bad("expectedVersion须为正整数");}
    private void recordFields(String resource,JsonNode body,boolean update){String names=switch(resource){case "products"->"key version legacyEquipmentId sourceRecordId category brand model active description suitabilityTags levelTags sources imageSource imageLicenseStatus notes";case "variants"->"key version productKey label active specs rawSpec sources verificationStatus notes";case "quotes"->"key sourceProductKey variantKey version currency amountCents displayPrice priceKind market purchaseScope packageQuantity packageUnit shippingCents taxCents landedPriceConfirmed collectedAt collectedDate validUntil verificationStatus active sources rawPrice notes";case "rules"->"key version type severity scope conditions outcome explanation sources verificationStatus active notes";case "templates"->"key version purpose requiredCategories selectionConditions reasonCodes sources verificationStatus active notes";default->throw new IllegalArgumentException(resource);};fields(body,names+" pricingMode priceAssumptions"+(update?" expectedVersion":""));}
    private JsonNode write(String resource,ObjectNode n,Long actor,String action){
        ObjectNode d=json.object().put("schemaVersion",1).put("datasetVersion","admin-"+UUID.randomUUID());for(String group:GROUPS)d.set(group,json.array());((ArrayNode)d.get(resource)).add(n);d.set("gaps",json.array());ArrayNode mappings=json.array();
        if(resource.equals("products")||resource.equals("variants")||resource.equals("quotes")){
            JsonNode p=resource.equals("products")?n:json.parse(existing("products",text(n,resource.equals("quotes")?"sourceProductKey":"productKey")).getContentJson());
            String sourcePath="admin:"+text(n,"key");if(resource.equals("quotes"))for(JsonNode s:n.path("sources")){String ref=text(s,"evidenceRef");if(ref!=null&&ref.startsWith("equipment.json#")&&ref.contains("/")){sourcePath=ref.substring(0,ref.lastIndexOf('/'));break;}}
            ObjectNode m=json.object().put("sourceRecordId",text(p,"sourceRecordId")).put("productKey",text(p,"key")).put("sourcePath",sourcePath).put("sourceRecordSha256",json.hash(n)).put("basis","Explicit admin revision fingerprint; original field provenance remains in sources");m.set("variantKeys",resource.equals("variants")?json.array().add(text(n,"key")):json.array());m.set("quoteKeys",resource.equals("quotes")?json.array().add(text(n,"key")):json.array());m.set("ruleKeys",json.array());mappings.add(m);
        }d.set("mapping",mappings);
        BoardDtos.ImportResult result=catalog.writeDataset(d,catalog.catalogVersion(),actor,action);
        if(result.errorCode()!=null){ObjectNode out=conflict(result.errorCode(),null);ArrayNode errors=json.array();for(BoardDtos.Issue i:result.errors())errors.add(json.object().put("path",i.path()).put("code",i.code()).put("detail",i.detail()));out.set("errors",errors);out.set("gaps",result.gaps());return out;}
        ObjectNode out=(ObjectNode)json.parse(existing(resource,text(n,"key")).getContentJson());out.put("catalogVersion",result.catalogVersion());return out;
    }
    private ObjectNode conflict(String code,BoardRecord current){ObjectNode out=json.object().put("errorCode",code).put("catalogVersion",catalog.catalogVersion());if(current!=null){out.put("currentVersion",current.getVersion());out.set("current",json.parse(current.getContentJson()));}return out;}
    public JsonNode trial(JsonNode demand){return calculation.publicView(calculation.recommend(demand));}
}
