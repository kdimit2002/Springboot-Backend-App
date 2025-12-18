package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.Avatar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SingUp request
 * For taking user roleName, avatar, location,
 * also the request has auth token from firebase(in headers)
 * and storing user into the database
 *
 */
public record SignUpRequest(
        @NotBlank(message = "Role name must not be null or blank")
        String roleName,

        @NotNull(message = "Avatar must not be null")
        Avatar avatar,

        @NotNull(message = "Location must not be null")
        @Valid
        LocationDto locationDto
){}

