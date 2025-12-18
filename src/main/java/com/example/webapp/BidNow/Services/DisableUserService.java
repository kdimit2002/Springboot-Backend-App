package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisableUserService {

    private final AdminUserEntityService adminUserEntityService;
    private final UserEntityRepository userEntityRepository;

    public DisableUserService(AdminUserEntityService adminUserEntityService, UserEntityRepository userEntityRepository) {
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
