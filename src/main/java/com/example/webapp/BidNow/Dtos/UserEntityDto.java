package com.example.webapp.BidNow.Dtos;

/**
 *
 * User Response Dto
 * when user want to inspect his profile details
 *
 * Api:
 *  - AuthController -> getUserProfile
 */
public record UserEntityDto(
        String username,
        String email,
        String phoneNumber,
        String avatarUrl,
        String avatarName,
        Long rewardPoints,
        String role,

        Boolean eligibleForChat,
        LocationDto locationDto,
        Long allTimeRewardPoints,
        Boolean isReferralCodeOwner,
        Boolean hasUsedReferralCode,
        String referralCodeUsed
){}
