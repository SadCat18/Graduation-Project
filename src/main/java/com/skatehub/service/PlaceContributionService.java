package com.skatehub.service;

import com.skatehub.dao.PlaceRepository;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.place.PlaceContributionRequest;
import com.skatehub.pojo.place.PlaceContributionReviewRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.InputValidator;
import com.skatehub.util.PlaceVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlaceContributionService {

    private static final int APPROVAL_EXP = 20;

    private final PlaceRepository placeRepository;
    private final UserGrowthService userGrowthService;

    @Transactional
    public Place submit(CurrentUser currentUser, PlaceContributionRequest request) {
        Long userId = requireUserId(currentUser);
        Place place = new Place();
        place.setName(trimRequired(request.getName(), "Place name"));
        place.setAddress(trimToNull(request.getAddress()));
        place.setIntro(trimToNull(request.getIntro()));
        place.setScore(BigDecimal.ZERO);
        place.setReviewCount(0);
        place.setSubmittedByUserId(userId);
        place.setVerificationStatus(PlaceVerificationStatus.PENDING);
        return placeRepository.save(place);
    }

    @Transactional(readOnly = true)
    public List<Place> pending() {
        return placeRepository.findByVerificationStatusOrderByCreateTimeDesc(PlaceVerificationStatus.PENDING);
    }

    @Transactional
    public Place review(Long placeId, CurrentUser admin, PlaceContributionReviewRequest request) {
        InputValidator.positiveId(placeId, "Place ID");
        Long adminId = requireUserId(admin);
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BizException("Place not found"));
        if (!PlaceVerificationStatus.PENDING.equals(place.getVerificationStatus())) {
            throw new BizException("Only pending place contributions can be reviewed");
        }

        String status = normalizeStatus(request.getStatus());
        place.setVerificationStatus(status);
        place.setVerificationNote(trimToNull(request.getNote()));
        place.setVerifiedByAdminId(adminId);
        place.setVerifiedTime(LocalDateTime.now());
        Place saved = placeRepository.save(place);

        if (PlaceVerificationStatus.APPROVED.equals(status) && saved.getSubmittedByUserId() != null) {
            userGrowthService.grantExp(
                    saved.getSubmittedByUserId(),
                    "PLACE_CONTRIBUTION_APPROVED",
                    "PLACE",
                    saved.getPlaceId(),
                    APPROVAL_EXP);
        }
        return saved;
    }

    private String normalizeStatus(String status) {
        String normalized = trimRequired(status, "Review status").toUpperCase(Locale.ROOT);
        if (!PlaceVerificationStatus.REVIEW_STATUSES.contains(normalized)) {
            throw new BizException("Review status must be APPROVED or REJECTED");
        }
        return normalized;
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("User must be authenticated");
        }
        return currentUser.id();
    }

    private String trimRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
