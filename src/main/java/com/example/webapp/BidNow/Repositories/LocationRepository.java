package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    boolean existsByRegion(String region);

    Optional<Location> findByUser(UserEntity user);

    Optional<Location> findByUserFirebaseId(String userFirebaseId);
}
