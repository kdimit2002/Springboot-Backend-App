package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.NotificationDto;
import com.example.webapp.BidNow.Entities.Announcement;
import com.example.webapp.BidNow.Entities.Notification;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Mappers.NotificationMapper;
import com.example.webapp.BidNow.Repositories.AnnouncementRepository;
import com.example.webapp.BidNow.Repositories.NotificationRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationRepository notificationRepository;
    private final UserEntityRepository userEntityRepository;
    private final AnnouncementRepository announcementRepository;

    public NotificationService(NotificationMapper notificationMapper,
                               NotificationRepository notificationRepository,
                               UserEntityRepository userEntityRepository,
                               AnnouncementRepository announcementRepository) {
        this.notificationMapper = notificationMapper;
        this.notificationRepository = notificationRepository;
        this.userEntityRepository = userEntityRepository;
        this.announcementRepository = announcementRepository;
    }

    @Transactional
    public Page<NotificationDto> getMyNotifications(int page, int size) {

        if (size > 100) size = 100;
        if (size <= 0) size = 20;
        if (page < 0) page = 0;

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // σωστό merge pagination: τραβάμε top (page+1)*size από κάθε πηγή
        int limit = (page + 1) * size;
        Pageable topN = PageRequest.of(0, limit, sort);

        List<NotificationDto> personal = notificationRepository
                .findByUser_FirebaseId(user.getFirebaseId(), topN)
                .map(notificationMapper::toDto) // ⚠️ με βάση το mapper που έχεις
                .getContent();

        if(!personal.isEmpty())
            notificationRepository.markAllReadByFirebaseId(getUserFirebaseId(), LocalDateTime.now());


        List<NotificationDto> announcements = announcementRepository
                .findAll(topN)
                .map(this::announcementToDto)
                .getContent();

        List<NotificationDto> merged = mergeSorted(personal, announcements);

        int from = page * size;
        int to = Math.min(from + size, merged.size());
        List<NotificationDto> content = from >= merged.size() ? List.of() : merged.subList(from, to);

        long total = notificationRepository.countByUser_FirebaseId(user.getFirebaseId())
                + announcementRepository.count();

        return new PageImpl<>(content, PageRequest.of(page, size, sort), total);
    }

    private NotificationDto announcementToDto(Announcement a) {
        return new NotificationDto(
                -a.getId(),                 // 👈 αρνητικό id για announcements
                a.getType().name(),         // "GENERAL"
                a.getTitle(),
                a.getBody(),
                true,                       // 👈 δεν σε νοιάζει read, άστο true
                a.getCreatedAt(),
                a.getMetadataJson()
        );
    }

    private List<NotificationDto> mergeSorted(List<NotificationDto> a, List<NotificationDto> b) {
        int i = 0, j = 0;
        List<NotificationDto> out = new ArrayList<>(a.size() + b.size());

        while (i < a.size() || j < b.size()) {
            if (i == a.size()) { out.add(b.get(j++)); continue; }
            if (j == b.size()) { out.add(a.get(i++)); continue; }

            NotificationDto x = a.get(i);
            NotificationDto y = b.get(j);

            if (x.createdAt().isAfter(y.createdAt())) { out.add(x); i++; }
            else if (y.createdAt().isAfter(x.createdAt())) { out.add(y); j++; }
            else { // same createdAt -> id desc
                if (x.id() >= y.id()) { out.add(x); i++; }
                else { out.add(y); j++; }
            }
        }
        return out;
    }
}
