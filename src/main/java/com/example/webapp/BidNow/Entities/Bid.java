package com.example.webapp.BidNow.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name="bids")
public class Bid {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(name="amount", unique=false,nullable = false)
    private BigDecimal amount;

    @ManyToOne(optional = false,fetch = FetchType.LAZY)//check lazy load
    @JoinColumn(name="auction_id", nullable=false)
    @JsonBackReference
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)  //  CHECK
    @JoinColumn(name = "bidder_id",unique = false,nullable = false)
    @JsonBackReference
    private UserEntity bidder;

    private boolean isEnabled = true;

    @CreatedDate
    private LocalDateTime createdAt;

    public Bid(BigDecimal amount, Auction auction, UserEntity bidder, boolean isEnabled) {
        this.amount = amount;
        this.auction = auction;
        this.bidder = bidder;
        this.isEnabled = isEnabled;
    }

    public Bid() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Auction getAuction() {
        return auction;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public UserEntity getBidder() {
        return bidder;
    }

    public void setBidder(UserEntity bidder) {
        this.bidder = bidder;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    @PrePersist
    private void init(){
        this.setCreatedAt(LocalDateTime.now());
    }
}
