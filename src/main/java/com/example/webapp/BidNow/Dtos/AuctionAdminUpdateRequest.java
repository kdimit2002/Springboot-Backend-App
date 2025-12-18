package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.ShippingCostPayer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Auction request dto
 */
public class AuctionAdminUpdateRequest {

    //todo:  @Annotations in order to cut early the request
    private Long categoryId;
    private String title;
    private String shortDescription;
    private String description;
    private BigDecimal startingAmount;
    private BigDecimal minBidIncrement;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ShippingCostPayer shippingCostPayer;
    private AuctionStatus auctionStatus;

    public AuctionAdminUpdateRequest() {
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getStartingAmount() {
        return startingAmount;
    }

    public void setStartingAmount(BigDecimal startingAmount) {
        this.startingAmount = startingAmount;
    }

    public BigDecimal getMinBidIncrement() {
        return minBidIncrement;
    }

    public void setMinBidIncrement(BigDecimal minBidIncrement) {
        this.minBidIncrement = minBidIncrement;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public ShippingCostPayer getShippingCostPayer() {
        return shippingCostPayer;
    }

    public void setShippingCostPayer(ShippingCostPayer shippingCostPayer) {
        this.shippingCostPayer = shippingCostPayer;
    }

    public AuctionStatus getAuctionStatus() {
        return auctionStatus;
    }

    public void setAuctionStatus(AuctionStatus auctionStatus) {
        this.auctionStatus = auctionStatus;
    }
}
