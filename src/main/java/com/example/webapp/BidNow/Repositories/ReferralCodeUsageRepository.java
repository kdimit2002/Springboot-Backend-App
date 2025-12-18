package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.ReferralCodeUsage;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralCodeUsageRepository extends JpaRepository<ReferralCodeUsage,Long> {

    boolean existsByUser_FirebaseId(String firebaseId);


    List<ReferralCodeUsage> findByReferralCodeId(Long id);

    List<ReferralCodeUsage> findByReferralCode_Owner_FirebaseId(String userFirebaseId);



    Page<ReferralCodeUsage> findByReferralCode_Owner_FirebaseId(
            String firebaseId,
            Pageable pageable
    );
    Optional<ReferralCodeUsage> findByUser_FirebaseId(String firebaseId);

}
