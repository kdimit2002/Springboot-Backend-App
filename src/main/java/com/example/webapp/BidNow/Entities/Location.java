package com.example.webapp.BidNow.Entities;

import com.example.webapp.BidNow.Enums.Region;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Entity
@Table(name = "user_locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ο χρήστης στον οποίο ανήκει αυτή η τοποθεσία
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 100)
    @Size(max = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private Region region;


    @Column(length = 100)
    @Size(max = 100)
    private String city;

    @Column(length = 255)
    @Size(max = 255)
    private String addressLine;

    @Column(length = 20)
    @Size(max = 20)
    private String postalCode;

    // Προαιρετικά – αν δεν σε νοιάζουν, μπορείς να τα αφαιρέσεις
    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    public Location() {
    }

    public Location(UserEntity user,
                    String country,
                    Region region,
                    String city,
                    String addressLine,
                    String postalCode,
                    BigDecimal latitude,
                    BigDecimal longitude) {
        this.user = user;
        this.country = country;
        this.region = region;
        this.city = city;
        this.addressLine = addressLine;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters / Setters

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }
}
