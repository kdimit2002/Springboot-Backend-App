package com.example.webapp.BidNow.Entities;

import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.ShippingCostPayer;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name="auctions")
public class Auction {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuctionStatus status;

    @OneToMany(mappedBy = "auction",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("amount DESC")
    @JsonManagedReference
    private List<Bid> bids = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonBackReference
    private UserEntity owner;

    @Column(name = "min_bid_increment", nullable = false)
    private BigDecimal minBidIncrement;


    //ToDo check for lazyload
    @OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Image> auctionImages = new ArrayList<>();

    @Lob
    @Column(name = "description")
    private String description;

    @Column(nullable = false, length = 100)
    private String title;

    //ΜΙΚΡΗ ΠΕΡΙΓΡΑΦΗ (για τη λίστα)
    @Column(name = "short_description", length = 100, nullable = false)
    private String shortDescription;

    @Column(name = "starting_amount", nullable = false)
    @PositiveOrZero(message = "Starting amount must be zero or positive")
    private BigDecimal startingAmount;

    @JsonFormat(pattern = "HH:mm:ss dd/MM/yyyy")
    @Column(nullable = false)
    private LocalDateTime startDate;

    @JsonFormat(pattern = "HH:mm:ss dd/MM/yyyy")
    @Column(nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_cost_payer", nullable = false)
    private ShippingCostPayer shippingCostPayer;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createdBy;

    private String modifiedBy;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private UserEntity winner;

    @Column(name = "ending_soon_notified", nullable = false)
    private boolean endingSoonNotified = false;





    public boolean isEndingSoonNotified() {
        return endingSoonNotified;
    }

    public void setEndingSoonNotified(boolean endingSoonNotified) {
        this.endingSoonNotified = endingSoonNotified;
    }


    public UserEntity getWinner() {
        return winner;
    }

    public void setWinner(UserEntity winner) {
        this.winner = winner;
    }

//    @Version
//    private int version;

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }



    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getStartingAmount() {
        return startingAmount;
    }

    public void setStartingAmount(BigDecimal startingAmount) {
        this.startingAmount = startingAmount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

//    public int getVersion() {
//        return version;
//    }

    public void setMinBidIncrement(BigDecimal minBidIncrement) {
        this.minBidIncrement = minBidIncrement;
    }

    public BigDecimal getMinBidIncrement() {
        return minBidIncrement;
    }

    public List<Image> getAuctionImages() {
        return auctionImages;
    }

    public void setAuctionImages(List<Image> auctionImages) {
        this.auctionImages = auctionImages;
    }

    public void setBids(List<Bid> bids) {
        this.bids = bids;
    }

    public List<Bid> getBids(){
        return bids;
    }

    public String getShortDescription(){
        return shortDescription;
    }

    public void setShortDescription(String shortDescription){
        this.shortDescription=shortDescription;
    }

    public ShippingCostPayer getShippingCostPayer() { return shippingCostPayer; }

    public void setShippingCostPayer(ShippingCostPayer shippingCostPayer) { this.shippingCostPayer = shippingCostPayer; }



    @PrePersist
    public void onCreate(){
        this.status = AuctionStatus.PENDING_APPROVAL; // default όταν το φτιάχνει ο user
        this.createdAt = LocalDateTime.now();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        this.createdBy = (auth != null) ? auth.getName() : "system";
    }

    @PreUpdate
    public void onUpdate(){
        this.updatedAt = LocalDateTime.now();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        this.modifiedBy = (auth != null) ? auth.getName() : "system";
    }

}