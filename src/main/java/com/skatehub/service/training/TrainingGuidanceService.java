package com.skatehub.service.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.skatehub.dao.UserRepository;
import com.skatehub.service.UserGrowthService;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import static com.skatehub.service.training.GuidanceJson.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class TrainingGuidanceService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserGrowthService growth;
    private final GuidanceContentService content;
    private final Clock clock;
    // ponytail: one versioned JSON aggregate per plan; relational practices/completions/requests retain facts.
    // ponytail: the catalog row currently serializes guidance writes with publication (global throughput ceiling).
    // Replace it with shared source/version locks if measured concurrency requires more throughput.
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    private String now() { return ZonedDateTime.now(clock.withZone(ZONE)).toOffsetDateTime().toString(); }
    public long owner(CurrentUser u) {
        if(u==null || u.id()==null) throw new GuidanceException(401,"UNAUTHENTICATED","请登录"); return u.id();
    }
    private void lock(CurrentUser u) { users.findByIdForUpdate(owner(u)).orElseThrow(GuidanceJson::missing); }
    public ObjectNode load(CurrentUser u,long id) {
        var rows=jdbc.query("select document from tb_guidance_plan where user_plan_id=? and user_id=?",(r,i)->parse(r.getString(1)),id,owner(u));
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private ObjectNode locked(CurrentUser u,long id) {
        lock(u);
        jdbc.queryForObject("select id from tb_guidance_catalog_lock where id=1 for update",Integer.class);
        // Every writer, including legacy, takes the same user row before the plan.
        var rows=jdbc.query("select document from tb_guidance_plan where user_plan_id=? and user_id=? for update",(r,i)->parse(r.getString(1)),id,owner(u));
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    public ObjectNode preview(ObjectNode body) {
        String id=text(body,"templateId",1,64); long version=number(body,"templateVersion");
        ObjectNode template=content.template(id,version,false); content.validate(template);
        String goal=text(body,"goal",1,100); String level=choice(body,"selfLevel","BEGINNER","FOUNDATION","INTERMEDIATE");
        int minutes=integer(body,"sessionMinutes",5,120);
        ArrayNode days=list(body,"trainingDays",1,7); Set<Integer> unique=new TreeSet<>();
        for(JsonNode day:days) { check(day.isIntegralNumber()&&day.asInt()>=1&&day.asInt()<=7&&unique.add(day.asInt()),"训练日须为不重复ISO1-7"); }
        LocalDate start=date(body,"startDate"); check(!start.isBefore(today())&&!start.isAfter(today().plusDays(30)),"开始日期须为今天至30日内");
        ObjectNode plan=obj("version",0,"status","IN_PROGRESS","timezone","Asia/Shanghai","goal",goal,"selfLevel",level,
            "settings",obj("trainingDays",unique,"sessionMinutes",minutes,"startDate",start.toString()),
            "template",obj("id",id,"version",version,"title",template.path("title")),"snapshot",template,
            "tasks",arr(),"suggestions",arr(),"adjustments",arr(),"safetyEvents",arr(),"pauseIntervals",arr(),"advanceBlocked",false);
        LocalDate cursor=start;
        for(JsonNode source:template.path("tasks")) {
            ObjectNode task=(ObjectNode)source.deepCopy(); task.put("definitionId",source.path("id").asText()); task.put("id",uuid());
            task.put("unlocked",source.path("prerequisites").isEmpty()); task.put("status","PENDING"); task.put("selfAssessment","NOT_ASSESSED"); ArrayNode segments=arr();
            int remaining=source.path("suggestedMinutes").asInt();
            while(remaining>0) { cursor=nextDay(plan,cursor); int m=Math.min(remaining,minutes); segments.add(obj("id",uuid(),"date",cursor.toString(),"minutes",m,"status","PENDING")); remaining-=m; cursor=cursor.plusDays(1); }
            task.set("segments",segments); task.put("date",segments.get(0).path("date").asText()); ((ArrayNode)plan.get("tasks")).add(task);
        }
        plan.put("feedbackPolicy","记录提交后不可修改或删除；再次练习请新增记录。用户自评不代表客观能力认证。");
        plan.set("limitations",template.path("limitations")); return plan;
    }
    private LocalDate nextDay(ObjectNode plan,LocalDate d) {
        Set<Integer> days=new HashSet<>(); plan.path("settings").path("trainingDays").forEach(v->days.add(v.asInt()));
        for(int i=0;i<7;i++,d=d.plusDays(1)) if(days.contains(d.getDayOfWeek().getValue())) return d;
        throw new IllegalStateException("No training day");
    }
    @Transactional
    public JsonNode create(CurrentUser u,ObjectNode body) {
        lock(u); jdbc.queryForObject("select id from tb_guidance_catalog_lock where id=1 for update",Integer.class); String op="create"; JsonNode old=replay(u,op,body); if(old!=null) return old;
        var legacy=jdbc.queryForList("select user_plan_id from tb_user_training_plan where user_id=? and mode='LEGACY' and status='IN_PROGRESS'",Long.class,owner(u));
        if(!legacy.isEmpty()) throw new GuidanceException(409,"PLAN_CONFLICT","存在旧训练计划，请从旧计划入口退出",Map.of("legacyConflict",obj("userPlanIds",legacy,"exitApi","POST /api/skate/training-plans/{userPlanId}/quit")));
        if(jdbc.queryForObject("select count(*) from tb_guidance_active where user_id=?",Long.class,owner(u))>0) throw new GuidanceException(409,"PLAN_CONFLICT","已有进行中或暂停的训练指导计划");
        ObjectNode p=preview(body);
        long id=insertId("insert into tb_user_training_plan(user_id,plan_id,mode,start_date,current_day,completed_days,status,create_time,update_time) values(?,null,'GUIDED',?,1,0,'IN_PROGRESS',?,?)",
            owner(u),p.path("settings").path("startDate").asText(),LocalDateTime.now(clock.withZone(ZONE)),LocalDateTime.now(clock.withZone(ZONE)));
        p.put("id",id); p.put("createdAt",now()); p.path("tasks").forEach(t->((ObjectNode)t).put("planId",id));
        jdbc.update("insert into tb_guidance_plan(user_plan_id,user_id,version,template_id,template_version,document) values(?,?,0,?,?,?)",id,owner(u),p.path("template").path("id").asText(),p.path("template").path("version").asLong(),p.toString());
        jdbc.update("insert into tb_guidance_active(user_id,user_plan_id) values(?,?)",owner(u),id);
        return remember(u,op,body,view(p));
    }
    private long insertId(String sql,Object... args) {
        GeneratedKeyHolder holder=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]); return s;},holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    private String key(ObjectNode body) { String k=text(body,"requestKey",8,100); check(k.matches("[A-Za-z0-9_-]+"),"requestKey 仅允许字母数字下划线短横线"); return k; }
    private JsonNode replay(CurrentUser u,String op,ObjectNode body) {
        String k=key(body); var rows=jdbc.query("select payload_hash,result from tb_guidance_request where user_id=? and operation=? and request_key=?",(r,i)->List.of(r.getString(1),r.getString(2)),owner(u),op,k);
        if(rows.isEmpty()) return null;
        if(!rows.get(0).get(0).equals(hash(body))) throw new GuidanceException(409,"IDEMPOTENCY_CONFLICT","相同请求键内容不同");
        return parse(rows.get(0).get(1));
    }
    private JsonNode remember(CurrentUser u,String op,ObjectNode body,JsonNode result) {
        jdbc.update("insert into tb_guidance_request(user_id,operation,request_key,payload_hash,result) values(?,?,?,?,?)",owner(u),op,key(body),hash(body),result.toString()); return result;
    }
    private void version(ObjectNode p,ObjectNode b) { if(number(b,"expectedVersion")!=p.path("version").asLong()) throw new GuidanceException(409,"PLAN_VERSION_CONFLICT","计划已变化，请刷新"); }
    private boolean usable(ObjectNode p) { return content.available(p.path("template").path("id").asText(),p.path("template").path("version").asLong()); }
    private void active(ObjectNode p) { if(!p.path("status").asText().equals("IN_PROGRESS")) throw new GuidanceException(409,"PLAN_CONFLICT","当前计划状态不允许练习或调整"); }
    private void save(ObjectNode p) {
        p.put("version",p.path("version").asLong()+1);
        jdbc.update("update tb_guidance_plan set version=?,document=? where user_plan_id=?",p.path("version").asLong(),p.toString(),p.path("id").asLong());
        jdbc.update("update tb_user_training_plan set status=?,update_time=? where user_plan_id=?",p.path("status").asText(),LocalDateTime.now(clock.withZone(ZONE)),p.path("id").asLong());
        if(Set.of("QUIT","COMPLETED").contains(p.path("status").asText())) jdbc.update("delete from tb_guidance_active where user_plan_id=?",p.path("id").asLong());
    }
    public JsonNode current(CurrentUser u) {
        var ids=jdbc.queryForList("select user_plan_id from tb_guidance_active where user_id=?",Long.class,owner(u)); return ids.isEmpty()?null:view(load(u,ids.get(0)));
    }
    public ObjectNode plan(CurrentUser u,long id) { return view(load(u,id)); }
    public ObjectNode history(CurrentUser u,int page,int size) {
        check(page>=1&&size>=1&&size<=100,"分页范围无效");
        var list=jdbc.query("select document from tb_guidance_plan where user_id=? order by user_plan_id desc limit ? offset ?",(r,i)->view(parse(r.getString(1))),owner(u),size,(long)(page-1)*size);
        return obj("list",list,"total",jdbc.queryForObject("select count(*) from tb_guidance_plan where user_id=?",Long.class,owner(u)),"page",page,"size",size);
    }

    public ObjectNode task(CurrentUser u,long id,String taskId) {
        ObjectNode p=load(u,id); return taskView(p,findTask(p,taskId));
    }
    private ObjectNode findTask(ObjectNode p,String id) { for(JsonNode t:p.path("tasks")) if(t.path("id").asText().equals(id)) return (ObjectNode)t; throw missing(); }
    public ObjectNode todayView(CurrentUser u,long id,LocalDate d) { return todayView(load(u,id),d); }
    private ObjectNode todayView(ObjectNode p,LocalDate d) { return todayView(p,d,usable(p)); }
    private ObjectNode todayView(ObjectNode p,LocalDate d,boolean available) {
        check(!d.isBefore(LocalDate.parse(p.path("settings").path("startDate").asText())),"展示日期早于计划开始日期");
        ArrayNode scheduled=arr(),overdue=arr();
        for(JsonNode t:p.path("tasks")) {
            boolean scheduledHere=false,late=false;
            if(t.path("status").asText().equals("CANCELLED")) continue;
            for(JsonNode s:t.path("segments")) {
                LocalDate day=LocalDate.parse(s.path("date").asText());
                if(day.equals(d)) scheduledHere=true;
                if(!t.path("status").asText().equals("COMPLETED")&&s.path("status").asText().equals("PENDING")&&day.isBefore(today())&&!pausedOn(p,day)) late=true;
            }
            if(scheduledHere) scheduled.add(taskView(p,(ObjectNode)t,available)); if(late) overdue.add(taskView(p,(ObjectNode)t,available));
        }
        return obj("date",d.toString(),"timezone","Asia/Shanghai","tasks",scheduled,"restDay",scheduled.isEmpty(),"paused",pausedOn(p,d),"overdue",overdue);
    }
    private boolean pausedOn(ObjectNode p,LocalDate day) {
        for(JsonNode interval:p.path("pauseIntervals")) {
            LocalDate start=LocalDate.parse(interval.path("from").asText());
            if(!day.isBefore(start)&&(interval.path("to").isNull()||!day.isAfter(LocalDate.parse(interval.path("to").asText())))) return true;
        } return false;
    }
    public ObjectNode statistics(CurrentUser u,long id) { return stats(load(u,id)); }
    private ObjectNode stats(ObjectNode p) {
        int total=0,done=0; Map<String,Integer> assessments=new LinkedHashMap<>();
        for(JsonNode t:p.path("tasks")) { if(t.path("status").asText().equals("CANCELLED")) continue; total++; if(t.path("status").asText().equals("COMPLETED")) done++; assessments.merge(t.path("selfAssessment").asText(),1,Integer::sum); }
        long id=p.path("id").asLong();
        var totals=jdbc.queryForMap("select count(*) as practice_count,coalesce(sum(actual_minutes),0) as practice_minutes from tb_guidance_practice where user_plan_id=?",id);
        return obj("completedTasks",done,"totalTasks",total,"completionRate",total==0?0:(double)done/total,"practiceMinutes",totals.get("practice_minutes"),"practiceCount",totals.get("practice_count"),"selfAssessmentSummary",assessments,
            "definition","分母为有效逻辑根任务，分子为首次完整完成根任务；分段不新增分子/分母。时长累计每条不可修改练习。自查为用户自评，不代表能力认证。");
    }
    private ArrayNode blocked(ObjectNode p) { return blocked(p,usable(p)); }
    private ArrayNode blocked(ObjectNode p,boolean available) {
        ArrayNode b=arr(); if(!p.path("status").asText().equals("IN_PROGRESS")) b.add("PLAN_"+p.path("status").asText());
        if(today().isBefore(LocalDate.parse(p.path("settings").path("startDate").asText()))) b.add("PLAN_NOT_STARTED");
        if(!available) b.add("CONTENT_UNAVAILABLE"); if(p.path("advanceBlocked").asBoolean()) b.add("SAFETY_REASSESSMENT_REQUIRED"); return b;
    }
    private ArrayNode actions(ObjectNode p) { return actions(p,usable(p)); }
    private ArrayNode actions(ObjectNode p,boolean available) {
        ArrayNode a=arr(); String status=p.path("status").asText();
        if(status.equals("IN_PROGRESS")) { a.add("PAUSE").add("QUIT"); if(available&&!today().isBefore(LocalDate.parse(p.path("settings").path("startDate").asText()))) a.add("FEEDBACK"); }
        if(status.equals("PAUSED")) { a.add("QUIT"); if(available) a.add("RESUME"); } return a;
    }
    private ObjectNode taskView(ObjectNode p,ObjectNode original) {return taskView(p,original,usable(p));}
    private ObjectNode taskView(ObjectNode p,ObjectNode original,boolean available) {
        ObjectNode t=original.deepCopy(); ArrayNode reasons=blocked(p,available); ArrayNode allowed=actions(p,available);
        if(!t.path("unlocked").asBoolean()) {reasons.add("PREREQUISITE_CONFIRMATION_REQUIRED"); allowed=arr();}
        if(today().isBefore(LocalDate.parse(t.path("date").asText()))) {reasons.add("TASK_NOT_SCHEDULED_YET"); allowed=arr();}
        // State actions belong to PlanView; a task exposes feedback only.
        ArrayNode feedback=arr(); for(JsonNode action:allowed)if(action.asText().equals("FEEDBACK"))feedback.add(action);
        t.set("allowedActions",feedback); t.set("blockedReasons",reasons); t.put("contentAvailable",available); return t;
    }
    private ObjectNode view(ObjectNode original) {
        boolean available=usable(original); ObjectNode p=original.deepCopy(); p.remove(List.of("snapshot","suggestions","adjustments"));
        ArrayNode tasks=arr(); original.path("tasks").forEach(t->tasks.add(taskView(original,(ObjectNode)t,available))); p.set("tasks",tasks);
        p.set("stats",stats(original)); p.set("allowedActions",actions(original,available)); p.set("blockedReasons",blocked(original,available));
        LocalDate d=today(); LocalDate start=LocalDate.parse(p.path("settings").path("startDate").asText()); p.set("today",d.isBefore(start)?obj("date",d.toString(),"timezone","Asia/Shanghai","startsOn",start.toString(),"notStarted",true,"tasks",arr(),"restDay",true,"paused",false,"overdue",arr()):todayView(original,d,available)); p.set("safetyAlerts",alerts(original,available));
        p.put("contentAvailable",available); return p;
    }
    private ArrayNode alerts(ObjectNode p) {return alerts(p,usable(p));}
    private ArrayNode alerts(ObjectNode p,boolean available) {
        ArrayNode a=arr(); for(JsonNode e:p.path("safetyEvents")) a.add(e);
        if(!available) a.add(obj("type","CONTENT_UNAVAILABLE","message","采用的教学版本已停用或安全纠正，请停止采用相关指导并查看新版本；历史记录保留。")); return a;
    }
    public ObjectNode records(CurrentUser u,long id,int page,int size) {
        load(u,id);check(page>=1&&size>=1&&size<=100,"分页范围无效");
        var list=jdbc.query("select document from tb_guidance_practice where user_plan_id=? order by practice_date desc,record_id desc limit ? offset ?",(r,i)->parse(r.getString(1)),id,size,(long)(page-1)*size);
        return obj("list",list,"total",jdbc.queryForObject("select count(*) from tb_guidance_practice where user_plan_id=?",Long.class,id),"page",page,"size",size);
    }
    private List<ObjectNode> recordList(long id) { return jdbc.query("select document from tb_guidance_practice where user_plan_id=? order by practice_date desc,record_id desc",(r,i)->parse(r.getString(1)),id); }
    public ObjectNode record(CurrentUser u,long id,String recordId) {
        ObjectNode p=load(u,id); ObjectNode r=recordList(id).stream().filter(v->v.path("id").asText().equals(recordId)).findFirst().orElseThrow(GuidanceJson::missing);
        ArrayNode notices=(ArrayNode)r.path("safetyAlerts").deepCopy();
        if(!usable(p)) notices.add(obj("type","CONTENT_UNAVAILABLE","message","历史教学快照保留，但该版本/来源现已不可用，请勿据此继续练习。"));
        return obj("record",r,"review",r.path("review"),"safetyAlerts",notices,"contentAvailable",usable(p),"suggestions",recordSuggestions(p,recordId));
    }
    private ArrayNode recordSuggestions(ObjectNode p,String recordId) {
        ArrayNode a=arr(); for(JsonNode s:p.path("suggestions")) if(s.path("recordId").asText().equals(recordId)) a.add(suggestionView(p,(ObjectNode)s)); return a;
    }

    @Transactional
    public JsonNode feedback(CurrentUser u,long id,ObjectNode b) {
        ObjectNode p=locked(u,id); String op="feedback:"+id; JsonNode retry=replay(u,op,b); if(retry!=null)return retry;
        version(p,b); active(p); if(!usable(p)) throw new GuidanceException(409,"CONTENT_UNAVAILABLE","教学资料已停用");
        ObjectNode t=findTask(p,text(b,"instanceId",1,64));
        check(t.path("unlocked").asBoolean(),"前置阶段需完成自查并接受推进建议后解锁"); LocalDate date=date(b,"practiceDate");
        check(!date.isAfter(today())&&!date.isBefore(LocalDate.parse(p.path("settings").path("startDate").asText())),"练习日期超出允许补录范围");
        ObjectNode segment=null;
        if(b.hasNonNull("segmentId")) { String sid=text(b,"segmentId",1,64); for(JsonNode s:t.path("segments")) if(s.path("id").asText().equals(sid)) segment=(ObjectNode)s; if(segment==null) throw missing(); check(!segment.path("status").asText().equals("RETIRED"),"该分段已调整，请使用当前分段"); }
        LocalDate earliest=LocalDate.parse(segment==null?t.path("date").asText():segment.path("date").asText());
        check(!date.isBefore(earliest),"不能提前练习尚未排到的任务/分段");
        check(!pausedOn(p,date),"暂停期间不补计练习，请先恢复并按新安排练习");
        String completion=choice(b,"completion","NOT_STARTED","PARTIAL","COMPLETED"); int minutes=integer(b,"actualMinutes",0,240);
        check(completion.equals("NOT_STARTED")?minutes==0:minutes>=1,"完成情况与实际分钟不一致");
        if(!completion.equals("NOT_STARTED")) choice(b,"difficulty","EASY","MODERATE","HARD"); else if(b.hasNonNull("difficulty")) choice(b,"difficulty","EASY","MODERATE","HARD");
        Set<String> reasons=new LinkedHashSet<>(); for(JsonNode reason:list(b,"reasons",0,5)) check(reason.isTextual()&&Set.of("TIME_SHORTAGE","VENUE_UNSUITABLE","MOVEMENT_DIFFICULTY","DISCOMFORT","OTHER").contains(reason.asText())&&reasons.add(reason.asText()),"原因无效或重复");
        if(b.hasNonNull("note")) text(b,"note",0,1000);
        Set<String> questionIds=new HashSet<>(); t.path("selfChecks").forEach(q->questionIds.add(q.path("id").asText()));
        Set<String> answered=new HashSet<>(); boolean stable=true,uncertain=false;
        ArrayNode checks=list(b,"selfChecks",0,3);
        for(JsonNode answer:checks) {
            String q=text(answer,"questionId",1,64); check(questionIds.contains(q)&&answered.add(q),"自查问题不属于任务快照或重复");
            String a=choice(answer,"answer","YES","NO","UNSURE","NOT_APPLICABLE"); if(!a.equals("YES"))stable=false; if(Set.of("UNSURE","NOT_APPLICABLE").contains(a))uncertain=true;
        }
        stable=stable&&answered.size()==questionIds.size()&&!answered.isEmpty()&&!completion.equals("NOT_STARTED");
        String assessment=stable?"SELF_STABLE":uncertain?"UNCERTAIN":checks.isEmpty()?"NOT_ASSESSED":"NEEDS_PRACTICE";
        t.put("selfAssessment",assessment);
        String recordId=uuid(); ObjectNode r=b.deepCopy(); r.remove(List.of("requestKey","expectedVersion")); r.put("id",recordId); r.put("planId",id); r.put("createdAt",now()); r.put("selfAssessment",assessment); r.set("taskSnapshot",t.deepCopy());
        int exp=0;
        if(completion.equals("COMPLETED")&&!t.path("status").asText().equals("COMPLETED")) {
            // A segment completion is partial at root level until all other segments have actually been completed.
            boolean rootComplete=true;
            if(segment!=null) { segment.put("status","COMPLETED"); for(JsonNode s:t.path("segments")) if(!Set.of("COMPLETED","RETIRED").contains(s.path("status").asText())) rootComplete=false; }
            else for(JsonNode s:t.path("segments")) if(!s.path("status").asText().equals("RETIRED")) check(!date.isBefore(LocalDate.parse(s.path("date").asText())),"完整完成根任务不可早于所有分段安排；可单独记录已到期分段");
            if(rootComplete) {
                t.put("status","COMPLETED"); for(JsonNode s:t.path("segments")) ((ObjectNode)s).put("status","COMPLETED");
                long fact=insertId("insert into tb_guidance_completion(user_plan_id,instance_id,record_id) values(?,?,?)",id,t.path("id").asText(),recordId);
                exp=growth.grantExpResult(owner(u),"TRAINING_TASK_COMPLETE","GUIDANCE_COMPLETION",fact,8).delta();
            } else t.put("status","PARTIAL");
        } else if(completion.equals("PARTIAL")&&!t.path("status").asText().equals("COMPLETED")) t.put("status","PARTIAL");
        boolean discomfort=reasons.contains("DISCOMFORT");
        if(discomfort) {
            p.put("advanceBlocked",true); ((ArrayNode)p.get("safetyEvents")).add(obj("id",uuid(),"recordId",recordId,"type","DISCOMFORT","createdAt",now(),"acknowledged",false,"message","停止相关练习，身体不适时不要加量或进阶；持续疼痛、肿胀等请寻求专业帮助。本系统不诊断。"));
        } else if(stable && p.path("safetyEvents").findValues("acknowledged").stream().allMatch(JsonNode::asBoolean)) p.put("advanceBlocked",false);
        long nextVersion=p.path("version").asLong()+1;
        ObjectNode suggestion=rule(p,t,r,reasons,stable,uncertain,nextVersion);
        if(suggestion!=null)((ArrayNode)p.get("suggestions")).add(suggestion);
        ObjectNode review=obj("selfAssessment",assessment,"message",discomfort?"先停止相关练习并查看安全提醒":t.path("guidance").path(reasons.isEmpty()?(uncertain?"UNSURE":"CONTINUE"):reasons.iterator().next()).asText(),"sources",t.path("sources"),"limitations",t.path("limitations"),"completionIsNotMastery",true);
        r.set("review",review); r.set("safetyAlerts",alerts(p)); r.put("expAwarded",exp);
        jdbc.update("insert into tb_guidance_practice(record_id,user_plan_id,instance_id,practice_date,actual_minutes,document) values(?,?,?,?,?,?)",recordId,id,t.path("id").asText(),date.toString(),minutes,r.toString());
        boolean all=true; for(JsonNode task:p.path("tasks")) if(!Set.of("COMPLETED","CANCELLED").contains(task.path("status").asText())) all=false;
        int planExp=0; if(all) { p.put("status","COMPLETED"); planExp=growth.grantExpResult(owner(u),"TRAINING_PLAN_COMPLETE","USER_TRAINING_PLAN",id,30).delta(); }
        save(p);
        return remember(u,op,b,obj("record",r,"plan",view(p),"review",review,"safetyAlerts",alerts(p),"suggestions",recordSuggestions(p,recordId),"planExpAwarded",planExp));
    }

    private ObjectNode rule(ObjectNode p,ObjectNode t,ObjectNode record,Set<String> reasons,boolean stable,boolean uncertain,long version) {
        String type,trigger;
        String last=jdbc.queryForObject("select max(practice_date) from tb_guidance_practice where user_plan_id=? and actual_minutes>0",String.class,p.path("id").asLong());
        LocalDate reference=last==null?LocalDate.parse(p.path("settings").path("startDate").asText()):LocalDate.parse(last);
        int breakDays=p.path("snapshot").path("rules").path("longBreakDays").asInt();
        if(reasons.contains("DISCOMFORT") || p.path("safetyEvents").findValues("acknowledged").stream().anyMatch(v->!v.asBoolean())) { type="PAUSE"; trigger="DISCOMFORT"; }
        else if(!reference.plusDays(breakDays).isAfter(today())) { type="PAUSE"; trigger="LONG_BREAK"; }
        else if(reasons.contains("TIME_SHORTAGE")) { type="SPLIT"; trigger="TIME_SHORTAGE"; }
        else if(reasons.contains("VENUE_UNSUITABLE")||!todayView(p,today().isBefore(LocalDate.parse(p.path("settings").path("startDate").asText()))?LocalDate.parse(p.path("settings").path("startDate").asText()):today()).path("overdue").isEmpty()) { type="RESCHEDULE"; trigger="OVERDUE_OR_VENUE"; }
        else if(reasons.contains("MOVEMENT_DIFFICULTY")||uncertain||record.path("difficulty").asText().equals("HARD")) { type="CONSOLIDATE"; trigger="SELF_REVIEW"; }
        else if(stable && record.path("completion").asText().equals("COMPLETED") && !p.path("advanceBlocked").asBoolean() && nextLocked(p)!=null) { type="ADVANCE"; trigger="SELF_STABLE"; }
        else { type="CONSOLIDATE"; trigger="CONTINUE"; }
        ArrayNode before=scheduleTasks(p),after=before.deepCopy();
        if(type.equals("ADVANCE")) {
            ObjectNode next=nextLocked(p); if(next==null || !prerequisitesReady(p,next)) return null;
            for(JsonNode a:after) if(a.path("id").equals(next.path("id"))) ((ObjectNode)a).put("unlocked",true);
        } else if(!type.equals("PAUSE")) adjustSchedule(p,after,t.path("id").asText(),type,record);
        return obj("id",uuid(),"type",type,"status","PENDING","ruleId",trigger,"ruleVersion",p.path("snapshot").path("rules").path("version"),
            "triggerReasons",List.of(trigger),"recordId",record.path("id"),"basePlanVersion",version,"catalogRevision",content.catalogStamp(),
            "affectedTaskIds",ids(after),"before",obj("tasks",before,"status",p.path("status")),"after",obj("tasks",after,"status",type.equals("PAUSE")?"PAUSED":p.path("status").asText()),
            "diff",obj("operation",type,"message",type.equals("ADVANCE")?"仅用户确认后解锁下一阶段；自评不是掌握认证":type.equals("PAUSE")?"确认暂停，恢复需要重新自评": "保留历史练习与已完成分段，重新安排剩余分钟，不增加单次可用时间", "remainingMinutesArePlanningOnly",true),"createdAt",now());
    }
    private ArrayNode scheduleTasks(ObjectNode p) {
        ArrayNode result=arr(); for(JsonNode t:p.path("tasks")) result.add(obj("id",t.path("id"),"status",t.path("status"),"unlocked",t.path("unlocked"),"date",t.path("date"),"segments",t.path("segments").deepCopy(),"consumedMinutesAtSchedule",t.path("consumedMinutesAtSchedule").asLong(),"scheduleGeneration",t.path("scheduleGeneration").asInt())); return result;
    }
    private ArrayNode ids(ArrayNode tasks) { ArrayNode ids=arr(); tasks.forEach(t->ids.add(t.path("id"))); return ids; }
    private ObjectNode nextLocked(ObjectNode p) { for(JsonNode t:p.path("tasks")) if(!t.path("unlocked").asBoolean()) return (ObjectNode)t; return null; }
    private boolean prerequisitesReady(ObjectNode p,ObjectNode next) {
        for(JsonNode dep:next.path("prerequisites")) {
            boolean ready=false; for(JsonNode t:p.path("tasks")) if(t.path("definitionId").asText().equals(dep.asText())&&t.path("status").asText().equals("COMPLETED")&&t.path("selfAssessment").asText().equals("SELF_STABLE"))ready=true;
            if(!ready)return false;
        } return !p.path("advanceBlocked").asBoolean();
    }
    private void adjustSchedule(ObjectNode p,ArrayNode tasks,String target,String type,ObjectNode currentRecord) {
        LocalDate cursor=nextDay(p,today().plusDays(1)); int session=p.path("settings").path("sessionMinutes").asInt();
        List<ObjectNode> practices=new ArrayList<>(recordList(p.path("id").asLong())); if(currentRecord!=null)practices.add(currentRecord);
        for(JsonNode raw:tasks) {
            ObjectNode task=(ObjectNode)raw; if(Set.of("COMPLETED","CANCELLED").contains(task.path("status").asText()))continue;
            // Completed segments and original dates are retained; obsolete pending segments are explicitly retired.
            ArrayNode segments=(ArrayNode)task.path("segments"); int planned=0,maxSegment=1;
            for(JsonNode s:segments) if(s.path("status").asText().equals("PENDING")) {planned+=s.path("minutes").asInt(); maxSegment=Math.max(maxSegment,s.path("minutes").asInt()); ((ObjectNode)s).put("status","RETIRED");}
            long recorded=jdbc.queryForObject("select coalesce(sum(actual_minutes),0) from tb_guidance_practice where user_plan_id=? and instance_id=?",Long.class,p.path("id").asLong(),task.path("id").asText());
            if(currentRecord!=null && task.path("id").equals(currentRecord.path("instanceId"))) recorded+=currentRecord.path("actualMinutes").asInt();
            long previous=task.path("consumedMinutesAtSchedule").asLong(0);
            int generation=task.path("scheduleGeneration").asInt(); Set<String> completedSegments=new HashSet<>();
            for(JsonNode segment:segments) if(segment.path("status").asText().equals("COMPLETED")&&segment.path("generation").asInt()==generation)completedSegments.add(segment.path("id").asText());
            long accountedCompletionMinutes=0;
            for(JsonNode practice:practices) if(practice.path("instanceId").equals(task.path("id"))&&completedSegments.contains(practice.path("segmentId").asText()))accountedCompletionMinutes+=practice.path("actualMinutes").asInt();
            int remaining=Math.max(1,planned-(int)Math.max(0,recorded-previous-accountedCompletionMinutes));
            task.put("consumedMinutesAtSchedule",recorded); task.put("scheduleGeneration",generation+1);
            int cap=type.equals("SPLIT")&&task.path("id").asText().equals(target)?Math.max(1,Math.min(Math.min(session,maxSegment),(remaining+1)/2)):Math.min(session,maxSegment);
            LocalDate first=cursor;
            while(remaining>0) {cursor=nextDay(p,cursor); int m=Math.min(remaining,cap); segments.add(obj("id",uuid(),"date",cursor.toString(),"minutes",m,"status","PENDING","generation",generation+1)); remaining-=m; cursor=cursor.plusDays(1);}
            task.put("date",nextDay(p,first).toString());
        }
    }
    public ObjectNode suggestions(CurrentUser u,long id,int page,int size) {
        ObjectNode p=load(u,id); List<ObjectNode> list=new ArrayList<>(); for(JsonNode s:p.path("suggestions")) list.add(suggestionView(p,(ObjectNode)s)); Collections.reverse(list); return page(list,page,size);
    }
    private ObjectNode suggestionView(ObjectNode p,ObjectNode original) {
        ObjectNode s=original.deepCopy(); ArrayNode reasons=arr();
        if(s.path("status").asText().equals("PENDING")) {
            if(s.path("basePlanVersion").asLong()!=p.path("version").asLong())reasons.add("PLAN_VERSION_CONFLICT");
            if(s.path("catalogRevision").asLong()!=content.catalogStamp()||!usable(p))reasons.add("CONTENT_UNAVAILABLE");
            if(!p.path("status").asText().equals("IN_PROGRESS"))reasons.add("PLAN_STATE_CHANGED");
            if(s.path("type").asText().equals("ADVANCE")&&p.path("advanceBlocked").asBoolean())reasons.add("SAFETY_REASSESSMENT_REQUIRED");
            if(!reasons.isEmpty())s.put("status","EXPIRED");
        }
        s.set("blockedReasons",reasons); s.set("allowedActions",s.path("status").asText().equals("PENDING")?JSON.valueToTree(List.of("ACCEPT","REJECT")):arr()); return s;
    }
    @Transactional(noRollbackFor=GuidanceException.class)
    public JsonNode decision(CurrentUser u,long id,String suggestionId,ObjectNode b) {
        ObjectNode p=locked(u,id); String op="decision:"+id+":"+suggestionId; JsonNode retry=replay(u,op,b); if(retry!=null)return retry;
        number(b,"expectedVersion"); String decision=choice(b,"decision","ACCEPT","REJECT"); ObjectNode s=null;
        for(JsonNode item:p.path("suggestions")) if(item.path("id").asText().equals(suggestionId))s=(ObjectNode)item;
        if(s==null)throw missing();
        // An already processed suggestion is a stable no-op, including another key; never reapply its diff.
        if(Set.of("ACCEPTED","REJECTED").contains(s.path("status").asText())) return remember(u,op,b,obj("suggestion",suggestionView(p,s),"plan",view(p)));
        version(p,b);
        if(suggestionView(p,s).path("status").asText().equals("EXPIRED")) {
            s.put("status","EXPIRED");
            jdbc.update("update tb_guidance_plan set document=? where user_plan_id=?",p.toString(),id);
            throw new GuidanceException(409,"SUGGESTION_EXPIRED","建议上下文已失效，请按最新记录获取建议");
        }
        if(decision.equals("ACCEPT")) {
            if(s.path("type").asText().equals("ADVANCE")) { ObjectNode next=nextLocked(p); if(next==null||!prerequisitesReady(p,next))throw new GuidanceException(409,"SUGGESTION_EXPIRED","前置条件或安全自评已变化"); }
            if(s.path("type").asText().equals("PAUSE")) { p.put("status","PAUSED"); ((ArrayNode)p.get("pauseIntervals")).add(obj("from",today().toString(),"to",null)); }
            else for(JsonNode schedule:s.path("after").path("tasks")) {
                ObjectNode target=findTask(p,schedule.path("id").asText()); schedule.fields().forEachRemaining(field->target.set(field.getKey(),field.getValue().deepCopy()));
            }
            s.put("status","ACCEPTED");
            ((ArrayNode)p.get("adjustments")).add(obj("id",uuid(),"suggestionId",suggestionId,"operation",s.path("type"),"fromVersion",p.path("version"),"toVersion",p.path("version").asLong()+1,"before",s.path("before"),"after",s.path("after"),"diff",s.path("diff"),"actorId",owner(u),"createdAt",now()));
        } else s.put("status","REJECTED");
        save(p); return remember(u,op,b,obj("suggestion",suggestionView(p,s),"plan",view(p)));
    }
    @Transactional
    public JsonNode state(CurrentUser u,long id,ObjectNode b) {
        ObjectNode p=locked(u,id); String op="state:"+id; JsonNode retry=replay(u,op,b);if(retry!=null)return retry;
        version(p,b); String action=choice(b,"action","PAUSE","RESUME","QUIT"),status=p.path("status").asText();
        if(Set.of("COMPLETED","QUIT").contains(status))throw new GuidanceException(409,"PLAN_CONFLICT","已结束计划为历史只读");
        ObjectNode before=obj("status",p.path("status"),"tasks",scheduleTasks(p),"selfLevel",p.path("selfLevel"));
        if(action.equals("RESUME")) {
            check(status.equals("PAUSED"),"只有暂停计划可恢复"); if(!usable(p))throw new GuidanceException(409,"CONTENT_UNAVAILABLE","采用资料不可用");
            String self=choice(b,"selfLevel","BEGINNER","FOUNDATION","INTERMEDIATE"); check(b.path("safetyAcknowledged").isBoolean(),"恢复须明确安全确认");
            if(p.path("advanceBlocked").asBoolean())check(b.path("safetyAcknowledged").asBoolean(),"请重新自评并确认安全提醒；此确认不是医疗许可");
            p.put("advanceBlocked",true); p.put("selfLevel",self); for(JsonNode e:p.path("safetyEvents")) {((ObjectNode)e).put("acknowledged",true); ((ObjectNode)e).put("acknowledgedAt",now());}
            for(JsonNode interval:p.path("pauseIntervals")) if(interval.path("to").isNull()) ((ObjectNode)interval).put("to",today().minusDays(1).toString());
            p.put("status","IN_PROGRESS"); adjustSchedule(p,(ArrayNode)p.get("tasks"),"","RESCHEDULE",null);
        } else if(action.equals("PAUSE")) {
            check(status.equals("IN_PROGRESS"),"当前已暂停"); p.put("status","PAUSED"); ((ArrayNode)p.get("pauseIntervals")).add(obj("from",today().toString(),"to",null));
        } else p.put("status","QUIT");
        ((ArrayNode)p.get("adjustments")).add(obj("id",uuid(),"operation",action,"fromVersion",p.path("version"),"toVersion",p.path("version").asLong()+1,"before",before,"after",obj("status",p.path("status"),"tasks",scheduleTasks(p)),"actorId",owner(u),"createdAt",now()));
        save(p); return remember(u,op,b,view(p));
    }
    public ObjectNode adjustments(CurrentUser u,long id,int page,int size) {
        List<JsonNode> items=new ArrayList<>(); load(u,id).path("adjustments").forEach(items::add);Collections.reverse(items);return page(items,page,size);
    }
    public ObjectNode aiContext(CurrentUser u,long id,ObjectNode b) {
        ObjectNode p=load(u,id); String recordId=text(b,"recordId",1,64),question=text(b,"question",1,1000);
        JsonNode r=record(u,id,recordId).path("record"); ObjectNode t=findTask(p,r.path("instanceId").asText());
        // Retrieval is scoped to this owned task's published snapshot; no visibility-unchecked legacy chunks.
        ArrayNode references=arr(); if(usable(p)) for(JsonNode source:t.path("sources")) if(source.path("status").asText().equals("AVAILABLE"))references.add(source);
        ObjectNode context=obj("owner",owner(u),"planId",id,"planVersion",p.path("version"),"recordId",recordId,"question",question,"catalogRevision",content.catalogStamp(),"contentAvailable",usable(p),"references",references,
            "contextSummary",obj("task",t.path("title"),"completion",r.path("completion"),"actualMinutes",r.path("actualMinutes"),"selfAssessment",r.path("selfAssessment"),"review",r.path("review").path("message")),"safetyAlerts",alerts(p));
        context.put("contextHash",hash(context)); context.remove("owner"); return context;
    }
}
