package com.example.webapp.BidNow.Services;


import com.example.webapp.BidNow.Dtos.AuthUserDto;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Dtos.UserEntityDto;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.ReferralCodeUsage;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.FirebaseConnectionException;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.*;

@Service
public class UserEntityService {


    private static final Logger log = LoggerFactory.getLogger(UserEntityService.class);
    private final ReferralCodeUsageRepository referralCodeUsageRepository;
    private final RoleRepository roleRepository;
    private final UserEntityRepository userEntityRepository;
    private final FirebaseAuth firebaseAuth;
    private final ReferralCodeRepository referralCodeRepository;
    private final FirebaseRetryService firebaseRetryService;
    private final LocationRepository locationRepository;
    private final UserActivityService userActivityService;



    public UserEntityService(ReferralCodeUsageRepository referralCodeUsageRepository, RoleRepository roleRepository, UserEntityRepository userEntityRepository, FirebaseAuth firebaseAuth, ReferralCodeRepository referralCodeRepository, FirebaseRetryService firebaseRetryService, LocationRepository locationRepository, UserActivityService userActivityService) {
        this.referralCodeUsageRepository = referralCodeUsageRepository;
        this.roleRepository = roleRepository;
        this.userEntityRepository = userEntityRepository;
        this.firebaseAuth = firebaseAuth;
        this.referralCodeRepository = referralCodeRepository;
        this.firebaseRetryService = firebaseRetryService;
        this.locationRepository = locationRepository;
        this.userActivityService = userActivityService;
    }


    //Todo: load also user photo in future!,location??
    @Transactional(readOnly = true)
    public UserEntityDto getUserProfile(){
        String userFirebaseId = getUserFirebaseId();
        UserEntity userEntity = userEntityRepository.findByFirebaseId(userFirebaseId).orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return  userEntityToDto(userEntity);
    }

    private UserEntityDto userEntityToDto(UserEntity userEntity){
        if (userEntity.getRoles() == null || userEntity.getRoles().isEmpty()) {
            log.error("User {} doesn't have a role something is wrong",userEntity.getFirebaseId());
        }
        String userRole = getDominantRole(userEntity.getRoles());

        Location location = userEntity.getLocation();

        LocationDto locationDto = new LocationDto(
                location.getCountry(),
                location.getRegion(),
                location.getCity(),
                location.getAddressLine(),
                location.getPostalCode()
        );


        Boolean isReferralCodeOwner  = referralCodeRepository.existsByOwner_FirebaseId(getUserFirebaseId());
        Boolean hasUsedReferralCode = referralCodeUsageRepository.existsByUser_FirebaseId(getUserFirebaseId());
        ReferralCodeUsage usedReferralCode = referralCodeUsageRepository.findByUser_FirebaseId(getUserFirebaseId()).orElse(null);
        String code = "Not Used";
        if(usedReferralCode != null)code = usedReferralCode.getReferralCode().getCode();


        return new UserEntityDto(
                userEntity.getUsername(),userEntity.getEmail(),userEntity.getPhoneNumber()
                ,userEntity.getAvatar().getUrl(), userEntity.getAvatar().name(),userEntity.getRewardPoints(),
                userRole,userEntity.isEligibleForChat(),locationDto,userEntity.getAllTimeRewardPoints(),isReferralCodeOwner,hasUsedReferralCode,code);
    }


    @Transactional
    public void updateUsername(String username){
        if(userEntityRepository.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"User with username "+ username + "already exists");
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()-> new ResourceNotFoundException("User not found"));

        canUpdateProfile(userEntity);

        String pastUserName = userEntity.getUsername();

        userEntity.setUsername(username);

        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());

        userEntityRepository.save(userEntity);

        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_USERNAME, "User: " + getUserFirebaseId()+ " changed his username from: " + pastUserName + "to: " + userEntity.getUsername() );

        UpdateRequest request = new UpdateRequest(getUserFirebaseId())
                .setDisplayName(username);
        try {
            firebaseAuth.updateUser(
                    new UpdateRequest(getUserFirebaseId()).setDisplayName(username)
            );
        } catch (FirebaseAuthException e) {
            // αναγκάζουμε rollback
            throw new RuntimeException("Firebase update failed", e);
        }
    }


    @Transactional
    public void updateAvatar(Avatar avatar){
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                        .orElseThrow(()-> new ResourceNotFoundException("User not found"));
        canUpdateProfile(userEntity);
        userEntity.setAvatar(avatar);
        // 🔴 ΕΔΩ κλειδώνουμε για 10 λεπτά

        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());
        userEntityRepository.save(userEntity);
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_AVATAR, "User " + getUserFirebaseId() + " changed his avatar to: " + userEntity.getAvatar());
    }


    //Todo: send sms or email to verify before deleting
    @Transactional
    public void anonymizeUser() throws FirebaseAuthException {
        String firebaseId = getUserFirebaseId();
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User was not found"));
        userEntity.setUsername("deleted_user_" + userEntity.getId());
        userEntity.setEmail("anonymized_" + userEntity.getId() + "@example.com");
        userEntity.setAnonymized(true);
        userEntity.setFirebaseId("deleted_firebase_" + userEntity.getId());
        userEntity.setPhoneNumber("XXXXXX");
        userEntity.setAvatar(Avatar.DEFAULT);

        Location location = userEntity.getLocation();
        if (location != null) {
            userEntity.setLocation(null);       // σπάει το link στη μνήμη
            location.setUser(null);             // προαιρετικά, για καθαρότητα
            locationRepository.delete(location); // ✅ Σβήνει το row από DB
        }
        userEntityRepository.save(userEntity);
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_USERNAME, "User: " + firebaseId  + " deleted his account");
        firebaseRetryService.deleteUserFromFirebase(firebaseId);
//        firebaseAuth.revokeRefreshTokens(firebaseId);
    }




    //todo: for each userentity edit post delete api do a scheduled service


    /**
     * Assign roles to set to store roles into user table
     */
    public Set<Role> assignRoles(String role){
        Set<Role> roles = new HashSet<>();

        if(role.equals("Auctioneer")) {
            roles.add(roleRepository.findByName("Auctioneer").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
            roles.add(roleRepository.findByName("Bidder").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
        }else if(role.equals("Bidder")){// ToDo: Must put on startup roles in the database
            roles.add(roleRepository.findByName("Bidder").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
        }else return null;
        return roles;
    }

    @Transactional
    public void updateLocation(LocationDto locationDto) {
        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()->new ResourceNotFoundException("User not found"));
        canUpdateProfile(user);

        Location location = locationRepository.findByUserFirebaseId(getUserFirebaseId())
                .orElseThrow(()->new ResourceNotFoundException("Location was not found"));
        location.setCountry(locationDto.country());
        location.setRegion(locationDto.region());
        location.setCity(locationDto.city());
        location.setAddressLine(locationDto.addressLine());
        location.setPostalCode(locationDto.postalCode());
        locationRepository.save(location);
        // 🔴 ΕΔΩ κλειδώνουμε για 10 λεπτά
        user.setProfileLastUpdatedAt(LocalDateTime.now());
        userEntityRepository.save(user);
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_LOCATION, "User: " + getUserFirebaseId() +  " changed his Location");

    }



    private void canUpdateProfile(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdate = user.getProfileLastUpdatedAt();

        if (lastUpdate != null) {
            long millisecondsSinceLastUpdate =java.time.Duration.between(lastUpdate, now).toMillis();
            long minutesSinceLastUpdate = java.time.Duration.between(lastUpdate, now).toMinutes();

            if (millisecondsSinceLastUpdate < 100 || minutesSinceLastUpdate < 20) {
                long remaining = 20 - minutesSinceLastUpdate;
                throw new IllegalStateException(
                        "You can update your profile again in " + remaining + " minutes"
                );
            }
        }
    }



    //ToDo: na stelni last 10 auctions i last 6 months auction kati tetio
    //ToDo: mpori na gini me pagination gia dynamic loading
    //ToDo:@Cacheable(value = "auctionCache", key = "'auction_' + #id") for caching pages??
    //ToDo: caching with caffeine
//    @Transactional
//    public List<AuctionDto> pastUserBids(){
//        String userFirebaseId = getUserFirebaseId();
//        bidRepository.getAuctionsByFirebaseId(userFirebaseId);
//
//
//
//    }




    /**
     * Απλό update ρόλου για τον ΤΡΕΧΟΝΤΑ χρήστη (όχι admin flow).
     */
    @Transactional
    public void updateRole(String newRole){
        // 1) Validate τιμή ρόλου (BIDDER / AUCTIONEER κ.λπ.)
        if(newRole.equals("Bidder"))
            throw new IllegalArgumentException("You cannot go back to role Bidder");
        if (!isRoleValid(newRole))
            throw new IllegalArgumentException("Provided an invalid role: " + newRole);

        // 2) Πάρε firebaseId του τρέχοντος χρήστη από SecurityContext / token
        String firebaseId = getUserFirebaseId();

        // 3) Βρες τον user στη βάση
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with firebase id: { " + firebaseId + " } was not found"));

        canUpdateProfile(userEntity);

        // 4) Δες αν όντως αλλάζει κάτι
        String oldRole = getDominantRole(userEntity.getRoles());
        if (oldRole.equalsIgnoreCase(newRole)) {
            // Δεν υπάρχει αλλαγή → απλά κάνε return
            return;
        }

        // 5) Με βάση το string newRole, φτιάξε τα Role entities (ίδια λογική όπως στο admin)
        Set<Role> roles = assignRoles(newRole);  // υποθέτω ότι ήδη υπάρχει αυτό το method στην UserEntityService
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User didn't provide role");
        }

        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());
        userEntity.setRoles(roles);
        userEntityRepository.save(userEntity);

        userActivityService.saveUserActivityAsync(
                Endpoint.USER_UPDATE_ROLE,
                "User " + firebaseId + " updated role from " + oldRole + " to " + newRole
        );
        // 6) Ενημέρωσε τα custom claims στο Firebase (list με string roles)
        List<String> roleList = UserEntityHelper.assignRolesList(userEntity.getRoles());
        try{
            firebaseRetryService.setFirebaseClaims(firebaseId, Map.of("roles", roleList));
        }catch (Exception e) {
            throw new FirebaseConnectionException("Unexpected exception when communicating with firebase " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public AuthUserDto signIn() {
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()-> new IllegalArgumentException("User Not Found"));

        Boolean isReferralCodeOwner = referralCodeRepository.existsByOwner_FirebaseId(getUserFirebaseId());

        return new AuthUserDto(userEntity.getUsername(),getDominantRole(userEntity.getRoles()),isReferralCodeOwner);

    }
}




