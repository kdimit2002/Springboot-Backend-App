package com.example.webapp.BidNow.Dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * NOT USED
 */
public record BidDto(
        @NotNull
        @Positive
        Long id,

        @NotNull
        @Positive
        Long auctionId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount) {
}
