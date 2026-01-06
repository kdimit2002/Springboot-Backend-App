package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * DisableUserService
 *
 * Notes:
 *  - Called by nightly consistency scheduler
 *  - Handles "anonymization" of a user inside the database.
 *   - This is typically used when an admin deletes an account or user wants to delete his account
 *     while keeping historical data (auctions, bids, messages) but removing personally identifiable info.
 *
 */
@Service
public class AnonymizeUserService {

    private final AdminUserEntityService adminUserEntityService;
    private final UserEntityRepository userEntityRepository;

    public AnonymizeUserService(AdminUserEntityService adminUserEntityService, UserEntityRepository userEntityRepository) {
        this.adminUserEntityService = adminUserEntityService;
        this.userEntityRepository = userEntityRepository;
    }


    @Transactional
    public void handleDbUserAnonymize(UserEntity user) {
        adminUserEntityService.disableUserActions(user);  // μέσα στο ίδιο TX

        user.setUsername("deleted_user_" + user.getId());
        user.setEmail("anonymized_" + user.getId() + "@example.com");
        user.setAnonymized(true);
        user.setFirebaseId("deleted_firebase_" + user.getId());
        user.setPhoneNumber("XXXXXX");
        user.setAvatar(Avatar.DEFAULT);

        userEntityRepository.save(user);
    }


}
