package com.example.webapp.BidNow.Dtos;

import com.example.webapp.BidNow.Enums.NotificationType;

/**
 *
 * Notification response event
 *
 * it uses TransactionalEventListener for writing notification events, after
 * commit of a DB transaction
 *
 */
public record NotificationEvent(
        Long userId,
        NotificationType type,
        String title,
        String body,
        String metadataJson
) {}