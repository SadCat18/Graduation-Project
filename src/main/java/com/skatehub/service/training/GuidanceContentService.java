package com.skatehub.service.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.dao.UserRepository;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.skatehub.service.training.GuidanceJson.*;

@Service
@RequiredArgsConstructor
public class GuidanceContentService {
    private final JdbcTemplate jdbc;

    public List<ObjectNode> templates(boolean admin) {
        String where=admin?"":" where t.status='PUBLISHED' and t.safety_corrected=false";
        List<ObjectNode> result=jdbc.query("select t.template_id,t.version,t.status,t.safety_corrected,t.document from tb_guidance_template t"+where+" order by t.template_id,t.version desc",
            (r,i)-> { ObjectNode n=parse(r.getString("document")); n.put("id",r.getString("template_id")); n.put("version",r.getLong("version")); n.put("status",r.getString("status")); n.put("safetyCorrected",r.getBoolean("safety_corrected")); n.put("taskCount",n.path("tasks").size()); return n; });
        if(!admin) result.removeIf(doc->!valid(doc)); return result;
    }
    public ObjectNode template(String id,Long version,boolean admin) {
        return templates(true).stream().filter(n->(admin || (n.path("status").asText().equals("PUBLISHED")&&!n.path("safetyCorrected").asBoolean()&&valid(n)))).filter(n->n.path("id").asText().equals(id) && (version==null || n.path("version").asLong()==version)).findFirst()
            .orElseThrow(()->new GuidanceException(404,"CONTENT_UNAVAILABLE","教学版本不存在或不可用"));
    }
    public boolean available(String id,long version) {
        var docs=jdbc.query("select document from tb_guidance_template where template_id=? and version=? and status='PUBLISHED' and safety_corrected=false",(r,i)->parse(r.getString(1)),id,version);
        return !docs.isEmpty()&&valid(docs.get(0));
    }
    private boolean valid(ObjectNode doc) { try {validate(doc); return true;} catch(GuidanceException e) {return false;} }
    public long catalogStamp() {
        // Includes drafts/publication/source edits: contexts conservatively expire after any catalog change.
        return jdbc.queryForObject("select revision from tb_guidance_catalog_lock where id=1",Long.class);
    }
    @Transactional
    public JsonNode mutate(CurrentUser user,String id,String action,ObjectNode body) {
        if(user==null || !"ADMIN".equals(user.role())) throw new GuidanceException(403,"FORBIDDEN","需要管理员权限");
        if(user.id()==null) throw new GuidanceException(401,"UNAUTHENTICATED","请登录");
        // Admin identities belong to tb_admin, not tb_user. The catalog row serializes publication and admin replay.
        jdbc.queryForObject("select id from tb_guidance_catalog_lock where id=1 for update",Integer.class);
        String op="admin:"+action+":"+(id==null?"new":id);
        String key=text(body,"requestKey",8,100); check(key.matches("[A-Za-z0-9_-]+"),"requestKey 字符无效");
        var replay=jdbc.query("select payload_hash,result from tb_guidance_request where user_id=? and operation=? and request_key=?",(r,i)->List.of(r.getString(1),r.getString(2)),user.id(),op,key);
        String hash=hash(body);
        if(!replay.isEmpty()) { if(!hash.equals(replay.get(0).get(0))) throw new GuidanceException(409,"IDEMPOTENCY_CONFLICT","相同请求键内容不同"); return parse(replay.get(0).get(1)); }
        ObjectNode result;
        if(action.equals("create")) {
            ObjectNode doc=document(body); String newId=uuid(); doc.put("id",newId); doc.put("version",1); doc.put("status","DRAFT");
            jdbc.update("insert into tb_guidance_template(template_id,version,status,document) values(?,1,'DRAFT',?)",newId,doc.toString()); result=doc;
        } else {
            ObjectNode latest=template(id,null,true); long v=number(body,"expectedVersion");
            if(v!=latest.path("version").asLong()) throw new GuidanceException(409,"PLAN_VERSION_CONFLICT","教学版本已变更");
            if(action.equals("edit")) {
                ObjectNode doc=document(body); long next=v+1; doc.put("id",id); doc.put("version",next); doc.put("status","DRAFT");
                jdbc.update("insert into tb_guidance_template(template_id,version,status,document) values(?,?,'DRAFT',?)",id,next,doc.toString()); result=doc;
            } else {
                String status=choice(body,"status","PUBLISHED","DISABLED"); String reason=text(body,"reason",1,1000);
                if(status.equals("PUBLISHED")) { check(latest.path("status").asText().equals("DRAFT"),"仅草稿可以发布"); validate(latest); }
                boolean safety=body.path("safetyCorrection").asBoolean(false);
                if(body.has("safetyCorrection")) check(body.get("safetyCorrection").isBoolean(),"safetyCorrection 类型无效");
                if(safety) jdbc.update("update tb_guidance_template set safety_corrected=true,status='DISABLED',publication_reason=? where template_id=?",reason,id);
                else if(status.equals("DISABLED")) jdbc.update("update tb_guidance_template set status='DISABLED' where template_id=? and status='PUBLISHED'",id);
                jdbc.update("update tb_guidance_template set status=?,safety_corrected=?,publication_reason=? where template_id=? and version=?",safety?"DISABLED":status,safety,reason,id,v);
                result=template(id,v,true);
            }
        }
        jdbc.update("update tb_guidance_catalog_lock set revision=revision+1 where id=1");
        jdbc.update("insert into tb_guidance_request(user_id,operation,request_key,payload_hash,result) values(?,?,?,?,?)",user.id(),op,key,hash,result.toString());
        return result;
    }
    private ObjectNode document(JsonNode body) {
        check(body.path("document").isObject(),"document 必须为对象"); ObjectNode doc=(ObjectNode)body.path("document").deepCopy();
        check(doc.toString().length()<=80000,"教学文档过长"); text(doc,"title",1,100); return doc;
    }
    public void validate(ObjectNode doc) {
        text(doc,"title",1,100); text(doc,"goal",1,100); choice(doc,"level","BEGINNER","FOUNDATION","INTERMEDIATE");
        Map<String,String> registry=new HashMap<>();
        jdbc.query("select source_id,version,document from tb_guidance_source where status='AVAILABLE'",r->{registry.put(r.getString(1)+":"+r.getLong(2),hash(parse(r.getString(3))));});
        text(doc,"limitations",1,2000); sources(doc,registry);
        JsonNode rules=doc.path("rules"); check(rules.isObject(),"缺少固定规则配置");
        integer(rules,"version",1,1000000); integer(rules,"longBreakDays",1,365);
        Set<String> types=new HashSet<>(); list(rules,"operations",5,5).forEach(v->{check(v.isTextual(),"规则操作无效"); types.add(v.asText());});
        check(types.equals(Set.of("RESCHEDULE","SPLIT","CONSOLIDATE","ADVANCE","PAUSE")),"规则映射无效");
        Set<String> ids=new HashSet<>(); ArrayNode tasks=list(doc,"tasks",1,40);
        for(JsonNode t:tasks) { String id=text(t,"id",1,64); check(id.matches("[A-Za-z0-9_-]+") && ids.add(id),"任务ID重复或无效"); }
        Set<String> prior=new HashSet<>();
        for(JsonNode t:tasks) {
            text(t,"title",1,100); text(t,"goal",1,1000); choice(t,"level","BEGINNER","FOUNDATION","INTERMEDIATE");
            list(t,"steps",1,12).forEach(v->check(v.isTextual()&&!v.asText().isBlank()&&v.asText().length()<=1000,"步骤无效"));
            integer(t,"suggestedMinutes",1,120); text(t,"suggestedPractice",1,1000); text(t,"limitations",1,2000);
            list(t,"safetyNotes",1,10).forEach(v->check(v.isTextual()&&!v.asText().isBlank(),"安全说明无效"));
            text(t,"advanceCondition",1,1000); sources(t,registry);
            Set<String> qids=new HashSet<>(); for(JsonNode q:list(t,"selfChecks",2,3)) {check(qids.add(text(q,"id",1,64)),"自查ID重复"); text(q,"question",1,500);}
            for(JsonNode dep:list(t,"prerequisites",0,40)) check(dep.isTextual()&&ids.contains(dep.asText())&&prior.contains(dep.asText()),"前置依赖必须存在且在前，无环");
            JsonNode guidance=t.path("guidance"); check(guidance.isObject(),"缺少困难指导映射");
            for(String reason:List.of("TIME_SHORTAGE","VENUE_UNSUITABLE","MOVEMENT_DIFFICULTY","DISCOMFORT","OTHER","UNSURE","CONTINUE")) text(guidance,reason,1,1000);
            prior.add(t.path("id").asText());
        }
    }
    private void sources(JsonNode node,Map<String,String> registry) {
        for(JsonNode s:list(node,"sources",1,8)) {
            text(s,"id",1,100); text(s,"title",1,200); integer(s,"version",1,1000000);
            choice(s,"type","STATION_TEXT","EDITORIAL_REVIEW"); choice(s,"status","AVAILABLE");
            text(s,"locator",1,500); text(s,"excerpt",1,2000); text(s,"limitations",1,2000);
            // No arbitrary external URLs are used as proof of instruction or fetched by the server.
            check(hash(s).equals(registry.get(s.path("id").asText()+":"+s.path("version").asLong())),"来源不存在、已停用或片段/定位与已审核登记版本不一致；保持草稿并核对来源");
        }
    }
}
