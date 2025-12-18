package com.example.webapp.BidNow.Dtos;

/**
 *
 * This is a return type Dto in various apis in AdminUserEntityController.
 * Its purpose is for admin to have all the necessary info about the app users.
 *
 */
public record AdminUserEntityDto(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String firebaseId,
        Long rewardPoints,
        Long allTimeRewardPoints,
        String avatarUrl,
        String role,
        Boolean isBanned,
        Boolean isAnonymized,
        Boolean eligibleForChat,
        LocationDto locationDto,
        Boolean isReferralCodeOwner,
        String referralCodeName,
        Boolean hasUsedReferralCode,
        String referralCodeUsed

) {}
