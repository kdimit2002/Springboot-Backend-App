package com.example.webapp.BidNow.Dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * Announcement request dto
 * in broadcast api in AdminNotificationController
 * for sending a joint announcement to all users
 *
 */
public record AdminBroadcastNotificationRequest(
        @NotBlank(message = "Title announcement to users cannot be blank")
        String title,
        @NotBlank(message = "Body announcement to users cannot be blank")
        String body,
        String metadataJson
) {}
