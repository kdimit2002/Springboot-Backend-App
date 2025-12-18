package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}
