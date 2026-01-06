package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.ReferralCodeUsageResponse;
import com.example.webapp.BidNow.Dtos.ReferralCodeUserResponse;
import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.ReferralCodeUsage;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.ReferralCodeRepository;
import com.example.webapp.BidNow.Repositories.ReferralCodeUsageRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * ReferralCodeService
 *
 */
@Service
public class ReferralCodeService {


    //TODO ACTIVE USERS A DAY ADMIN DASHBOARD

    private final UserActivityService userActivityService;
    private final ReferralCodeRepository referralCodeRepository;
    private final UserEntityRepository userEntityRepository;
    private final ReferralCodeUsageRepository referralCodeUsageRepository;

    public ReferralCodeService(UserActivityService userActivityService, ReferralCodeRepository referralCodeRepository, UserEntityRepository userEntityRepository, ReferralCodeUsageRepository referralCodeUsageRepository) {
        this.userActivityService = userActivityService;
        this.referralCodeRepository = referralCodeRepository;
        this.userEntityRepository = userEntityRepository;
        this.referralCodeUsageRepository = referralCodeUsageRepository;
    }

    /**
     * Applies a referral code for the current user.
     *
     * @param code, string sequence that a user used and is compared with the existing referral codes
     * @return
     */
    @Transactional
    public String useReferralCode(String code) {

        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));

        if (referralCode.getDisabled()) {
            throw new IllegalStateException("Referral code is disabled");
        }


        // Check if referral code exceeded its max number of uses
        if (referralCode.getMaxUses() > 0 &&
                referralCode.getUsesSoFar() >= referralCode.getMaxUses()) {
            throw new IllegalStateException("Referral code has reached max uses");
        }

        // User cannot use his own referral code
        if (referralCodeRepository.existsByOwner(user)) {
            throw new IllegalArgumentException("You are a referral code owner you cannot use any referral code");
        }

        // check if user already used his referral code
        if (referralCodeUsageRepository.existsByUser_FirebaseId(user.getFirebaseId())) {
            throw new IllegalArgumentException("You have already received a referral reward.");
        }

        // create referral code usage
        ReferralCodeUsage usage = new ReferralCodeUsage(user, referralCode);
        referralCodeUsageRepository.save(usage);

        // increment uses so far of the referral code
        referralCode.setUsesSoFar(referralCode.getUsesSoFar() + 1);

        // Give the rewards to owner and user if referral code (points are applied when referral code was created from admin)
        Long points = referralCode.getRewardPoints();
        userEntityRepository.incrementPoints(user.getId(), referralCode.getRewardPoints());
        userEntityRepository.incrementPoints(referralCode.getOwner().getId(), referralCode.getOwnerRewardPoints());

        //log
        userActivityService.saveUserActivityAsync(Endpoint.USE_REFERRAL_CODE, "User: " + getUserFirebaseId() +
                " just used referral code: " + referralCode.getCode()
                + " of user " + referralCode.getOwner() + " and gained " +
                referralCode.getRewardPoints() + " points. Creator gained " + referralCode.getOwnerRewardPoints() + " points");


        return "Referral code has been created, You earned " + points + " points!";
    }

    /**
     * Show referral code owner's about the usage of his code
     *
     * Notes:
     *  - Only referral code owners can view this info
     *
     * @param page
     * @param size
     * @return
     */
    @Transactional(readOnly = true)
    public Page<ReferralCodeUsageResponse> referralCodeUsage(int page, int size) {

        if(!referralCodeRepository.existsByOwner_FirebaseId(getUserFirebaseId()))
            throw new IllegalArgumentException("You are not a referral creator");

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "usedAt") // πιο χρήσιμο να τα βλέπεις από τα πιο πρόσφατα
        );

        Page<ReferralCodeUsage> usagesPage =
                referralCodeUsageRepository.findByReferralCode_Owner_FirebaseId(
                        getUserFirebaseId(),
                        pageable
                );

        // map from entity to DTO
        return usagesPage.map(usage -> {

            return new ReferralCodeUsageResponse(
                    usage.getUser().getUsername(),
                    usage.getReferralCode().getCode()
            );
        });
    }

    /**
     * Return dto response with the code of the referral code if the
     * user is a referral code owner
     * @return dto with the code of the referral code
     */
    @Transactional(readOnly = true)
    public ReferralCodeUserResponse isReferralCodeUser() {
        ReferralCode referralCode = referralCodeRepository.findByOwner_FirebaseId(getUserFirebaseId())
                .orElseThrow(IllegalArgumentException::new);

        return new ReferralCodeUserResponse(referralCode.getCode());


    }


}
