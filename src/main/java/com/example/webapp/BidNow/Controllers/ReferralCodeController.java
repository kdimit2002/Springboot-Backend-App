package com.example.webapp.BidNow.Controllers;


import com.example.webapp.BidNow.Dtos.ReferralCodeUsageResponse;
import com.example.webapp.BidNow.Dtos.ReferralCodeUserResponse;
import com.example.webapp.BidNow.Services.ReferralCodeService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;


/**
 * Controller for user's referral code actions.
 *
 * Base path: /referralCode
 *
 * Provides:
 *  - Use a referral code
 *  - view usage
 *  - check if current user is a referral code user.
 */
@RestController
@RequestMapping("/referralCode")
public class ReferralCodeController {

    private final ReferralCodeService referralCodeService;

    public ReferralCodeController(ReferralCodeService referralCodeService) {
        this.referralCodeService = referralCodeService;
    }

    /** Simple response dto for returning a referral code string. */
    public record ReferralCodeResponse(String code){}

    /**
     * Use a referral code.
     *
     * POST /referralCode/useReferralCode/{code}
     *
     * @param code referral code value
     * @return response containing the registered code as string
     */
    @PostMapping(value = "/useReferralCode/{code}")
    public ResponseEntity<ReferralCodeResponse> useReferralCode(@PathVariable String code){
        if(code.isBlank())throw new IllegalArgumentException("Referral Code cannot be blank");
        return ResponseEntity.ok(new ReferralCodeResponse(referralCodeService.useReferralCode(code)));
    }


    /**
     * Get referral code usage information for the current user.
     *
     * This Api is used only from referral code owners
     * of the application to inspect the users that applied
     * their referral code.
     *
     * GET /referralCode/getCodeUsage?page=0&size=10
     *
     * @param page page number (default 0)
     * @param size page size (default 10)
     * @return page of usage entries as ReferralCodeUsageResponse
     */
    @GetMapping(value = "/getCodeUsage")
    public ResponseEntity<Page<ReferralCodeUsageResponse>> referralCodeUsage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(referralCodeService.referralCodeUsage(page, size));
    }


    /**
     * Check if the current user used a referral code
     *
     * GET /referralCode/isReferralCodeUser
     *
     * @return ReferralCodeUserResponse indicating whether the user is part of the referral program
     */
    @GetMapping(value = "/isReferralCodeUser")
    public ResponseEntity<ReferralCodeUserResponse> isReferralCodeUser(
    ) {
        return ResponseEntity.ok(referralCodeService.isReferralCodeUser());
    }

}
