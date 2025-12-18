package com.example.webapp.BidNow.Entities;

import com.example.webapp.BidNow.Enums.Endpoint;
import jakarta.persistence.*;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * @Author Kendeas
 */


@Entity
@Table(name = "user_activity")
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(optional = false,fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity userEntity;

    @Enumerated(EnumType.STRING)
    @Column(name = "endpoint", nullable = false)
    private Endpoint endpoint;  // π.χ. LOGIN, UPDATE_PROFILE, PLACE_BID

    @Column(length = 512)
    private String details; // έξτρα πληροφορίες

    @Column(name = "ip_address")
    private String ipAddress;

    //ToDo:not completed see helper method
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    public UserActivity(UserEntity userEntity, Endpoint endpoint, String details, String ipAddress) {
        this.userEntity = userEntity;
        this.endpoint = endpoint;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    public void onCreate(){
        this.createdAt = LocalDateTime.now();
    }


}


//ToDo: scheduled task at night to check if something is suspectfull with ip adresses
// ToDo: all logging must be async commited to DB and ..