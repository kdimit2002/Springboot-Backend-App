package com.example.webapp.BidNow.Entities;


import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;

import javax.annotation.Nullable;
import java.math.BigDecimal;

@Entity
@Table(name = "referral_codes")
public class ReferralCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // Ο κωδικός που θα βάζει ο άλλος
    @Column(nullable = false, unique = true)
    private String code;


    @OneToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;



    @PositiveOrZero
    private Long rewardPoints = 0L;


    // Πόσους πόντους δίνει στον δημιουργό όταν κάποιος το χρησιμοποιεί
    @PositiveOrZero
    private Long ownerRewardPoints = 0L;

    // Πόσες φορές μπορεί να χρησιμοποιηθεί αυτός ο κωδικός
    @PositiveOrZero
    private Integer maxUses = 0;

    // Πόσες φορές έχει ήδη χρησιμοποιηθεί
    @PositiveOrZero
    private Integer usesSoFar = 0;

    @Column(nullable = false)
    private Boolean isDisabled = false;



    public ReferralCode() {}

    public ReferralCode(String code, UserEntity owner, Long rewardPoints, Long ownerRewardPoints, Integer maxUses, Integer usesSoFar, Boolean isDisabled) {
        this.code = code;
        this.owner = owner;
        this.rewardPoints = rewardPoints;
        this.ownerRewardPoints = ownerRewardPoints;
        this.maxUses = maxUses;
        this.usesSoFar = usesSoFar;
        this.isDisabled = isDisabled;
    }

    public Boolean getDisabled() {
        return isDisabled;
    }

    public void setDisabled(Boolean disabled) {
        isDisabled = disabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getUsesSoFar() {
        return usesSoFar;
    }

    public void setUsesSoFar(Integer usesSoFar) {
        this.usesSoFar = usesSoFar;
    }

    public Long getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Long rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public Long getOwnerRewardPoints() {
        return ownerRewardPoints;
    }

    public void setOwnerRewardPoints(Long ownerRewardPoints) {
        this.ownerRewardPoints = ownerRewardPoints;
    }

}
