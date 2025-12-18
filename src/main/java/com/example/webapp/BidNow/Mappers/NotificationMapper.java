package com.example.webapp.BidNow.Mappers;

import com.example.webapp.BidNow.Dtos.NotificationDto;
import com.example.webapp.BidNow.Entities.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType().name(),   // 👈 εδώ
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getMetadataJson()
        );
    }

}
