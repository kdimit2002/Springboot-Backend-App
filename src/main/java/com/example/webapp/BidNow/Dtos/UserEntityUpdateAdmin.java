package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.Avatar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * User Request Dto
 * when admin want to change users information
 *
 * Api:
 *  - AuthController -> editAdmin
 */
public record UserEntityUpdateAdmin(

        @NotBlank(message = "Username must not be null or blank")
        String username,

        @NotBlank(message = "Email must not be null or blank")
        String email,

        @NotNull(message = "RewardPoints must not be null")
        @Min(value = 0, message = "RewardPoints must be greater than or equal to 0")
        Long rewardPoints,

        @NotNull(message = "Avatar must not be null")
        Avatar avatar,

        @NotBlank(message = "Role must not be null or blank")
        String role,

        @NotNull(message = "IsBanned must not be null")
        Boolean isBanned,

        @NotNull(message = "IsAnonymized must not be null")
        Boolean isAnonymized,

        @NotNull(message = "EligibleForChat must not be null")
        Boolean eligibleForChat,

        @NotNull(message = "Location must not be null")
        @Valid
        LocationDto locationDto
) {}
