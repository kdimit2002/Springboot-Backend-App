package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.NotificationType;

import java.time.LocalDateTime;

/**
 *
 * Notification response dto
 * for sending the notifications of the user
 *
 * Api:
 *  - getMyNotifications from notificationsController
 */
public record NotificationDto(
   Long id,
   String notificationType,
   String title,
   String body,
   boolean read,
   LocalDateTime createdAt,
   String metadataJson
) {}
