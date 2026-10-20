package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.board.entity.BoardPlanSnapshot;
import com.skatehub.board.repository.BoardPlanSnapshotRepository;
import com.skatehub.util.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.skatehub.board.BoardCalculationService.*;
import static com.skatehub.board.BoardDatasetValidator.text;

@Service @RequiredArgsConstructor @Transactional
public class BoardPlanService {
    private final BoardPlanSnapshotRepository plans;
    private final BoardCalculationService calculation;
    private final BoardCatalogService catalog;
    private final BoardDatasetValidator json;
    public JsonNode save(Long owner,JsonNode body){
        fields(body,"name note demand selection expectedCalculationVersion");versionToken(body,"expectedCalculationVersion");validateText(body,true);
        JsonNode data=catalog.lockedCalculationCatalog();ObjectNode evaluation=calculation.evaluate(calculation.demand(body.path("demand")),calculation.selection(body.path("selection")),data);
        if(!body.path("expectedCalculationVersion").asText().equals(evaluation.path("calculationVersion").asText()))return conflict(calculation.changeCode(body.path("expectedCalculationVersion").asText(),evaluation),evaluation);
        if("INCOMPATIBLE".equals(text(evaluation,"validationState")))return conflict("INCOMPATIBLE",evaluation);
        if("INSUFFICIENT_DATA".equals(text(evaluation,"validationState"))){evaluation.putNull("totalCents");evaluation.putNull("budgetDeltaCents");evaluation.put("snapshotState","DRAFT");}else evaluation.put("snapshotState","REFERENCE_ESTIMATE".equals(text(evaluation,"validationState"))?"REFERENCE_ESTIMATE":"VERIFIED".equals(text(evaluation,"validationState"))?"VERIFIED":"MANUFACTURER_COMPLETE");
        // Complete calculation/quote/rule/template contents remain frozen inside this JSON.
        BoardPlanSnapshot p=new BoardPlanSnapshot();p.setKey(UUID.randomUUID().toString());p.setOwnerId(owner);p.setName(text(body,"name"));p.setNote(text(body,"note"));String content=json.canonical(evaluation);if(content.getBytes(StandardCharsets.UTF_8).length>65535)bad("方案证据超过保存上限");p.setContentJson(content);plans.saveAndFlush(p);return view(p);
    }
    private ObjectNode conflict(String code,ObjectNode evaluation){ObjectNode out=json.object().put("errorCode",code).put("requiresConfirmation",true);out.set("evaluation",calculation.publicView(evaluation));return out;}
    @Transactional(readOnly=true)
    public PageResult<JsonNode> list(Long owner,int page,int size){if(page<1||page>10000||size<1||size>50)bad("分页参数错误");var result=plans.findByOwnerId(owner,PageRequest.of(page-1,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));return new PageResult<>(result.getTotalElements(),result.stream().map(p->{ObjectNode summary=view(p);summary.remove(List.of("calculationSources","quoteSnapshots","items","checks"));return (JsonNode)summary;}).toList());}
    public ObjectNode detail(Long owner,String key){BoardPlanSnapshot p=owned(owner,key);ObjectNode out=json.object();ObjectNode snapshot=view(p);out.set("snapshot",snapshot);ObjectNode request=json.object();request.set("demand",snapshot.path("demand"));request.set("selection",snapshot.path("selection"));try{out.set("latestEvaluation",calculation.publicView(calculation.evaluate(request)));}catch(BoardRequestException e){if(e.status()!=400&&e.status()!=404)throw e;ObjectNode latest=json.object().put("errorCode","CATALOG_CHANGED").put("validationState","INSUFFICIENT_DATA");latest.putNull("totalCents");latest.set("missing",json.array().add("SAVED_SELECTION_NO_LONGER_AVAILABLE"));out.set("latestEvaluation",latest);}return out;}
    public ObjectNode patch(Long owner,String key,JsonNode body){fields(body,"name note");validateText(body,false);BoardPlanSnapshot p=owned(owner,key);if(body.has("name"))p.setName(text(body,"name"));if(body.has("note"))p.setNote(text(body,"note"));plans.saveAndFlush(p);return view(p);}
    public ObjectNode copy(Long owner,String key,JsonNode body){fields(body,"name note");validateText(body,false);BoardPlanSnapshot source=owned(owner,key);BoardPlanSnapshot p=new BoardPlanSnapshot();p.setKey(UUID.randomUUID().toString());p.setOwnerId(owner);p.setName(body.has("name")?text(body,"name"):source.getName());p.setNote(body.has("note")?text(body,"note"):source.getNote());p.setContentJson(source.getContentJson());p.setCopiedFromKey(source.getKey());plans.saveAndFlush(p);return view(p);}
    public void delete(Long owner,String key){plans.delete(owned(owner,key));plans.flush();}
    private BoardPlanSnapshot owned(Long owner,String key){if(key==null||key.length()>200)throw new BoardRequestException(404,"方案不存在");return plans.findByKeyAndOwnerId(key,owner).orElseThrow(()->new BoardRequestException(404,"方案不存在"));}
    private void validateText(JsonNode body,boolean requireName){if(requireName&&!body.has("name")||body.has("name")&&(text(body,"name")==null||text(body,"name").isBlank()||text(body,"name").length()>80))bad("方案名称须为1至80字符");if(body.hasNonNull("note")&&(text(body,"note")==null||text(body,"note").length()>1000))bad("备注最长1000字符");}
    private ObjectNode view(BoardPlanSnapshot p){ObjectNode result=(ObjectNode)json.parse(p.getContentJson());result.put("key",p.getKey()).put("ownerId",p.getOwnerId()).put("name",p.getName());if(p.getNote()==null)result.putNull("note");else result.put("note",p.getNote());result.put("createdAt",p.getCreatedAt().toString()).put("updatedAt",p.getUpdatedAt().toString());if(p.getCopiedFromKey()==null)result.putNull("copiedFromKey");else result.put("copiedFromKey",p.getCopiedFromKey()).put("historicalCopy",true);return result;}
}
