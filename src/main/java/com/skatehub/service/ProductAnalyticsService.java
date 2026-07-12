package com.skatehub.service;

import com.skatehub.dao.ProductEventRepository;
import com.skatehub.pojo.ProductEvent;
import com.skatehub.pojo.analytics.ProductEventRequest;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductAnalyticsService {

    private final ProductEventRepository productEventRepository;

    public void record(CurrentUser currentUser, ProductEventRequest request) {
        if (request == null || !StringUtils.hasText(request.getEventType())) {
            return;
        }
        ProductEvent event = new ProductEvent();
        event.setEventType(request.getEventType().trim());
        event.setUserId(currentUser == null ? null : currentUser.id());
        event.setTargetType(trim(request.getTargetType()));
        event.setTargetId(request.getTargetId());
        event.setSourcePage(trim(request.getSourcePage()));
        productEventRepository.save(event);
    }

    public Map<String, Object> summary7d() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trend7d", trend7d());
        result.put("recentEvents", productEventRepository.findTop100ByOrderByCreateTimeDesc().stream()
                .limit(20)
                .map(this::toMap)
                .toList());
        return result;
    }

    private List<Map<String, Object>> trend7d() {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("events", productEventRepository.countByCreateTimeBetween(start, end));
            row.put("activeUsers", productEventRepository.countDistinctUsersBetween(start, end));
            row.put("activityViews", productEventRepository.countByEventTypeAndCreateTimeBetween("VIEW_ACTIVITY", start, end));
            row.put("activitySigns", productEventRepository.countByEventTypeAndCreateTimeBetween("SIGN_ACTIVITY", start, end));
            row.put("aiCoachQuestions", productEventRepository.countByEventTypeAndCreateTimeBetween("ASK_AI_COACH", start, end));
            row.put("trainingCompletions", productEventRepository.countByEventTypeAndCreateTimeBetween("COMPLETE_TRAINING_TASK", start, end));
            row.put("contentCreates", productEventRepository.countByEventTypeAndCreateTimeBetween("PUBLISH_POST", start, end)
                    + productEventRepository.countByEventTypeAndCreateTimeBetween("PUBLISH_VIDEO", start, end));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toMap(ProductEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventType", event.getEventType());
        map.put("targetType", event.getTargetType());
        map.put("targetId", event.getTargetId());
        map.put("sourcePage", event.getSourcePage());
        map.put("createTime", event.getCreateTime());
        return map;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
