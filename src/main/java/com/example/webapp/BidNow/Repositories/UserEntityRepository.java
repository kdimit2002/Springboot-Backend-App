package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * @Author Kendeas
 */
@Repository
public interface UserEntityRepository extends JpaRepository<UserEntity,Long> {

    int deleteByFirebaseId(String firebaseId);

    boolean existsByFirebaseId(String uid);

    Optional<UserEntity> findByFirebaseId(String uid);


    Page<UserEntity> findById(Long id, Pageable pageable);

    @Modifying
    @Query("UPDATE UserEntity u SET u.username = :username WHERE u.firebaseId = :firebase_id")
    int updateUsername(@Param("username") String username, @Param("firebase_id") String firebaseId);



    @Modifying
    @Query("UPDATE UserEntity u SET u.avatar = :avatar WHERE u.firebaseId = :firebase_id")
    int updateAvatar(@Param("avatar") Avatar avatar, @Param("firebase_id") String firebaseId);


    @Modifying
    @Query("UPDATE UserEntity u SET u.isBanned = true WHERE u.firebaseId = :firebase_id")
    void banUser(String firebase_id);



    Page<UserEntity> findAll(Pageable pageable);


    @Modifying
    @Query("""
            UPDATE UserEntity u\s
            SET u.rewardPoints = u.rewardPoints + :points,
             u.allTimeRewardPoints = u.allTimeRewardPoints + :points
            WHERE u.id = :userId
           \s""")
    void incrementPoints(@Param("userId") Long userId,
                         @Param("points") Long points);




    @Query("SELECT u.isBanned FROM UserEntity u WHERE u.firebaseId = :firebaseId")
    Boolean isUserBanned(@Param("firebaseId") String firebaseId);

    Optional<UserEntity> findByEmail(String mail);

    boolean existsByUsername(String displayName);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Page<UserEntity> findByFirebaseIdContainingIgnoreCase(String search, Pageable pageable);

    Page<UserEntity> findByUsernameContainingIgnoreCase(String search, Pageable pageable);

    Optional<UserEntity> findByUsername(String username);
}
