package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * Auction response dto
 * for returning the info of the main
 * screen with the list of auctions
 *
 */
public record AuctionListItemDto(
        Long id,
        String title,
        String categoryName,
        String sellerUsername,
        String sellerLocation,
        String shortDescription,
        BigDecimal startingAmount,
        BigDecimal minBidIncrement,
        BigDecimal topBidAmount,
        String topBidderUsername,
        String mainImageUrl,
        LocalDateTime endDate,
        AuctionStatus status,
        boolean eligibleForBid
) {}
