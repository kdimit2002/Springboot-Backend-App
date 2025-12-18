package com.example.webapp.BidNow.Dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 *
 * Referral code Request Dto
 * for admin to create a new Referral code assigned to a user
 * or update an existing one (AdminReferralCodeController)
 */
public record ReferralCodeRequest(
        @NotBlank(message = "Code must not be null or blank")
        String code,

        @NotNull(message = "CreatorId must not be null")
        Long ownerId,

        @NotNull(message = "RewardPoints must not be null")
        @Min(value = 0, message = "RewardPoints must be greater than or equal to 0")
        Long rewardPoints,

        @NotNull(message = "CreatorRewardPoints must not be null")
        @Min(value = 0, message = "CreatorRewardPoints must be greater than or equal to 0")
        Long ownerRewardPoints,

        @NotNull(message = "MaxUses must not be null")
        @Min(value = 0, message = "MaxUses must be greater than or equal to 0")
        Integer maxUses,

        @NotNull(message = "IsDisabled must not be null")
        Boolean isDisabled

) {}