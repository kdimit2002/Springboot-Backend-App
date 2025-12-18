package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.UserEntity;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {

    Optional<ReferralCode> findByCode(String code);

    boolean existsByOwner(UserEntity owner);

    Optional<ReferralCode> findByOwnerFirebaseId(String userFirebaseId);

    boolean existsByOwner_FirebaseId(String userFirebaseId);

    Optional<ReferralCode> findByOwner_FirebaseId(String firebaseId);

    boolean existsByCode(@NotBlank(message = "Code must not be null or blank") String code);
}
