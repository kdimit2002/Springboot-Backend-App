package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.AdminBroadcastNotificationRequest;
import com.example.webapp.BidNow.Entities.Announcement;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Repositories.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AdminAnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @Transactional
    public Long broadcastGeneral(AdminBroadcastNotificationRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (req.body() == null || req.body().isBlank()) {
            throw new IllegalArgumentException("body is required");
        }

        Announcement a = new Announcement();
        a.setType(NotificationType.GENERAL);
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        a.setMetadataJson(req.metadataJson());

        return announcementRepository.save(a).getId();
    }
}
