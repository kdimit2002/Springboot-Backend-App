package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Dtos.PageResponse;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.Notification;
import com.example.webapp.BidNow.Enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_FirebaseId(String firebaseId, Pageable pageable);

    boolean existsByUser_IdAndTypeAndMetadataJson(Long userId, NotificationType type, String metadataJson);

//    void save(Notification n);

    long countByUser_FirebaseId(String firebaseId);

    @Modifying
    @Query("""
update Notification n
set n.read = true, n.readAt = :now
where n.user.firebaseId = :firebaseId
  and n.read = false
""")
    void markAllReadByFirebaseId(String firebaseId, LocalDateTime now);

}
