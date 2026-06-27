package com.skatehub.service;

import com.skatehub.dao.SkateEquipmentRepository;
import com.skatehub.dao.UserEquipmentFavoriteRepository;
import com.skatehub.pojo.skate.SkateEquipment;
import com.skatehub.pojo.skate.UserEquipmentFavorite;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private SkateEquipmentRepository equipmentRepository;
    @Mock private UserEquipmentFavoriteRepository favoriteRepository;
    @Mock private UserGrowthService userGrowthService;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void favoriteIsIdempotentAndFirstFavoriteGrantsExperience() {
        SkateEquipment equipment = new SkateEquipment();
        equipment.setEquipmentId(4L);
        equipment.setEquipmentName("8.0 Maple Deck");
        equipment.setStatus("1");
        when(equipmentRepository.findByEquipmentIdAndStatus(4L, "1")).thenReturn(Optional.of(equipment));
        when(favoriteRepository.findByUserIdAndEquipmentId(USER_ID, 4L)).thenReturn(Optional.empty());
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(favoriteRepository.save(any(UserEquipmentFavorite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userGrowthService.grantExp(USER_ID, "FIRST_EQUIPMENT_FAVORITE", "USER", USER_ID, 3)).thenReturn(103);

        var result = equipmentService.favorite(currentUser(), 4L);

        verify(favoriteRepository).save(any(UserEquipmentFavorite.class));
        assertThat(result.get("favorited")).isEqualTo(true);
        assertThat(result.get("expAwarded")).isEqualTo(3);
        assertThat(result.get("totalExp")).isEqualTo(103);
    }

    @Test
    void favoriteDoesNotWriteDuplicateRecord() {
        SkateEquipment equipment = new SkateEquipment();
        equipment.setEquipmentId(4L);
        equipment.setStatus("1");
        when(equipmentRepository.findByEquipmentIdAndStatus(4L, "1")).thenReturn(Optional.of(equipment));
        when(favoriteRepository.findByUserIdAndEquipmentId(USER_ID, 4L))
                .thenReturn(Optional.of(new UserEquipmentFavorite()));

        var result = equipmentService.favorite(currentUser(), 4L);

        verify(favoriteRepository, never()).save(any());
        assertThat(result.get("favorited")).isEqualTo(true);
        assertThat(result.get("expAwarded")).isEqualTo(0);
    }

    private CurrentUser currentUser() {
        return new CurrentUser(USER_ID, "USER", "skater");
    }
}
