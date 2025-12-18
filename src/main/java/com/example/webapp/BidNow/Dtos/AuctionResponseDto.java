package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Entities.AuctionMessage;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.ShippingCostPayer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * Auction response dto
 * for sending more detailed information
 * about a single auction
 *
 */
public record AuctionResponseDto(
        Long id,
        String title,
        String categoryName,
        String sellerUsername,
        String sellerLocation,
        String shortDescription,
        String description,
        BigDecimal startingAmount,
        BigDecimal minBidIncrement,
        LocalDateTime startDate,
        LocalDateTime endDate,
        AuctionStatus status,
        ShippingCostPayer shippingCostPayer,
        List<String> imageUrls,
        List<ChatMessageResponse> chat,
        List<BidResponseDto> bids,
        boolean eligibleForBid,
        boolean eligibleForChat
) {}
