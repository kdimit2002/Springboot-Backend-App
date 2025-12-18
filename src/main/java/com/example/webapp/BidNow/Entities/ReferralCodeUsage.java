package com.example.webapp.BidNow.Entities;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_code_usage")
public class ReferralCodeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ο χρήστης που ΧΡΗΣΙΜΟΠΟΙΗΣΕ τον κωδικό
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // ο κωδικός που χρησιμοποιήθηκε
    @ManyToOne
    @JoinColumn(name = "referral_code_id", nullable = false)
    private ReferralCode referralCode;

    private LocalDateTime usedAt;


    public ReferralCodeUsage() {}


    public ReferralCodeUsage(UserEntity user, ReferralCode referralCode) {
        this.user = user;
        this.referralCode = referralCode;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public ReferralCode getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(ReferralCode referralCode) {
        this.referralCode = referralCode;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    @PrePersist
    public void inert(){
        usedAt = LocalDateTime.now();
    }
}
