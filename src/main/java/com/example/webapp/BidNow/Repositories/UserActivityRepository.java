package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Projections.DailyActiveUsersProjection;
import com.example.webapp.BidNow.Entities.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {


    // earliest activity date
    @Query("SELECT MIN(ua.createdAt) FROM UserActivity ua")
    LocalDateTime findFirstActivityDate();

    @Query(value = """
        SELECT 
            CAST(ua.created_at AS DATE) AS activityDate,
            COUNT(DISTINCT ua.user_id) AS activeUsers
        FROM user_activity ua
        WHERE ua.created_at >= :start
          AND ua.created_at < :end
        GROUP BY CAST(ua.created_at AS DATE)
        ORDER BY CAST(ua.created_at AS DATE)
        """,
            nativeQuery = true)
    List<DailyActiveUsersProjection> findDailyActiveUsersBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
