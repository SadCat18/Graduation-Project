package com.skatehub.service;

import com.skatehub.dao.DailyCheckinRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.DailyCheckin;
import com.skatehub.pojo.User;
import com.skatehub.pojo.growth.CheckinCalendarResponse;
import com.skatehub.pojo.growth.CheckinResponse;
import com.skatehub.pojo.growth.CheckinStatusResponse;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final int BASE_EXP = 5;
    private static final int STREAK_BONUS_EXP = 20;
    private static final DateTimeFormatter TARGET_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final DailyCheckinRepository dailyCheckinRepository;
    private final UserRepository userRepository;
    private final UserGrowthService userGrowthService;
    private final Clock clock;

    @Transactional
    public CheckinResponse checkin(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BizException("User not found"));
        LocalDate today = LocalDate.now(clock);

        DailyCheckin existing = dailyCheckinRepository.findByUserIdAndCheckinDate(userId, today)
                .orElse(null);
        if (existing != null) {
            return response(existing, true, dailyCheckinRepository.countByUserId(userId), expOf(user));
        }

        int streakDays = dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(userId)
                .filter(previous -> today.minusDays(1).equals(previous.getCheckinDate()))
                .map(previous -> previous.getStreakDays() + 1)
                .orElse(1);
        int bonusExp = streakDays % 7 == 0 ? STREAK_BONUS_EXP : 0;
        int award = BASE_EXP + bonusExp;

        DailyCheckin dailyCheckin = new DailyCheckin();
        dailyCheckin.setUserId(userId);
        dailyCheckin.setCheckinDate(today);
        dailyCheckin.setStreakDays(streakDays);
        dailyCheckin.setBaseExp(BASE_EXP);
        dailyCheckin.setBonusExp(bonusExp);
        dailyCheckin.setCreateTime(LocalDateTime.now(clock));
        dailyCheckinRepository.save(dailyCheckin);

        Long targetId = Long.valueOf(today.format(TARGET_DATE_FORMAT));
        int totalExp = userGrowthService.grantExp(
                userId, "DAILY_CHECKIN", "DATE", targetId, award);

        return response(dailyCheckin, false, dailyCheckinRepository.countByUserId(userId), totalExp);
    }

    @Transactional(readOnly = true)
    public CheckinStatusResponse status(Long userId) {
        User user = findUser(userId);
        LocalDate today = LocalDate.now(clock);
        DailyCheckin todayCheckin = dailyCheckinRepository.findByUserIdAndCheckinDate(userId, today)
                .orElse(null);

        int streakDays;
        if (todayCheckin != null) {
            streakDays = todayCheckin.getStreakDays();
        } else {
            streakDays = dailyCheckinRepository.findTopByUserIdOrderByCheckinDateDesc(userId)
                    .filter(latest -> today.minusDays(1).equals(latest.getCheckinDate()))
                    .map(DailyCheckin::getStreakDays)
                    .orElse(0);
        }

        return new CheckinStatusResponse(
                todayCheckin != null,
                streakDays,
                dailyCheckinRepository.countByUserId(userId),
                expOf(user));
    }

    @Transactional(readOnly = true)
    public CheckinCalendarResponse calendar(Long userId, YearMonth month) {
        if (month == null) {
            throw new BizException("Month is required");
        }
        findUser(userId);
        List<LocalDate> checkedDates = dailyCheckinRepository
                .findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(
                        userId, month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(DailyCheckin::getCheckinDate)
                .toList();
        return new CheckinCalendarResponse(month.toString(), checkedDates);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException("User not found"));
    }

    private int expOf(User user) {
        return user.getExp() == null ? 0 : user.getExp();
    }

    private CheckinResponse response(DailyCheckin checkin, boolean alreadyCheckedIn,
                                     long totalDays, int totalExp) {
        return new CheckinResponse(
                alreadyCheckedIn,
                checkin.getCheckinDate(),
                checkin.getStreakDays(),
                totalDays,
                checkin.getBaseExp(),
                checkin.getBonusExp(),
                totalExp);
    }
}
