package com.skatehub.dao;

import com.skatehub.pojo.DailyCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {

    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate date);

    Optional<DailyCheckin> findTopByUserIdOrderByCheckinDateDesc(Long userId);

    @Query("select coalesce(max(d.streakDays), 0) from DailyCheckin d where d.userId = :userId")
    long findMaximumStreakDaysByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    List<DailyCheckin> findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(
            Long userId, LocalDate start, LocalDate end);
}
