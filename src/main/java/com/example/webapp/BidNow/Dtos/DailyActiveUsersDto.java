package com.example.webapp.BidNow.Dtos;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * Dto used for tracking
 * active users per day
 *
 */
public record DailyActiveUsersDto(
        int dayOfMonth,
        Long activeUsers
) {}
