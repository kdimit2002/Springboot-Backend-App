package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Location response and request dto
 *
 * for handling location requests
 *
 *
 */
public record LocationDto(
        @NotBlank(message = "Country must not be null or blank")
        String country,

        @NotNull(message = "Region must not be null")
        Region region,

        String city,
        String addressLine,
        String postalCode
) { }
