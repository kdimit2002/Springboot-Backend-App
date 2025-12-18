package com.example.webapp.BidNow.Entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * @Author Kendeas
 */
@Entity
@Table(name="images")
public class Image {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;


//    @Column(length=50,unique=true,nullable = false)
//    private String filename;

//
//    // Κρατάμε και το R2 storage key (μοναδικό μέσα στο bucket)
//    @Column(name = "storage_key", length = 255, nullable = false, unique = true)
//    private String storageKey;

    @Column(length=512,unique=true,nullable = false)
    protected String url;

    // Καλύτερα bytes για ακρίβεια (αν θες “σε MB” φτιάχνεις helper στο DTO)
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length=30,nullable = false)
    protected String format;


    @Column(nullable = false)
    protected int width;

    @Column(nullable = false)
    protected int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    @JsonBackReference
    private Auction auction;


//    //ToDo:check for more metadata
//



    public Image() {}

    public Image(String url,
                 long sizeBytes,
                 String format,
                 int width,
                 int height,
                 int sortOrder,
                 Auction auction) {
        this.url = url;
        this.sizeBytes = sizeBytes;
        this.format = format;
        this.width = width;
        this.height = height;
        this.sortOrder = sortOrder;
        this.auction = auction;
        this.createdAt = Instant.now();
    }
    // =====================
    // Getters / Setters
    // =====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Auction getAuction() {
        return auction;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

}

//FINISHED