package com.skatehub.service;

import com.skatehub.dao.TrainingPlanRepository;
import com.skatehub.dao.TrainingTaskRepository;
import com.skatehub.dao.UserTrainingPlanRepository;
import com.skatehub.dao.UserTrainingRecordRepository;
import com.skatehub.pojo.skate.TrainingPlan;
import com.skatehub.pojo.skate.TrainingTask;
import com.skatehub.pojo.skate.TrainingTaskCompleteRequest;
import com.skatehub.pojo.skate.UserTrainingPlan;
import com.skatehub.pojo.skate.UserTrainingRecord;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrainingPlanService {

    private static final int TASK_EXP = 8;
    private static final int PLAN_EXP = 30;

    private final TrainingPlanRepository planRepository;
    private final TrainingTaskRepository taskRepository;
    private final UserTrainingPlanRepository userPlanRepository;
    private final UserTrainingRecordRepository recordRepository;
    private final UserGrowthService userGrowthService;
    private final com.skatehub.dao.UserRepository userRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public List<TrainingPlan> listPlans() {
        return planRepository.findByStatusOrderByPlanIdAsc("1");
    }

    public Map<String, Object> detail(Long planId) {
        TrainingPlan plan = activePlan(planId);
        Map<String, Object> result = new HashMap<>();
        result.put("plan", plan);
        List<TrainingTask> tasks=taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(planId);
        result.put("tasks", tasks);
        result.put("scheduleCoverage", scheduleCoverage(plan.getDurationDays(),tasks));
        return result;
    }

    private Map<String,Object> scheduleCoverage(Integer declaredDays,List<TrainingTask> tasks) {
        boolean validDeclaration=declaredDays!=null&&declaredDays>0;
        java.util.SortedSet<Integer> available=new java.util.TreeSet<>();
        List<Map<String,Object>> diagnostics=new java.util.ArrayList<>();
        if(!validDeclaration)diagnostics.add(Map.of("code","INVALID_DECLARED_DAYS"));
        for(TrainingTask task:tasks){
            Integer day=task.getDayNo();
            if(validDeclaration&&day!=null&&day>=1&&day<=declaredDays)available.add(day);
            else {
                Map<String,Object> diagnostic=new HashMap<>();diagnostic.put("code","INVALID_TASK_DAY");diagnostic.put("taskId",task.getTaskId());diagnostic.put("dayNo",day);diagnostics.add(diagnostic);
            }
        }
        List<Integer> missing=new java.util.ArrayList<>();
        if(validDeclaration)for(int day=1;day<=declaredDays;day++)if(!available.contains(day))missing.add(day);
        Map<String,Object> coverage=new HashMap<>();coverage.put("declaredDays",declaredDays);coverage.put("availableDays",List.copyOf(available));coverage.put("missingDays",missing);
        coverage.put("complete",validDeclaration&&missing.isEmpty()&&diagnostics.isEmpty());
        if(!diagnostics.isEmpty())coverage.put("diagnostics",diagnostics);
        return coverage;
    }

    @Transactional
    public Map<String, Object> join(CurrentUser currentUser, Long planId) {
        Long userId = requireUserId(currentUser);
        lockUser(userId);
        if (jdbc.queryForObject("select count(*) from tb_guidance_active where user_id=?", Long.class, userId) > 0) {
            throw new com.skatehub.service.training.GuidanceException(409, "PLAN_CONFLICT", "请先退出或完成训练指导计划");
        }
        activePlan(planId);
        UserTrainingPlan userPlan = userPlanRepository
                .findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(userId, planId, "IN_PROGRESS")
                .orElse(null);
        boolean created = false;
        if (userPlan == null) {
            userPlan = new UserTrainingPlan();
            userPlan.setUserId(userId);
            userPlan.setPlanId(planId);
            userPlan = userPlanRepository.save(userPlan);
            created = true;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userPlan", userPlan);
        result.put("created", created);
        return result;
    }

    @Transactional
    public void quit(CurrentUser currentUser, Long userPlanId) {
        Long userId = requireUserId(currentUser);
        lockUser(userId);
        InputValidator.positiveId(userPlanId, "用户训练计划ID");
        UserTrainingPlan userPlan = userPlanRepository.findByUserPlanIdAndUserId(userPlanId, userId)
                .orElseThrow(() -> new BizException("训练计划记录不存在"));
        if (!"LEGACY".equals(userPlan.getMode())) throw new BizException("请使用训练指导入口退出计划");
        if (!"IN_PROGRESS".equals(userPlan.getStatus())) {
            throw new BizException("当前训练计划不可退出");
        }
        userPlan.setStatus("QUIT");
        userPlanRepository.save(userPlan);
    }

    public Map<String, Object> current(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        Map<String,Object> result=userPlanRepository.findFirstByUserIdAndModeAndStatusOrderByCreateTimeDesc(userId, "LEGACY", "IN_PROGRESS")
                .map(this::toUserPlanProgress).orElseGet(HashMap::new);
        if(jdbc.queryForObject("select count(*) from tb_guidance_active where user_id=?",Long.class,userId)>0) result.put("guidanceApi","/api/user/training-guidance/current");
        return result;
    }

    @Transactional
    public Map<String, Object> completeTask(CurrentUser currentUser, Long taskId, TrainingTaskCompleteRequest request) {
        Long userId = requireUserId(currentUser);
        lockUser(userId);
        InputValidator.positiveId(taskId, "训练任务ID");
        TrainingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException("训练任务不存在"));
        TrainingPlan plan = activePlan(task.getPlanId());
        UserTrainingPlan userPlan = userPlanRepository
                .findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(userId, task.getPlanId(), "IN_PROGRESS")
                .orElseGet(() -> userPlanRepository.findFirstByUserIdAndPlanIdAndModeOrderByCreateTimeDesc(userId, task.getPlanId(), "LEGACY")
                        .filter(p -> "COMPLETED".equals(p.getStatus())).orElseThrow(() -> new BizException("请先加入该训练计划")));
        if (!"LEGACY".equals(userPlan.getMode())) throw new BizException("请使用训练指导入口");

        if (recordRepository.existsByUserPlanIdAndTaskId(userPlan.getUserPlanId(), taskId)) {
            return progressResult(userPlan, "COMPLETED".equals(userPlan.getStatus()), 0, 0, userRepository.findById(userId).map(u -> u.getExp() == null ? 0 : u.getExp()).orElse(0));
        }

        UserTrainingRecord record = new UserTrainingRecord();
        record.setUserPlanId(userPlan.getUserPlanId());
        record.setTaskId(taskId);
        record.setUserId(userId);
        record.setPracticeMinutes(request.getPracticeMinutes());
        record.setContent(trimToNull(request.getContent()));
        record.setMediaUrls(trimToNull(request.getMediaUrls()));
        recordRepository.save(record);

        var taskAward = userGrowthService.grantExpResult(userId, "TRAINING_TASK_COMPLETE", "TRAINING_TASK", taskId, TASK_EXP);
        int totalExp = taskAward.totalExp();
        record.setExpAwarded(taskAward.delta());

        recordRepository.flush();
        var completedIds = recordRepository.findByUserPlanIdOrderByCreateTimeAsc(userPlan.getUserPlanId()).stream()
                .map(UserTrainingRecord::getTaskId).collect(java.util.stream.Collectors.toSet());
        var allTasks = taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(task.getPlanId());
        int completedDays = 0;
        for (int day = 1; day <= Math.max(1, plan.getDurationDays()); day++) {
            int d = day;
            var dayTasks = allTasks.stream().filter(t -> java.util.Objects.equals(t.getDayNo(), d)).toList();
            if (dayTasks.isEmpty() || dayTasks.stream().anyMatch(t -> !completedIds.contains(t.getTaskId()))) break;
            completedDays = day;
        }
        userPlan.setCompletedDays(completedDays);
        userPlan.setCurrentDay(completedDays + 1);

        boolean completedPlan = completedDays >= Math.max(1, plan.getDurationDays() == null ? 1 : plan.getDurationDays());
        int planExp = 0;
        if (completedPlan) {
            userPlan.setStatus("COMPLETED");
            var planAward = userGrowthService.grantExpResult(userId, "TRAINING_PLAN_COMPLETE", "USER_TRAINING_PLAN", userPlan.getUserPlanId(), PLAN_EXP);
            totalExp = planAward.totalExp(); planExp = planAward.delta();
        }
        userPlanRepository.save(userPlan);
        TrainingTask nextTask = completedPlan ? null : nextTask(userPlan);
        return progressResult(userPlan, completedPlan, record.getExpAwarded(), planExp, totalExp, nextTask);
    }

    public List<UserTrainingRecord> records(CurrentUser currentUser) {
        return recordRepository.findByUserIdOrderByCreateTimeDesc(requireUserId(currentUser));
    }

    private Map<String, Object> toUserPlanProgress(UserTrainingPlan userPlan) {
        Map<String, Object> result = new HashMap<>();
        result.put("userPlan", userPlan);
        result.put("plan", planRepository.findById(userPlan.getPlanId()).orElse(null));
        result.put("tasks", taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(userPlan.getPlanId()));
        result.put("records", recordRepository.findByUserPlanIdOrderByCreateTimeAsc(userPlan.getUserPlanId()));
        result.put("nextTask", nextTask(userPlan));
        return result;
    }

    private Map<String, Object> progressResult(UserTrainingPlan userPlan, boolean completedPlan, int taskExp, int planExp, int totalExp) {
        return progressResult(userPlan, completedPlan, taskExp, planExp, totalExp, null);
    }

    private Map<String, Object> progressResult(UserTrainingPlan userPlan, boolean completedPlan, int taskExp, int planExp, int totalExp, TrainingTask nextTask) {
        Map<String, Object> result = new HashMap<>();
        result.put("userPlan", userPlan);
        result.put("completedPlan", completedPlan);
        result.put("taskExpAwarded", taskExp);
        result.put("planExpAwarded", planExp);
        result.put("totalExp", totalExp);
        result.put("nextTask", nextTask);
        result.put("recommendedNextAction", recommendedNextAction(completedPlan, nextTask));
        return result;
    }

    private TrainingTask nextTask(UserTrainingPlan userPlan) {
        if (userPlan == null || userPlan.getPlanId() == null || !"IN_PROGRESS".equals(userPlan.getStatus())) {
            return null;
        }
        int currentDay = userPlan.getCurrentDay() == null ? 1 : userPlan.getCurrentDay();
        var completed=recordRepository.findByUserPlanIdOrderByCreateTimeAsc(userPlan.getUserPlanId()).stream().map(UserTrainingRecord::getTaskId).collect(java.util.stream.Collectors.toSet());
        return taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(userPlan.getPlanId()).stream()
                .filter(task -> !completed.contains(task.getTaskId()))
                .filter(task -> task.getDayNo() != null && task.getDayNo() >= currentDay)
                .findFirst()
                .orElse(null);
    }

    private String recommendedNextAction(boolean completedPlan, TrainingTask nextTask) {
        if (completedPlan) {
            return "Plan complete: review your records and choose another plan.";
        }
        if (nextTask == null || !StringUtils.hasText(nextTask.getTaskTitle())) {
            return "Next: keep the current plan moving with the next unlocked task.";
        }
        return "Next: " + nextTask.getTaskTitle().trim();
    }

    private TrainingPlan activePlan(Long planId) {
        InputValidator.positiveId(planId, "训练计划ID");
        TrainingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BizException("训练计划不存在"));
        if (!"1".equals(plan.getStatus())) {
            throw new BizException("训练计划已停用");
        }
        return plan;
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private void lockUser(Long id) {
        userRepository.findByIdForUpdate(id).orElseThrow(() -> new BizException("用户不存在"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
