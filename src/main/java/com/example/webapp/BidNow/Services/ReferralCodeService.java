package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Controllers.ReferralCodeController;
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

@Service
public class ReferralCodeService {


    //TODO ACTIVE USERS A DAY ADMIN DASHBOARD

    private final UserActivityService userActivityService;
    private final ReferralCodeRepository referralCodeRepository;
    private final UserEntityRepository userEntityRepository;
    private final ReferralCodeUsageRepository referralCodeUsageRepository;
    private final EmailService emailService;

    public ReferralCodeService(UserActivityService userActivityService, ReferralCodeRepository referralCodeRepository, UserEntityRepository userEntityRepository, ReferralCodeUsageRepository referralCodeUsageRepository, EmailService emailService) {
        this.userActivityService = userActivityService;
        this.referralCodeRepository = referralCodeRepository;
        this.userEntityRepository = userEntityRepository;
        this.referralCodeUsageRepository = referralCodeUsageRepository;
        this.emailService = emailService;
    }

    @Transactional
    public String useReferralCode(String code) {

        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));

        if (referralCode.getDisabled()) {
            throw new IllegalStateException("Referral code is disabled");
        }


        // Έλεγχος maxUses
        if (referralCode.getMaxUses() > 0 &&
                referralCode.getUsesSoFar() >= referralCode.getMaxUses()) {
            throw new IllegalStateException("Referral code has reached max uses");
        }

        // Να μην μπορεί να βάλει τον δικό του code
        if (referralCodeRepository.existsByOwner(user)) {
            throw new IllegalArgumentException("You are a referral code owner you cannot use any referral code");
        }

        // Έλεγχος αν το έχει ξαναχρησιμοποιήσει
        if (referralCodeUsageRepository.existsByUser_FirebaseId(user.getFirebaseId())) {
            throw new IllegalArgumentException("You have already received a referral reward.");
        }

        // ✅ ΕΔΩ δημιουργείται το Usage
        ReferralCodeUsage usage = new ReferralCodeUsage(user, referralCode);
        referralCodeUsageRepository.save(usage);

        // ✅ Αυξάνεται το counter
        referralCode.setUsesSoFar(referralCode.getUsesSoFar() + 1);

        // ✅ Εδώ μπορείς να δώσεις reward points
        Long points = referralCode.getRewardPoints();
        userEntityRepository.incrementPoints(user.getId(), referralCode.getRewardPoints());
        userEntityRepository.incrementPoints(referralCode.getOwner().getId(), referralCode.getOwnerRewardPoints());

        userActivityService.saveUserActivityAsync(Endpoint.USE_REFERRAL_CODE, "User: " + getUserFirebaseId() +
                " just used referral code: " + referralCode.getCode()
                + " of user " + referralCode.getOwner() + " and gained " +
                referralCode.getRewardPoints() + " points. Creator gained " + referralCode.getOwnerRewardPoints() + " points");


        //log
        return "Referral code has been created, You earned " + points + " points!";
    }

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

        // map από entity -> DTO
        return usagesPage.map(usage -> {

            return new ReferralCodeUsageResponse(
                    usage.getUser().getUsername(),
                    usage.getReferralCode().getCode()
            );
        });
    }

    @Transactional(readOnly = true)
    public ReferralCodeUserResponse isReferralCodeUser() {
        ReferralCode referralCode = referralCodeRepository.findByOwner_FirebaseId(getUserFirebaseId())
                .orElseThrow(IllegalArgumentException::new);

        return new ReferralCodeUserResponse(referralCode.getCode());


    }


//    @Transactional(readOnly = true)
//    // Μέσα στο service σου
//    public ReferralCodeCreatorMonthlyAmount getReferralCodeCreatorMonthlyAmount() {
//
//        if(!referralCodeRepository.existsByCreator_FirebaseId(getUserFirebaseId()))
//            throw new IllegalArgumentException("You are not a referral creator");
//
//        List<ReferralCodeUsage> usages =
//                referralCodeUsageRepository.findByReferralCode_Creator_FirebaseId(getUserFirebaseId());
//
//        // Τελευταίοι 5 μήνες: από (now - 4 μήνες) μέχρι now
//        YearMonth end = YearMonth.now();
//        YearMonth start = end.minusMonths(4);   // σύνολο 5 μήνες
//
//        // Ένα LinkedHashMap για να κρατάμε τη σειρά: start -> ... -> end
//        Map<YearMonth, BigDecimal> monthTotals = new LinkedHashMap<>();
//
//        YearMonth cursor = start;
//        while (!cursor.isAfter(end)) {
//            monthTotals.put(cursor, BigDecimal.ZERO);
//            cursor = cursor.plusMonths(1);
//        }
//
//        // Γεμίζουμε τα ποσά
//        for (ReferralCodeUsage usage : usages) {
//            LocalDateTime usedAt = usage.getUsedAt();
//            if (usedAt == null) {
//                continue;
//            }
//
//            YearMonth usageMonth = YearMonth.from(usedAt);
//
//            // Αν το usage είναι μεταξύ start και end (inclusive)
//            if (!usageMonth.isBefore(start) && !usageMonth.isAfter(end)) {
//                BigDecimal current = monthTotals.getOrDefault(usageMonth, BigDecimal.ZERO);
//                BigDecimal creatorAmount = usage.getCreatorAmount() != null
//                        ? usage.getCreatorAmount()
//                        : BigDecimal.ZERO;
//
//                BigDecimal updated = current.add(creatorAmount);
//                monthTotals.put(usageMonth, updated);
//            }
//        }
//
//        // Μετατρέπουμε σε DTO objects
//        List<MonthlyAmount> monthlyAmounts = monthTotals.entrySet().stream()
//                .map(entry -> {
//                    YearMonth ym = entry.getKey();
//                    BigDecimal amount = entry.getValue();
//                    Month monthEnum = ym.getMonth();
//                    return new MonthlyAmount(
//                            ym.getYear(),
//                            ym.getMonthValue(),
//                            monthEnum.name(),  // ή με Locale αν θες ελληνικά
//                            amount
//                    );
//                })
//                .collect(Collectors.toList());
//
//        return new ReferralCodeCreatorMonthlyAmount(monthlyAmounts);
//    }

}
