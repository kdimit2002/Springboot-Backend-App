package com.example.webapp.BidNow.Dtos;

/**
 *
 * Referral code Response Dto
 * for admin to se the referral codes
 * we have in DB
 */
public record ReferralCodeDtoAdminResponse(
        Long id,
        String code,
        Long ownerId,
        Long rewardPoints,
        Long ownerRewardPoints,
        Integer maxUses,
        Integer usesSoFar,
        Boolean isDisabled
) {}