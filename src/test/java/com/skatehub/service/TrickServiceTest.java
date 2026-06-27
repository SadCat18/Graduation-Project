package com.skatehub.service;

import com.skatehub.dao.SkateTrickRepository;
import com.skatehub.dao.TrickCheckinRepository;
import com.skatehub.pojo.skate.SkateTrick;
import com.skatehub.pojo.skate.TrickCheckin;
import com.skatehub.pojo.skate.TrickCheckinRequest;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrickServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private SkateTrickRepository trickRepository;
    @Mock private TrickCheckinRepository checkinRepository;
    @Mock private UserGrowthService userGrowthService;

    @InjectMocks
    private TrickService trickService;

    @Test
    void submitCheckinSavesPracticeRecordAndGrantsExperienceOnCreatedCheckin() {
        SkateTrick trick = new SkateTrick();
        trick.setTrickId(3L);
        trick.setTrickName("Ollie");
        trick.setStatus("1");
        when(trickRepository.findByTrickIdAndStatus(3L, "1")).thenReturn(Optional.of(trick));
        when(checkinRepository.save(org.mockito.ArgumentMatchers.any(TrickCheckin.class))).thenAnswer(invocation -> {
            TrickCheckin saved = invocation.getArgument(0);
            saved.setCheckinId(44L);
            return saved;
        });
        when(userGrowthService.grantExp(USER_ID, "TRICK_CHECKIN", "TRICK_CHECKIN", 44L, 5)).thenReturn(105);

        TrickCheckinRequest request = new TrickCheckinRequest();
        request.setPracticeMinutes(25);
        request.setMasterStatus("BASIC");
        request.setContent("落地稳定了");
        request.setMediaUrls("/upload/ollie.mp4");
        request.setIsPublic("1");

        var result = trickService.submitCheckin(currentUser(), 3L, request);

        ArgumentCaptor<TrickCheckin> captor = ArgumentCaptor.forClass(TrickCheckin.class);
        verify(checkinRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTrickId()).isEqualTo(3L);
        assertThat(captor.getValue().getPracticeMinutes()).isEqualTo(25);
        assertThat(captor.getValue().getMasterStatus()).isEqualTo("BASIC");
        assertThat(captor.getValue().getIsPublic()).isEqualTo("1");
        assertThat(result.get("expAwarded")).isEqualTo(5);
        assertThat(result.get("totalExp")).isEqualTo(105);
    }

    private CurrentUser currentUser() {
        return new CurrentUser(USER_ID, "USER", "skater");
    }
}
