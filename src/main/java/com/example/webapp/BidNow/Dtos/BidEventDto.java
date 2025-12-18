package com.example.webapp.BidNow.Dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * Response bid dto
 * for broadcasting bids across active users.
 * Api: placeBid api in controller BidController
 *
 */
public record BidEventDto(
        Long id,
        BigDecimal amount,
        String bidderUsername,
        LocalDateTime createdAt,
        Long auctionId,
        LocalDateTime newEndDate
) {}
