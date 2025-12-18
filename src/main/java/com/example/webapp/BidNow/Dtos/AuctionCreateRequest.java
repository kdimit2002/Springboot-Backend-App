package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.ShippingCostPayer;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionCreateRequest {

    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    // μικρή περιγραφή για τη λίστα
    @NotBlank
    @Size(max = 100)
    private String shortDescription;

    @NotBlank(message = "Description is required")
    private String description;


    @NotNull(message = "Starting amount is required")
    @PositiveOrZero(message = "Starting amount must be zero or positive")
    private BigDecimal startingAmount;

    @NotNull
    @Positive(message = "Minimum bid increment must be positive")
    private BigDecimal minBidIncrement;

    @NotNull(message = "Start date is required")
    @Future(message = "Start Date must be in future")
    private LocalDateTime startDate;


    @NotNull(message = "End date is required")
    @Future(message = "End Date must be in future")
    private LocalDateTime endDate;

    @NotNull(message = "Shipping cost payer is required")
    private ShippingCostPayer shippingCostPayer;


    public AuctionCreateRequest() {
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

    public BigDecimal getMinBidIncrement() {
        return minBidIncrement;
    }

    public void setMinBidIncrement(BigDecimal minBidIncrement) {
        this.minBidIncrement = minBidIncrement;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getShortDescription(){
        return shortDescription;
    }

    public ShippingCostPayer getShippingCostPayer() {
        return shippingCostPayer;
    }

    public void setShippingCostPayer(ShippingCostPayer shippingCostPayer) { this.shippingCostPayer = shippingCostPayer; }

}