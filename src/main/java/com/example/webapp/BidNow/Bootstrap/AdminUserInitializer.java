package com.example.webapp.BidNow.Bootstrap;

import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Region;
import com.example.webapp.BidNow.Repositories.RoleRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final FirebaseAuth firebaseAuth;
    private final UserEntityRepository userEntityRepository;
    private final RoleRepository roleRepository;

    public AdminUserInitializer(FirebaseAuth firebaseAuth,
                                UserEntityRepository userEntityRepository,
                                RoleRepository roleRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userEntityRepository = userEntityRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Admin is created when app starts in
     * order to avoid security risks
     * @param args
     */
    @Override
    public void run(String... args) {
        try {
            createOrUpdateAdminUserTx();
        } catch (Exception e) {
            // We don't stop application if commandline runner fails
            log.error("Failed to initialize admin user", e);
        }
    }

    /**
     * Create admin user on startup. Both on our database and firebase auth database
     * Otherwise, if user already exists, just update his info
     * @throws FirebaseAuthException
     */
    @Transactional
    public void createOrUpdateAdminUserTx() throws FirebaseAuthException {

        UserRecord userRecord;
        //Todo: these should be assigned with env variables in prod, or make admin manually
        final String adminEmail = "bidnow@gmail.com";
        final String adminPassword = "1234567890";
        final String adminUsername = "BidNow";
        final String adminLocalPhone = "99999999";       // DB
        final String adminE164Phone = "+35799999999";    // Firebase
        try {
            userRecord = firebaseAuth.getUserByEmail(adminEmail);
            log.info("Admin Firebase user already exists: uid={}", userRecord.getUid());

            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(userRecord.getUid())
                    .setDisplayName(adminUsername)
                    .setPhoneNumber(adminE164Phone)
                    .setEmailVerified(true)
                    .setDisabled(false);

            firebaseAuth.updateUser(updateRequest);
            userRecord = firebaseAuth.getUser(userRecord.getUid()); // reload

        } catch (FirebaseAuthException e) {
            // Create user if it doesn't exist
            log.info("Admin Firebase user does not exist (or lookup failed). Creating new one...");

            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(adminEmail)
                    .setPassword(adminPassword)
                    .setDisplayName(adminUsername)
                    .setPhoneNumber(adminE164Phone)
                    .setEmailVerified(true)
                    .setDisabled(false);

            userRecord = firebaseAuth.createUser(createRequest);
            log.info("Created Firebase admin user: uid={}", userRecord.getUid());
        }

        String firebaseId = userRecord.getUid();

        // Set all claims/Roles in Firebase
        Map<String, Object> claims = Map.of("roles", List.of("Admin", "Auctioneer", "Bidder"));
        firebaseAuth.setCustomUserClaims(firebaseId, claims);
        log.info("Set role claims for Firebase user {}", firebaseId);

        // Set all claims/Roles in  DB
        Role adminRole = getOrCreateRole("Admin");
        Role auctioneerRole = getOrCreateRole("Auctioneer");
        Role bidderRole = getOrCreateRole("Bidder");

        // Find user on DB, check if user doesn't exist create user otherwise update his info
        Optional<UserEntity> existingOpt = userEntityRepository.findByFirebaseId(firebaseId);

        UserEntity adminUser;
        if (existingOpt.isPresent()) {
            adminUser = existingOpt.get();

            adminUser.setUsername(adminUsername);
            adminUser.setEmail(adminEmail);
            adminUser.setPhoneNumber(adminLocalPhone);
            adminUser.setAvatar(Avatar.BEARD_MAN_AVATAR);
            adminUser.setFirebaseId(firebaseId);
            adminUser.setBanned(false);
            adminUser.setAnonymized(false);

            Set<Role> roles = new HashSet<>(
                    Optional.ofNullable(adminUser.getRoles()).orElse(Collections.emptySet())
            );
            roles.add(adminRole);
            roles.add(auctioneerRole);
            roles.add(bidderRole);
            adminUser.setRoles(roles);

            log.info("Updated existing admin UserEntity with firebaseId={}", firebaseId);

        } else {
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            roles.add(auctioneerRole);
            roles.add(bidderRole);

            adminUser = new UserEntity(
                    adminUsername,
                    adminEmail,
                    adminLocalPhone,
                    firebaseId,
                    Avatar.BEARD_MAN_AVATAR,
                    roles
            );

            // Dummy info
            Location location = new Location(
                    adminUser,
                    "Cyprus",
                    Region.FAMAGUSTA,
                    "Sotira",
                    "Admin Address",
                    "0000",
                    null,
                    null
            );
            adminUser.setLocation(location);

            log.info("Creating new admin UserEntity with firebaseId={}", firebaseId);
        }

        userEntityRepository.save(adminUser);

        log.info("Admin UserEntity persisted successfully (id={}, firebaseId={})",
                adminUser.getId(), adminUser.getFirebaseId());
    }

    private Role getOrCreateRole(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(new Role(name)));
    }
}