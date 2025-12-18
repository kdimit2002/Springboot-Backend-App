package com.example.webapp.BidNow.Dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response bid dto
 * used in auction response dto for sending
 * the list of bids in the specific auction
 *
 */
public record BidResponseDto(
        Long id,
        BigDecimal amount,
        String bidderUsername,
        LocalDateTime createdAt,
        Long auctionId
) {}
