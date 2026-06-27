package com.skatehub.service;

import com.skatehub.dao.SkateTrickRepository;
import com.skatehub.dao.TrickCheckinRepository;
import com.skatehub.pojo.skate.SkateTrick;
import com.skatehub.pojo.skate.TrickCheckin;
import com.skatehub.pojo.skate.TrickCheckinRequest;
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
public class TrickService {

    private static final int CHECKIN_EXP = 5;

    private final SkateTrickRepository trickRepository;
    private final TrickCheckinRepository checkinRepository;
    private final UserGrowthService userGrowthService;

    public List<SkateTrick> list(String category) {
        if (StringUtils.hasText(category)) {
            return trickRepository.findByCategoryAndStatusOrderBySortOrderAscTrickIdAsc(category.trim(), "1");
        }
        return trickRepository.findByStatusOrderBySortOrderAscTrickIdAsc("1");
    }

    public SkateTrick detail(Long trickId) {
        InputValidator.positiveId(trickId, "动作ID");
        return activeTrick(trickId);
    }

    @Transactional
    public Map<String, Object> submitCheckin(CurrentUser currentUser, Long trickId, TrickCheckinRequest request) {
        Long userId = requireUserId(currentUser);
        InputValidator.positiveId(trickId, "动作ID");
        activeTrick(trickId);

        TrickCheckin checkin = new TrickCheckin();
        checkin.setUserId(userId);
        checkin.setTrickId(trickId);
        checkin.setPracticeMinutes(request.getPracticeMinutes());
        checkin.setMasterStatus(defaultString(request.getMasterStatus(), "PRACTICING"));
        checkin.setContent(trimToNull(request.getContent()));
        checkin.setMediaUrls(trimToNull(request.getMediaUrls()));
        checkin.setIsPublic(defaultString(request.getIsPublic(), "0"));
        checkin = checkinRepository.save(checkin);

        int totalExp = userGrowthService.grantExp(userId, "TRICK_CHECKIN", "TRICK_CHECKIN", checkin.getCheckinId(), CHECKIN_EXP);
        checkin.setExpAwarded(totalExp > 0 ? CHECKIN_EXP : 0);

        Map<String, Object> result = new HashMap<>();
        result.put("checkin", checkin);
        result.put("expAwarded", checkin.getExpAwarded());
        result.put("totalExp", totalExp);
        return result;
    }

    public List<TrickCheckin> history(CurrentUser currentUser, Long trickId) {
        Long userId = requireUserId(currentUser);
        InputValidator.positiveId(trickId, "动作ID");
        return checkinRepository.findByUserIdAndTrickIdOrderByCreateTimeDesc(userId, trickId);
    }

    public List<TrickCheckin> myCheckins(CurrentUser currentUser) {
        return checkinRepository.findByUserIdOrderByCreateTimeDesc(requireUserId(currentUser));
    }

    private SkateTrick activeTrick(Long trickId) {
        return trickRepository.findByTrickIdAndStatus(trickId, "1")
                .orElseThrow(() -> new BizException("滑板动作不存在或已停用"));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
