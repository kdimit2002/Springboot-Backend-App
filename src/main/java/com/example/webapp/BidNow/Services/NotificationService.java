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

/**
 * NotificationService
 *
 * Returns the user's "inbox" by combining:
 * - Personal notifications (stored per user)
 * - Global announcements (visible to everyone)
 *
 * The two lists are merged in-memory and returned as a single paginated feed,
 * sorted by (createdAt desc, id desc).
 */
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

        // Basic pagination guards.
        if (size > 100) size = 100;
        if (size <= 0) size = 20;
        if (page < 0) page = 0;

        // Feed ordering.
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        // Resolve current user.
        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Pull enough items from each source so we can merge and then cut the requested page.
        int limit = (page + 1) * size;
        Pageable topN = PageRequest.of(0, limit, sort);

        // Personal notifications (mapped to DTOs).
        List<NotificationDto> personal = notificationRepository
                .findByUser_FirebaseId(user.getFirebaseId(), topN)
                .map(notificationMapper::toDto)
                .getContent();

        // Mark personal notifications as read when the user fetches them.
        if (!personal.isEmpty()) {
            notificationRepository.markAllReadByFirebaseId(getUserFirebaseId(), LocalDateTime.now());
        }

        // Global announcements (also mapped to NotificationDto shape).
        List<NotificationDto> announcements = announcementRepository
                .findAll(topN)
                .map(this::announcementToDto)
                .getContent();

        // Merge both lists into one sorted feed.
        List<NotificationDto> merged = mergeSorted(personal, announcements);

        // Manual pagination on the merged list.
        int from = page * size;
        int to = Math.min(from + size, merged.size());
        List<NotificationDto> content = from >= merged.size() ? List.of() : merged.subList(from, to);

        // Total count = personal count + announcements count.
        long total = notificationRepository.countByUser_FirebaseId(user.getFirebaseId())
                + announcementRepository.count();

        return new PageImpl<>(content, PageRequest.of(page, size, sort), total);
    }

    private NotificationDto announcementToDto(Announcement a) {
        // Use negative ids so announcements never collide with real notification ids.
        return new NotificationDto(
                -a.getId(),//todo: maybe should remove id
                a.getType().name(),
                a.getTitle(),
                a.getBody(),
                true, // announcements don't use read/unread logic
                a.getCreatedAt(),
                a.getMetadataJson()
        );
    }


    /**
     * Merges two already-sorted notification lists into a single sorted list.
     * Both inputs must be sorted DESC by (createdAt, id).
     * This is the merge step of merge-sort and runs in O(n + m) time.
     *
     * @param a
     * @param b
     * @return
     */
    private List<NotificationDto> mergeSorted(List<NotificationDto> a, List<NotificationDto> b) {
        // Classic merge (like merge-sort): both lists are already sorted desc by createdAt/id.
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