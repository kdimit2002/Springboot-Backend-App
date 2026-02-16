package com.example.webapp.BidNow.Entities;

import com.example.webapp.BidNow.Enums.Avatar;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;


@Entity
@Table(name="users")
public class UserEntity {



    //todo: REMOVE ROLE AUCTIONEER BIDDER OR DONT ALLOW AN AUCTIONEER TO GET BACK TO BIDDER

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name="name", length=40,unique=true)
    private String username;


    @Email
    @NotBlank
    @Column(name="email", length=60,unique=true)
    private String email;

    @NotBlank
    @Column(name = "phone_number",length=50,unique=true,nullable = false)
    private String phoneNumber;

    @NotBlank
    @Column(name="firebase_id",length=50,unique=true,nullable = false)//ToDo: final???
    private String firebaseId;


    @PositiveOrZero
    @Column(name = "reward_points")
    private Long rewardPoints = 0L;


    @PositiveOrZero
    @Column(name = "all_time_reward_points")
    private Long allTimeRewardPoints = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Avatar avatar;


    // We don't usually want to have auction list. Only when user wants to see it.
    @OneToMany(mappedBy = "owner",fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Auction> auctionList = new ArrayList<>();


    @NotEmpty
    @ManyToMany(fetch = FetchType.EAGER)
    @JsonManagedReference
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(
                    name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(
                    name = "role_id", referencedColumnName = "id"))
    private Set<Role> roles = new HashSet<>();

    public UserEntity(String username, String email, String phoneNumber,String firebaseId,Avatar avatarName,Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.firebaseId = firebaseId;
        this.avatar = avatarName;
        this.isBanned = false; // false by default
        this.roles = new HashSet<>(roles);
    }
    public UserEntity(){}


    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Location location;


    public int getVersion() {
        return version;
    }

    @Column(name="is_banned")
    private Boolean isBanned;

    @Column(name="is_anonymized")
    private Boolean isAnonymized;

    //ToDo:not completed see helper method
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String modifiedBy;

    @Column(name = "profile_last_updated_at")
    private LocalDateTime profileLastUpdatedAt;


    @Version
    private int version;

    @Column(nullable = false,name = "eligible_for_chat")
    private boolean eligibleForChat;

    public boolean isEligibleForChat() {
        return eligibleForChat;
    }

    public void setEligibleForChat(boolean eligibleForChat) {
        this.eligibleForChat = eligibleForChat;
    }

    public String getFirebaseId() {
        return firebaseId;
    }

    public void setFirebaseId(String firebaseId) {
        this.firebaseId = firebaseId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public List<Auction> getAuctionList() {
        return auctionList;
    }

    public void setAuctionList(List<Auction> auctionList) {
        this.auctionList = auctionList;
    }

    public Avatar getAvatar() {
        return this.avatar;
    }


    public void setAvatar(Avatar avatarName) {
        this.avatar = avatarName;
    }

    public Long getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Long rewardPoints) {
        this.rewardPoints = rewardPoints;
    }//ToDo: Probably remove set


    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }


    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Long getAllTimeRewardPoints() {
        return allTimeRewardPoints;
    }

    public void setAllTimeRewardPoints(Long allTimeRewardPoints) {
        this.allTimeRewardPoints = allTimeRewardPoints;
    }

    public LocalDateTime getProfileLastUpdatedAt() {
        return profileLastUpdatedAt;
    }

    public void setProfileLastUpdatedAt(LocalDateTime profileLastUpdatedAt) {
        this.profileLastUpdatedAt = profileLastUpdatedAt;
    }


    public Boolean getBanned() {
        return isBanned;
    }

    public void setBanned(Boolean banned) {
        isBanned = banned;
    }

    public Boolean getAnonymized() {
        return isAnonymized;
    }

    public void setAnonymized(Boolean anonymized) {
        isAnonymized = anonymized;
    }

    /**
     * On creation, initialize user's creation date.
     */
    @PrePersist
    public void onCreate(){
        this.isAnonymized = false; // Users are not anonymized upon creation.
        this.createdAt = LocalDateTime.now();
    }

    /**
     * On update, update user's update date and modified by
     */
    @PreUpdate
    public void onUpdate(){
        this.updatedAt = LocalDateTime.now();
        var ctx = SecurityContextHolder.getContext();
        var auth = (ctx != null) ? ctx.getAuthentication() : null;
        this.modifiedBy = (auth != null) ? auth.getName() : "system";

    }


}




//ToDo: na exo ipopsi mou ta indexes!!!!!!!!!!!!!!!!!!!//