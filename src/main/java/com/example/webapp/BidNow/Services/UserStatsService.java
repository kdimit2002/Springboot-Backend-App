package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.DailyActiveUsersDto;
import com.example.webapp.BidNow.Projections.DailyActiveUsersProjection;
import com.example.webapp.BidNow.Dtos.MonthlyDailyActiveUsersDto;
import com.example.webapp.BidNow.Repositories.UserActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class UserStatsService {

    private final UserActivityRepository userActivityRepository;

    public UserStatsService(UserActivityRepository userActivityRepository) {
        this.userActivityRepository = userActivityRepository;
    }

    @Transactional(readOnly = true)
    public List<MonthlyDailyActiveUsersDto> getDailyActiveUsersAllMonths() {

        LocalDateTime firstActivity = userActivityRepository.findFirstActivityDate();

        if (firstActivity == null) {
            return List.of(); // δεν υπάρχει καθόλου activity
        }

        // από τον πρώτο μήνα που είχες activity (π.χ. 2024-12-01)
        LocalDate startDate = firstActivity.toLocalDate().withDayOfMonth(1);

        // μέχρι το τέλος του τρέχοντος μήνα (exclusive)
        LocalDate endDateExclusive = LocalDate.now()
                .withDayOfMonth(1)
                .plusMonths(1);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDateExclusive.atStartOfDay();

        List<DailyActiveUsersProjection> rows =
                userActivityRepository.findDailyActiveUsersBetween(start, end);

        // group by YearMonth
        Map<YearMonth, List<DailyActiveUsersDto>> groupedByMonth =
                rows.stream()
                        .collect(Collectors.groupingBy(
                                r -> {
                                    LocalDate day = r.getActivityDate().toLocalDate();
                                    return YearMonth.from(day);
                                },
                                Collectors.mapping(
                                        r -> {
                                            LocalDate day = r.getActivityDate().toLocalDate();
                                            return new DailyActiveUsersDto(
                                                    day.getDayOfMonth(),
                                                    r.getActiveUsers()
                                            );
                                        },
                                        Collectors.toList()
                                )
                        ));

        // φτιάχνουμε sorted λίστα με όλους τους μήνες από start μέχρι τώρα
        List<MonthlyDailyActiveUsersDto> result = new ArrayList<>();

        YearMonth current = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDateExclusive.minusDays(1));

        while (!current.isAfter(last)) {
            List<DailyActiveUsersDto> days =
                    groupedByMonth.getOrDefault(current, List.of());

            days = days.stream()
                    .sorted(Comparator.comparingInt(DailyActiveUsersDto::dayOfMonth))
                    .toList();

            result.add(new MonthlyDailyActiveUsersDto(
                    current.getYear(),
                    current.getMonthValue(),
                    days
            ));

            current = current.plusMonths(1);
        }

        return result;
    }
}
