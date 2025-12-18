package com.example.webapp.BidNow.Projections;

import java.sql.Date;


public interface DailyActiveUsersProjection {
    Date getActivityDate();
    Long getActiveUsers();
}