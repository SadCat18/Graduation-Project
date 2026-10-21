package com.skatehub.service;

import com.skatehub.dao.SkateEquipmentRepository;
import com.skatehub.dao.UserEquipmentFavoriteRepository;
import com.skatehub.pojo.skate.SkateEquipment;
import com.skatehub.pojo.skate.UserEquipmentFavorite;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private static final int FIRST_FAVORITE_EXP = 3;

    private final SkateEquipmentRepository equipmentRepository;
    private final UserEquipmentFavoriteRepository favoriteRepository;
    private final UserGrowthService userGrowthService;

    public List<Map<String,String>> categories() {
        return equipmentRepository.findPublicCategories().stream().filter(value->value!=null&&!value.isBlank())
                .distinct().sorted().map(value->Map.of("key",value,"label",value)).toList();
    }

    public PageResult<Map<String, Object>> list(Integer page, Integer size, Long userId, String category, String brand, String priceRange, String suitableLevel) {
        int currentPage = InputValidator.page(page);
        int currentSize = InputValidator.size(size);
        var equipmentPage = equipmentRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), "1"));
            if (StringUtils.hasText(category)) predicates.add(builder.equal(root.get("category"), category.trim()));
            if (StringUtils.hasText(brand)) predicates.add(builder.equal(root.get("brand"), brand.trim()));
            if (StringUtils.hasText(priceRange)) predicates.add(builder.equal(root.get("priceRange"), priceRange.trim()));
            if (StringUtils.hasText(suitableLevel)) predicates.add(builder.equal(root.get("suitableLevel"), suitableLevel.trim()));
            return builder.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(currentPage - 1, currentSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(equipmentPage.getTotalElements(), equipmentPage.getContent().stream().map(item -> toEquipmentVO(item, userId)).toList());
    }

    public Map<String, Object> detail(Long equipmentId, Long userId) {
        return toEquipmentVO(activeEquipment(equipmentId), userId);
    }

    @Transactional
    public Map<String, Object> favorite(CurrentUser currentUser, Long equipmentId) {
        Long userId = requireUserId(currentUser);
        activeEquipment(equipmentId);
        if (favoriteRepository.findByUserIdAndEquipmentId(userId, equipmentId).isPresent()) {
            Map<String, Object> duplicate = new HashMap<>();
            duplicate.put("favorited", true);
            duplicate.put("expAwarded", 0);
            return duplicate;
        }
        boolean firstFavorite = favoriteRepository.countByUserId(userId) == 0;
        UserEquipmentFavorite favorite = new UserEquipmentFavorite();
        favorite.setUserId(userId);
        favorite.setEquipmentId(equipmentId);
        favoriteRepository.save(favorite);

        int totalExp = 0;
        int expAwarded = 0;
        if (firstFavorite) {
            totalExp = userGrowthService.grantExp(userId, "FIRST_EQUIPMENT_FAVORITE", "USER", userId, FIRST_FAVORITE_EXP);
            expAwarded = totalExp > 0 ? FIRST_FAVORITE_EXP : 0;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", true);
        result.put("expAwarded", expAwarded);
        result.put("totalExp", totalExp);
        return result;
    }

    @Transactional
    public void unfavorite(CurrentUser currentUser, Long equipmentId) {
        Long userId = requireUserId(currentUser);
        activeEquipment(equipmentId);
        favoriteRepository.deleteByUserIdAndEquipmentId(userId, equipmentId);
    }

    public List<Map<String, Object>> favorites(CurrentUser currentUser) {
        Long userId = requireUserId(currentUser);
        return favoriteRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(favorite -> equipmentRepository.findById(favorite.getEquipmentId()).orElse(null))
                .filter(item -> item != null && "1".equals(item.getStatus()))
                .map(item -> toEquipmentVO(item, userId))
                .toList();
    }

    private Map<String, Object> toEquipmentVO(SkateEquipment equipment, Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("equipment", equipment);
        if (userId != null) {
            map.put("favorited", favoriteRepository.findByUserIdAndEquipmentId(userId, equipment.getEquipmentId()).isPresent());
        }
        return map;
    }

    private SkateEquipment activeEquipment(Long equipmentId) {
        InputValidator.positiveId(equipmentId, "装备ID");
        return equipmentRepository.findByEquipmentIdAndStatus(equipmentId, "1")
                .orElseThrow(() -> new BizException("装备不存在或已下架"));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }
}
