package com.javademo1.service;

import com.javademo1.dao.ActivityRepository;
import com.javademo1.dao.ActivitySignRepository;
import com.javademo1.dao.UserRepository;
import com.javademo1.pojo.Activity;
import com.javademo1.pojo.ActivitySign;
import com.javademo1.pojo.User;
import com.javademo1.pojo.activity.ActivityCreateRequest;
import com.javademo1.util.ActivityReviewStatus;
import com.javademo1.util.ActivitySignStatus;
import com.javademo1.util.ActivityStatus;
import com.javademo1.util.BizException;
import com.javademo1.util.CurrentUser;
import com.javademo1.util.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivitySignRepository activitySignRepository;
    private final UserRepository userRepository;
    private final MessageNotifyService messageNotifyService;
    private final UserGrowthService userGrowthService;

    public PageResult<Map<String, Object>> list(Integer page, Integer size, Long userId, String city, String district, String keyword) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentSize = size == null || size < 1 ? 10 : Math.min(size, 50);

        String cityLike = normalizeKeyword(city);
        String districtLike = normalizeKeyword(district);
        String keywordLike = normalizeKeyword(keyword);

        Page<Activity> activityPage = activityRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Predicate publicVisiblePredicate = builder.and(
                    builder.equal(root.get("reviewStatus"), ActivityReviewStatus.APPROVED),
                    builder.coalesce(root.get("activityStatus"), root.get("status"))
                            .in(ActivityStatus.SIGNUP_OPEN, ActivityStatus.FULL, ActivityStatus.ENDED)
            );
            predicates.add(publicVisiblePredicate);

            if (StringUtils.hasText(cityLike)) {
                String likeCity = "%" + cityLike + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("city")), likeCity),
                        builder.like(builder.lower(root.get("place")), likeCity),
                        builder.like(builder.lower(root.get("address")), likeCity)
                ));
            }
            if (StringUtils.hasText(districtLike)) {
                String likeDistrict = "%" + districtLike + "%";
                predicates.add(builder.like(builder.lower(root.get("district")), likeDistrict));
            }
            if (StringUtils.hasText(keywordLike)) {
                String likeKeyword = "%" + keywordLike + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), likeKeyword),
                        builder.like(builder.lower(root.get("content")), likeKeyword),
                        builder.like(builder.lower(root.get("place")), likeKeyword),
                        builder.like(builder.lower(root.get("address")), likeKeyword),
                        builder.like(builder.lower(root.get("city")), likeKeyword),
                        builder.like(builder.lower(root.get("district")), likeKeyword)
                ));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(currentPage - 1, currentSize, Sort.by(Sort.Direction.DESC, "createTime")));

        List<Map<String, Object>> list = activityPage.getContent().stream()
                .map(item -> toActivityVO(item, userId))
                .collect(Collectors.toList());
        return new PageResult<>(activityPage.getTotalElements(), list);
    }

    public Map<String, Object> publicDetail(Long activityId) {
        Activity activity = getActivity(activityId);
        if (!ActivityReviewStatus.APPROVED.equals(activity.getReviewStatus())) {
            throw new BizException("活动暂未公开");
        }
        String status = resolveActivityStatus(activity);
        if (ActivityStatus.CANCELED.equals(status) || ActivityStatus.PENDING_REVIEW.equals(status) || ActivityStatus.REVIEW_REJECTED.equals(status)) {
            throw new BizException("活动暂不可查看");
        }
        return toActivityVO(activity, null);
    }

    public Activity create(CurrentUser currentUser, ActivityCreateRequest request) {
        validateCreateRequest(request);
        Activity activity = new Activity();
        activity.setUserId(currentUser.id());
        activity.setTitle(trimToNull(request.getTitle()));
        String activityDesc = trimToNull(request.getActivityDesc());
        activity.setContent(activityDesc);
        activity.setActivityType(trimToNull(request.getActivityType()));
        activity.setPlace(trimToNull(request.getPlace()));
        activity.setPlaceId(request.getPlaceId());
        activity.setAddress(trimToNull(request.getAddress()));
        activity.setCity(trimToNull(request.getCity()));
        activity.setDistrict(trimToNull(request.getDistrict()));
        activity.setLongitude(request.getLongitude());
        activity.setLatitude(request.getLatitude());
        activity.setActivityTime(request.getActivityTime());
        activity.setMaxNum(normalizeMaxNum(request.getMaxNum()));
        activity.setReviewStatus(ActivityReviewStatus.PENDING);
        activity.setActivityStatus(ActivityStatus.PENDING_REVIEW);
        activity.setStatus(ActivityStatus.PENDING_REVIEW);
        return activityRepository.save(activity);
    }

    public Map<String, Object> sign(CurrentUser currentUser, Long activityId) {
        Activity activity = getActivity(activityId);
        if (!ActivityStatus.SIGNUP_OPEN.equals(resolveActivityStatus(activity))) {
            throw new BizException("该活动当前不可报名");
        }
        if (activity.getMaxNum() != null && activity.getSignNum() >= activity.getMaxNum()) {
            throw new BizException("活动名额已满");
        }

        ActivitySign sign = activitySignRepository.findByActivityIdAndUserId(activityId, currentUser.id()).orElse(null);
        if (sign == null) {
            sign = new ActivitySign();
            sign.setActivityId(activityId);
            sign.setUserId(currentUser.id());
        } else if (ActivitySignStatus.PENDING_CONFIRM.equals(sign.getSignStatus())
                || ActivitySignStatus.APPROVED.equals(sign.getSignStatus())) {
            throw new BizException("已提交报名，请勿重复操作");
        }
        sign.setSignStatus(ActivitySignStatus.PENDING_CONFIRM);
        sign.setIsCheckin("0");
        activitySignRepository.save(sign);
        userGrowthService.grantExp(currentUser.id(), "ACTIVITY_SIGN", "ACTIVITY", activityId, 10);

        if (!activity.getUserId().equals(currentUser.id())) {
            messageNotifyService.send(activity.getUserId(), "ACTIVITY", "你的活动有新报名：" + activity.getTitle(), "ACTIVITY", activity.getActivityId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("signNum", activity.getSignNum());
        result.put("message", "报名已提交，等待发起人确认");
        return result;
    }

    public void checkin(CurrentUser currentUser, Long activityId) {
        ActivitySign sign = activitySignRepository.findByActivityIdAndUserId(activityId, currentUser.id())
                .orElseThrow(() -> new BizException("请先报名该活动后再签到"));
        if (!ActivitySignStatus.APPROVED.equals(sign.getSignStatus())) {
            throw new BizException("仅已通过报名可签到");
        }
        if ("1".equals(sign.getIsCheckin())) {
            throw new BizException("你已签到，请勿重复操作");
        }
        sign.setIsCheckin("1");
        activitySignRepository.save(sign);
        userGrowthService.grantExp(currentUser.id(), "ACTIVITY_CHECKIN", "ACTIVITY", activityId, 15);
    }

    public List<ActivitySign> signs(CurrentUser currentUser, Long activityId) {
        Activity activity = getActivity(activityId);
        if (!activity.getUserId().equals(currentUser.id()) && !"ADMIN".equals(currentUser.role())) {
            throw new BizException("无权查看报名信息");
        }
        return activitySignRepository.findByActivityId(activityId);
    }

    public ActivitySign updateSignStatus(CurrentUser currentUser, Long activityId, Long signId, String signStatus) {
        Activity activity = getActivity(activityId);
        if (!activity.getUserId().equals(currentUser.id()) && !"ADMIN".equals(currentUser.role())) {
            throw new BizException("无权处理报名");
        }
        ActivitySign sign = activitySignRepository.findBySignIdAndActivityId(signId, activityId)
                .orElseThrow(() -> new BizException("报名记录不存在"));

        String beforeStatus = sign.getSignStatus();
        if (ActivitySignStatus.APPROVED.equals(signStatus)) {
            if (activity.getMaxNum() != null && activity.getSignNum() >= activity.getMaxNum()) {
                throw new BizException("活动名额已满，无法继续通过");
            }
            sign.setSignStatus(ActivitySignStatus.APPROVED);
        } else if (ActivitySignStatus.REJECTED.equals(signStatus)) {
            sign.setSignStatus(ActivitySignStatus.REJECTED);
            sign.setIsCheckin("0");
        } else if (ActivitySignStatus.CANCELED.equals(signStatus)) {
            sign.setSignStatus(ActivitySignStatus.CANCELED);
            sign.setIsCheckin("0");
        } else {
            throw new BizException("报名状态不合法");
        }
        if (ActivitySignStatus.APPROVED.equals(beforeStatus)
                && (ActivitySignStatus.REJECTED.equals(signStatus) || ActivitySignStatus.CANCELED.equals(signStatus))) {
            sign.setIsCheckin("0");
        }

        ActivitySign saved = activitySignRepository.save(sign);
        if (!sign.getUserId().equals(currentUser.id())) {
            String actionText = ActivitySignStatus.APPROVED.equals(signStatus) ? "已通过" : "未通过";
            messageNotifyService.send(sign.getUserId(), "ACTIVITY", "你报名的活动" + actionText + "：" + activity.getTitle(), "ACTIVITY", activity.getActivityId());
        }
        refreshActivityCapacityAndStatus(activity);
        return saved;
    }

    public Activity closeSignup(CurrentUser currentUser, Long activityId) {
        Activity activity = getActivity(activityId);
        if (!activity.getUserId().equals(currentUser.id()) && !"ADMIN".equals(currentUser.role())) {
            throw new BizException("无权关闭报名");
        }
        if (!ActivityReviewStatus.APPROVED.equals(activity.getReviewStatus())) {
            throw new BizException("仅审核通过活动可关闭报名");
        }
        activity.setActivityStatus(ActivityStatus.ENDED);
        activity.setStatus(ActivityStatus.ENDED);
        return activityRepository.save(activity);
    }

    public void cancelMySign(CurrentUser currentUser, Long activityId) {
        Activity activity = getActivity(activityId);
        String activityStatus = resolveActivityStatus(activity);
        if (ActivityStatus.CANCELED.equals(activityStatus) || ActivityStatus.ENDED.equals(activityStatus)) {
            throw new BizException("活动已取消或已结束，无法退出报名");
        }
        ActivitySign sign = activitySignRepository.findByActivityIdAndUserId(activityId, currentUser.id())
                .orElseThrow(() -> new BizException("报名记录不存在"));
        if (!(ActivitySignStatus.PENDING_CONFIRM.equals(sign.getSignStatus()) || ActivitySignStatus.APPROVED.equals(sign.getSignStatus()))) {
            throw new BizException("当前报名状态不可取消");
        }
        sign.setSignStatus(ActivitySignStatus.CANCELED);
        sign.setIsCheckin("0");
        activitySignRepository.save(sign);
        refreshActivityCapacityAndStatus(activity);
    }

    public List<Map<String, Object>> mySignedActivities(Long userId) {
        List<ActivitySign> signList = activitySignRepository.findByUserIdOrderBySignTimeDesc(userId);
        List<Long> activityIds = signList.stream().map(ActivitySign::getActivityId).distinct().toList();
        Map<Long, Activity> activityMap = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getActivityId, item -> item));

        return signList.stream()
                .map(sign -> {
                    Activity activity = activityMap.get(sign.getActivityId());
                    if (activity == null) return null;
                    Map<String, Object> map = toActivityVO(activity, userId);
                    map.put("signId", sign.getSignId());
                    map.put("signTime", sign.getSignTime());
                    map.put("isCheckin", sign.getIsCheckin());
                    map.put("signStatus", sign.getSignStatus());
                    return map;
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> myPublishedActivities(Long userId) {
        return activityRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(activity -> toActivityVO(activity, userId))
                .collect(Collectors.toList());
    }

    public Activity updateStatus(Long activityId, String status) {
        Activity activity = getActivity(activityId);
        String normalizedStatus = normalizeActivityStatus(status);
        activity.setActivityStatus(normalizedStatus);
        activity.setStatus(normalizedStatus);
        if (ActivityStatus.PENDING_REVIEW.equals(normalizedStatus)) {
            activity.setReviewStatus(ActivityReviewStatus.PENDING);
        } else if (ActivityStatus.REVIEW_REJECTED.equals(normalizedStatus)) {
            activity.setReviewStatus(ActivityReviewStatus.REJECTED);
        } else {
            activity.setReviewStatus(ActivityReviewStatus.APPROVED);
        }
        return activityRepository.save(activity);
    }

    public Activity review(Long activityId, String reviewStatus) {
        Activity activity = getActivity(activityId);
        if (ActivityReviewStatus.APPROVED.equals(reviewStatus)) {
            activity.setReviewStatus(ActivityReviewStatus.APPROVED);
            activity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
            activity.setStatus(ActivityStatus.SIGNUP_OPEN);
        } else if (ActivityReviewStatus.REJECTED.equals(reviewStatus)) {
            activity.setReviewStatus(ActivityReviewStatus.REJECTED);
            activity.setActivityStatus(ActivityStatus.REVIEW_REJECTED);
            activity.setStatus(ActivityStatus.REVIEW_REJECTED);
        } else {
            throw new BizException("审核状态不合法");
        }
        return activityRepository.save(activity);
    }

    public void delete(CurrentUser currentUser, Long activityId, boolean isAdmin) {
        Activity activity = getActivity(activityId);
        boolean isRealAdmin = "ADMIN".equalsIgnoreCase(currentUser.role());
        boolean isOwner = activity.getUserId().equals(currentUser.id());
        if (!isOwner && !isRealAdmin) {
            throw new BizException("无权删除活动");
        }
        if (isRealAdmin && isAdmin) {
            activityRepository.delete(activity);
            return;
        }
        activity.setActivityStatus(ActivityStatus.CANCELED);
        activity.setStatus(ActivityStatus.CANCELED);
        activityRepository.save(activity);
    }

    public Activity getActivity(Long activityId) {
        return activityRepository.findById(activityId).orElseThrow(() -> new BizException("活动不存在"));
    }

    private Map<String, Object> toActivityVO(Activity activity, Long userId) {
        Map<String, Object> map = new HashMap<>();
        String activityStatus = resolveActivityStatus(activity);
        map.put("activityId", activity.getActivityId());
        map.put("userId", activity.getUserId());
        map.put("title", activity.getTitle());
        map.put("content", activity.getContent());
        map.put("activityDesc", activity.getContent());
        map.put("place", activity.getPlace());
        map.put("placeId", activity.getPlaceId());
        map.put("address", activity.getAddress());
        map.put("city", activity.getCity());
        map.put("district", activity.getDistrict());
        map.put("longitude", activity.getLongitude());
        map.put("latitude", activity.getLatitude());
        map.put("activityTime", activity.getActivityTime());
        map.put("maxNum", activity.getMaxNum());
        map.put("signNum", activity.getSignNum());
        map.put("activityType", activity.getActivityType());
        map.put("reviewStatus", activity.getReviewStatus());
        map.put("activityStatus", activityStatus);
        map.put("status", activityStatus);
        map.put("createTime", activity.getCreateTime());
        User user = userRepository.findById(activity.getUserId()).orElse(null);
        map.put("publisherName", user == null ? "已注销用户" : user.getUsername());
        if (userId != null) {
            boolean signed = activitySignRepository.findByActivityIdAndUserId(activity.getActivityId(), userId)
                    .map(item -> ActivitySignStatus.PENDING_CONFIRM.equals(item.getSignStatus())
                            || ActivitySignStatus.APPROVED.equals(item.getSignStatus()))
                    .orElse(false);
            map.put("signed", signed);
        }
        return map;
    }

    private void refreshActivityCapacityAndStatus(Activity activity) {
        int approvedCount = (int) activitySignRepository.countByActivityIdAndSignStatus(activity.getActivityId(), ActivitySignStatus.APPROVED);
        activity.setSignNum(approvedCount);
        if (activity.getMaxNum() != null && approvedCount >= activity.getMaxNum()) {
            activity.setActivityStatus(ActivityStatus.FULL);
            activity.setStatus(ActivityStatus.FULL);
        } else if (ActivityReviewStatus.APPROVED.equals(activity.getReviewStatus())) {
            activity.setActivityStatus(ActivityStatus.SIGNUP_OPEN);
            activity.setStatus(ActivityStatus.SIGNUP_OPEN);
        }
        activityRepository.save(activity);
    }

    private String resolveActivityStatus(Activity activity) {
        if (StringUtils.hasText(activity.getActivityStatus())) {
            return activity.getActivityStatus();
        }
        return normalizeActivityStatus(activity.getStatus());
    }

    private String normalizeActivityStatus(String status) {
        if (ActivityStatus.isValid(status)) {
            return status;
        }
        if ("0".equals(status)) {
            return ActivityStatus.SIGNUP_OPEN;
        }
        if ("1".equals(status)) {
            return ActivityStatus.ENDED;
        }
        throw new BizException("活动状态不合法");
    }

    private void validateCreateRequest(ActivityCreateRequest request) {
        String title = trimToNull(request.getTitle());
        String activityType = trimToNull(request.getActivityType());
        String place = trimToNull(request.getPlace());
        String activityDesc = trimToNull(request.getActivityDesc());
        Integer maxNum = request.getMaxNum();
        LocalDateTime activityTime = request.getActivityTime();

        if (!StringUtils.hasText(title)) {
            throw new BizException("活动标题不能为空");
        }
        if (!StringUtils.hasText(activityType)) {
            throw new BizException("活动类型不能为空");
        }
        if (!StringUtils.hasText(place)) {
            throw new BizException("活动地点不能为空");
        }
        if (!StringUtils.hasText(activityDesc)) {
            throw new BizException("活动说明不能为空");
        }
        if (activityDesc.length() < 10) {
            throw new BizException("活动说明不能过短");
        }
        if (activityTime == null) {
            throw new BizException("活动时间不能为空");
        }
        if (activityTime.isBefore(LocalDateTime.now())) {
            throw new BizException("活动时间不能早于当前时间");
        }
        if (maxNum == null) {
            throw new BizException("人数上限不能为空");
        }
        if (maxNum < 2 || maxNum > 6) {
            throw new BizException("普通用户发布活动人数上限需在 2 到 6 人之间");
        }
    }

    private Integer normalizeMaxNum(Integer maxNum) {
        if (maxNum == null || maxNum <= 0) {
            return null;
        }
        return maxNum;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
