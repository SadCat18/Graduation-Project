package com.skatehub.service;

import com.skatehub.dao.EventSignupRepository;
import com.skatehub.dao.SkateEventRepository;
import com.skatehub.pojo.skate.EventSignup;
import com.skatehub.pojo.skate.SkateEvent;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SkateEventService {

    private static final int SIGNUP_EXP = 5;
    private static final int CHECKIN_EXP = 20;

    private final SkateEventRepository eventRepository;
    private final EventSignupRepository signupRepository;
    private final UserGrowthService userGrowthService;

    public PageResult<Map<String, Object>> list(Integer page, Integer size, Long userId, String city, String eventType, String status) {
        int currentPage = InputValidator.page(page);
        int currentSize = InputValidator.size(size);
        var eventPage = eventRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(city)) {
                predicates.add(builder.like(builder.lower(root.get("city")), InputValidator.likeKeyword(city, 50, "城市"), '\\'));
            }
            if (StringUtils.hasText(eventType)) {
                predicates.add(builder.equal(root.get("eventType"), eventType.trim()));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(builder.equal(root.get("status"), status.trim()));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(currentPage - 1, currentSize, Sort.by(Sort.Direction.ASC, "startTime")));
        return new PageResult<>(eventPage.getTotalElements(), eventPage.getContent().stream().map(event -> toEventVO(event, userId)).toList());
    }

    public Map<String, Object> detail(Long eventId, Long userId) {
        return toEventVO(getEvent(eventId), userId);
    }

    @Transactional
    public Map<String, Object> signup(CurrentUser currentUser, Long eventId) {
        Long userId = requireUserId(currentUser);
        InputValidator.positiveId(eventId, "赛事ID");
        SkateEvent event = eventRepository.findByEventIdAndStatus(eventId, "SIGNUP_OPEN")
                .orElseThrow(() -> new BizException("赛事当前不可报名"));
        LocalDateTime now = LocalDateTime.now();
        if (event.getSignupStartTime() != null && now.isBefore(event.getSignupStartTime())) {
            throw new BizException("赛事报名尚未开始");
        }
        if (event.getSignupEndTime() != null && now.isAfter(event.getSignupEndTime())) {
            throw new BizException("赛事报名已截止");
        }
        long signedCount = signupRepository.countByEventIdAndSignupStatus(eventId, "SIGNED");
        if (event.getMaxParticipants() != null && signedCount >= event.getMaxParticipants()) {
            throw new BizException("赛事报名人数已满");
        }

        EventSignup signup = signupRepository.findByEventIdAndUserId(eventId, userId).orElse(null);
        if (signup != null && "SIGNED".equals(signup.getSignupStatus())) {
            throw new BizException("已报名该赛事，请勿重复报名");
        }
        if (signup == null) {
            signup = new EventSignup();
            signup.setEventId(eventId);
            signup.setUserId(userId);
        }
        signup.setSignupStatus("SIGNED");
        signup.setCancelTime(null);
        signupRepository.save(signup);

        int totalExp = userGrowthService.grantExp(userId, "SKATE_EVENT_SIGNUP", "SKATE_EVENT", eventId, SIGNUP_EXP);
        Map<String, Object> result = new HashMap<>();
        result.put("signupStatus", "SIGNED");
        result.put("expAwarded", totalExp > 0 ? SIGNUP_EXP : 0);
        result.put("totalExp", totalExp);
        return result;
    }

    @Transactional
    public void cancel(CurrentUser currentUser, Long eventId) {
        Long userId = requireUserId(currentUser);
        SkateEvent event = getEvent(eventId);
        if ("ONGOING".equals(event.getStatus()) || "ENDED".equals(event.getStatus())) {
            throw new BizException("赛事已开始或结束，无法取消报名");
        }
        EventSignup signup = signupRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BizException("报名记录不存在"));
        if (!"SIGNED".equals(signup.getSignupStatus())) {
            throw new BizException("当前报名状态不可取消");
        }
        signup.setSignupStatus("CANCELED");
        signup.setCancelTime(LocalDateTime.now());
        signupRepository.save(signup);
    }

    @Transactional
    public Map<String, Object> checkin(CurrentUser currentUser, Long eventId) {
        Long userId = requireUserId(currentUser);
        getEvent(eventId);
        EventSignup signup = signupRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BizException("请先报名赛事"));
        if (!"SIGNED".equals(signup.getSignupStatus()) && !"ATTENDED".equals(signup.getSignupStatus())) {
            throw new BizException("当前报名状态不可签到");
        }
        if ("ATTENDED".equals(signup.getSignupStatus())) {
            Map<String, Object> duplicate = new HashMap<>();
            duplicate.put("signupStatus", "ATTENDED");
            duplicate.put("expAwarded", 0);
            return duplicate;
        }
        signup.setSignupStatus("ATTENDED");
        signup.setCheckinTime(LocalDateTime.now());
        signupRepository.save(signup);
        int totalExp = userGrowthService.grantExp(userId, "SKATE_EVENT_CHECKIN", "SKATE_EVENT", eventId, CHECKIN_EXP);
        Map<String, Object> result = new HashMap<>();
        result.put("signupStatus", "ATTENDED");
        result.put("expAwarded", totalExp > 0 ? CHECKIN_EXP : 0);
        result.put("totalExp", totalExp);
        return result;
    }

    public List<Map<String, Object>> myEvents(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        return signupRepository.findByUserIdOrderBySignupTimeDesc(userId).stream()
                .map(signup -> {
                    SkateEvent event = eventRepository.findById(signup.getEventId()).orElse(null);
                    if (event == null) return null;
                    Map<String, Object> map = toEventVO(event, userId);
                    map.put("signup", signup);
                    return map;
                })
                .filter(item -> item != null)
                .toList();
    }

    private Map<String, Object> toEventVO(SkateEvent event, Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("event", event);
        map.put("signedCount", signupRepository.countByEventIdAndSignupStatus(event.getEventId(), "SIGNED"));
        if (userId != null) {
            map.put("signupStatus", signupRepository.findByEventIdAndUserId(event.getEventId(), userId)
                    .map(EventSignup::getSignupStatus)
                    .orElse(null));
        }
        return map;
    }

    private SkateEvent getEvent(Long eventId) {
        InputValidator.positiveId(eventId, "赛事ID");
        return eventRepository.findById(eventId).orElseThrow(() -> new BizException("赛事不存在"));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }
}
