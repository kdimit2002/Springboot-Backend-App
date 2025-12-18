package com.example.webapp.BidNow.Dtos;

import java.math.BigDecimal;

/**
 * Referral Code response dto
 * for a referral code owner to
 * inspect his referral code usage
 *
 * Api:
 *  - ReferralCodeController -> referralCodeUsage
 */
public record ReferralCodeUsageResponse(
        String username,
        String code
) {}
