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
        InputValidator.positiveId(userPlanId, "用户训练计划ID");
        UserTrainingPlan userPlan = userPlanRepository.findByUserPlanIdAndUserId(userPlanId, userId)
                .orElseThrow(() -> new BizException("训练计划记录不存在"));
        if (!"IN_PROGRESS".equals(userPlan.getStatus())) {
            throw new BizException("当前训练计划不可退出");
        }
        userPlan.setStatus("QUIT");
        userPlanRepository.save(userPlan);
    }

    public Map<String, Object> current(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        return userPlanRepository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(userId, "IN_PROGRESS")
                .map(this::toUserPlanProgress)
                .orElseGet(HashMap::new);
    }

    @Transactional
    public Map<String, Object> completeTask(CurrentUser currentUser, Long taskId, TrainingTaskCompleteRequest request) {
        Long userId = requireUserId(currentUser);
        InputValidator.positiveId(taskId, "训练任务ID");
        TrainingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException("训练任务不存在"));
        TrainingPlan plan = activePlan(task.getPlanId());
        UserTrainingPlan userPlan = userPlanRepository
                .findFirstByUserIdAndPlanIdAndStatusOrderByCreateTimeDesc(userId, task.getPlanId(), "IN_PROGRESS")
                .orElseThrow(() -> new BizException("请先加入该训练计划"));

        if (recordRepository.existsByUserPlanIdAndTaskId(userPlan.getUserPlanId(), taskId)) {
            return progressResult(userPlan, false, 0, 0, 0);
        }

        UserTrainingRecord record = new UserTrainingRecord();
        record.setUserPlanId(userPlan.getUserPlanId());
        record.setTaskId(taskId);
        record.setUserId(userId);
        record.setPracticeMinutes(request.getPracticeMinutes());
        record.setContent(trimToNull(request.getContent()));
        record.setMediaUrls(trimToNull(request.getMediaUrls()));
        recordRepository.save(record);

        int totalExp = userGrowthService.grantExp(userId, "TRAINING_TASK_COMPLETE", "TRAINING_TASK", taskId, TASK_EXP);
        record.setExpAwarded(totalExp > 0 ? TASK_EXP : 0);

        int completedDays = Math.max(userPlan.getCompletedDays() == null ? 0 : userPlan.getCompletedDays(), task.getDayNo());
        userPlan.setCompletedDays(completedDays);
        userPlan.setCurrentDay(completedDays + 1);

        boolean completedPlan = completedDays >= Math.max(1, plan.getDurationDays() == null ? 1 : plan.getDurationDays());
        int planExp = 0;
        if (completedPlan) {
            userPlan.setStatus("COMPLETED");
            totalExp = userGrowthService.grantExp(userId, "TRAINING_PLAN_COMPLETE", "USER_TRAINING_PLAN", userPlan.getUserPlanId(), PLAN_EXP);
            planExp = totalExp > 0 ? PLAN_EXP : 0;
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
        return taskRepository.findByPlanIdOrderByDayNoAscSortOrderAscTaskIdAsc(userPlan.getPlanId()).stream()
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
