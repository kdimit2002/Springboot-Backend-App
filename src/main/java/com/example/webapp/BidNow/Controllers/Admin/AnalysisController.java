package com.example.webapp.BidNow.Controllers.Admin;


import com.example.webapp.BidNow.Dtos.MonthlyDailyActiveUsersDto;
import com.example.webapp.BidNow.Services.UserStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin controller for analytics/statistics endpoints.
 *
 * Base path: /api/admin/analytics
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalysisController {



    private final UserStatsService userStatsService;

    public AnalysisController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
    }

    /**
     * Get daily active users for all months.
     *
     * GET /api/admin/analytics/active-users/all-months
     *
     * @return list of MonthlyDailyActiveUsersDto (daily active users grouped by month)
     */
    @GetMapping("/active-users/all-months")
    public ResponseEntity<List<MonthlyDailyActiveUsersDto>> getDailyActiveUsersAllMonths() {
        return ResponseEntity.ok(userStatsService.getDailyActiveUsersAllMonths());
    }

}
