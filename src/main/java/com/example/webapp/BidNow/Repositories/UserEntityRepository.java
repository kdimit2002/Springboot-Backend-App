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

//    @Modifying
//    @Transactional(propagation = Propagation.REQUIRES_NEW)// ToDo: move it to service layer
//    @Query("UPDATE UserEntity u SET u.isFirebaseCompatible = true WHERE u.firebaseId = :firebase_id")
//    void markFirebaseCompatible(@Param("firebase_id") String firebaseId);

//    @Query("select u.firebaseId from UserEntity u where u.isFirebaseCompatible = false and u.createdAt < :cutoff")
//    List<String> findOldUsersWithoutFirebaseClaims(@Param("cutoff") LocalDateTime cutoff);

    boolean existsByFirebaseId(String uid);

    Optional<UserEntity> findByFirebaseId(String uid);


    Page<UserEntity> findById(Long id, Pageable pageable);

    @Modifying
    @Query("UPDATE UserEntity u SET u.username = :username WHERE u.firebaseId = :firebase_id")
    int updateUsername(@Param("username") String username, @Param("firebase_id") String firebaseId);



    @Modifying
    @Query("UPDATE UserEntity u SET u.avatar = :avatar WHERE u.firebaseId = :firebase_id")
    int updateAvatar(@Param("avatar") Avatar avatar, @Param("firebase_id") String firebaseId);



//    //todo:is compatible = false or true???
//    @Modifying
//    @Transactional
////    @Query("""
////        UPDATE UserEntity u
////        SET\s
////            u.username = CONCAT('deleted_user_', u.id),
////            u.email = CONCAT('anonymized_', u.id, '@example.com'),
////            u.isAnonymized = true,
////            u.phoneNumber = CONCAT('anonymized_', u.id),
//            u.firebaseId = CONCAT('deleted_firebase_', u.id),
//            u.avatar = com.example.webapp.BidNow.Enums.Avatar.DEFAULT,
//            u.isBanned = false
//        WHERE u.firebaseId = :userFirebaseId
//   \s""")
//    int  anonymizeByFirebaseId(@Param("userFirebaseId")String userFirebaseId);

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
