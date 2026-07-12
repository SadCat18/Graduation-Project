package com.skatehub.service;

import com.skatehub.dao.PlaceRepository;
import com.skatehub.pojo.Place;
import com.skatehub.pojo.place.PlaceContributionRequest;
import com.skatehub.pojo.place.PlaceContributionReviewRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceContributionServiceTest {

    @Test
    void userContributionStartsPendingAndKeepsPublicScoreNeutral() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        UserGrowthService userGrowthService = mock(UserGrowthService.class);
        PlaceContributionService service = new PlaceContributionService(placeRepository, userGrowthService);
        PlaceContributionRequest request = new PlaceContributionRequest();
        request.setName("Riverside DIY Spot");
        request.setAddress("North bridge");
        request.setIntro("Flat ground, ledge, and lights.");

        service.submit(new CurrentUser(9L, "USER", "skater"), request);

        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(captor.capture());
        Place saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Riverside DIY Spot");
        assertThat(saved.getSubmittedByUserId()).isEqualTo(9L);
        assertThat(saved.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(saved.getScore()).isZero();
    }

    @Test
    void approvalPublishesPlaceAndRewardsContributorOnce() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        UserGrowthService userGrowthService = mock(UserGrowthService.class);
        PlaceContributionService service = new PlaceContributionService(placeRepository, userGrowthService);
        Place pending = new Place();
        pending.setPlaceId(3L);
        pending.setSubmittedByUserId(9L);
        pending.setVerificationStatus("PENDING");
        when(placeRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(placeRepository.save(pending)).thenReturn(pending);
        PlaceContributionReviewRequest request = new PlaceContributionReviewRequest();
        request.setStatus("APPROVED");
        request.setNote("Verified by local admin.");

        Place reviewed = service.review(3L, new CurrentUser(1L, "ADMIN", "admin"), request);

        assertThat(reviewed.getVerificationStatus()).isEqualTo("APPROVED");
        assertThat(reviewed.getVerifiedByAdminId()).isEqualTo(1L);
        assertThat(reviewed.getVerificationNote()).isEqualTo("Verified by local admin.");
        assertThat(reviewed.getVerifiedTime()).isNotNull();
        verify(userGrowthService).grantExp(9L, "PLACE_CONTRIBUTION_APPROVED", "PLACE", 3L, 20);
    }

    @Test
    void pendingListUsesRepositoryStatusQuery() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceContributionService service = new PlaceContributionService(placeRepository, mock(UserGrowthService.class));
        Place pending = new Place();
        when(placeRepository.findByVerificationStatusOrderByCreateTimeDesc("PENDING")).thenReturn(List.of(pending));

        assertThat(service.pending()).containsExactly(pending);
    }

    @Test
    void rejectRequiresPendingContribution() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceContributionService service = new PlaceContributionService(placeRepository, mock(UserGrowthService.class));
        Place approved = new Place();
        approved.setPlaceId(4L);
        approved.setVerificationStatus("APPROVED");
        when(placeRepository.findById(4L)).thenReturn(Optional.of(approved));
        PlaceContributionReviewRequest request = new PlaceContributionReviewRequest();
        request.setStatus("REJECTED");

        assertThatThrownBy(() -> service.review(4L, new CurrentUser(1L, "ADMIN", "admin"), request))
                .isInstanceOf(BizException.class);
    }
}
