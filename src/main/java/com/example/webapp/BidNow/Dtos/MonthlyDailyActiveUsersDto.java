package com.example.webapp.BidNow.Dtos;

import java.util.List;

/**
 *
 * This dto is being used for tracking of active
 * users for a month of a year
 */
public record MonthlyDailyActiveUsersDto(
        int year,
        int month,
        List<DailyActiveUsersDto> days
) {}
