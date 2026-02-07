package com.example.webapp.BidNow.Controllers.Admin;

import com.example.webapp.BidNow.Dtos.ReferralCodeDtoAdminResponse;
import com.example.webapp.BidNow.Dtos.ReferralCodeRequest;
import com.example.webapp.BidNow.Services.AdminReferralCodeService;
import com.example.webapp.BidNow.Services.AdminUserEntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Admin controller for managing referral codes.
 *
 * Base path: /api/admin
 */
@RestController
@RequestMapping("/api/admin")
public class AdminReferralCodeController {

    private final AdminReferralCodeService adminReferralCodeService;

    public AdminReferralCodeController(AdminReferralCodeService adminReferralCodeService) {
        this.adminReferralCodeService = adminReferralCodeService;
    }

    /**
     * Simple response DTO for create endpoint.
     * Returns the created referral code string.
     */
    public record ReferralCodeResponse(
            String name
    ) {}

    /**
     * Get referral codes with pagination.
     *
     * GET /api/admin/referralCodes?page=0&size=20
     *
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return paginated list of referral codes
     */
    @GetMapping(value = "/referralCodes")
    public ResponseEntity<Page<ReferralCodeDtoAdminResponse>> referralCodes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        return ResponseEntity.ok(adminReferralCodeService.getReferralCodes(page, size));
    }

    /**
     * Get a single referral code by code string.
     *
     * GET /api/admin/referralCodes/{code}
     *
     * @param code referral code value
     * @return referral code details
     */
    @GetMapping(value = "/referralCodes/{code}")
    public ResponseEntity<ReferralCodeDtoAdminResponse> referralCodes(
            @PathVariable String code
    ){
        return ResponseEntity.ok(adminReferralCodeService.getReferralCode(code));
    }


    /**
     * Create a new referral code.
     *
     * POST /api/admin/createReferralCode
     *
     * Notes:
     *  - ReferralCode's code must be unique
     *  - ReferralCode's owner must be unique
     *
     * @param referralCodeRequest request payload (e.g. code, metadata)
     * @return created referral code in a small response object
     */
    @PostMapping(value = "/createReferralCode")
    public ResponseEntity<ReferralCodeResponse> createReferralCode(@Valid @RequestBody ReferralCodeRequest referralCodeRequest){
        adminReferralCodeService.createReferralCode(referralCodeRequest);
        return ResponseEntity.ok(new ReferralCodeResponse(referralCodeRequest.code()));
    }



    /**
     * Edit an existing referral code by id.
     *
     * PATCH /api/admin/editReferralCode/{id}
     *
     * @param id          referral code id
     * @param codeRequest updated referral code payload
     * @return updated referral code details
     */
    @PatchMapping(value = "/editReferralCode/{id}")
    public ResponseEntity<ReferralCodeDtoAdminResponse> editReferralCode(
            @PathVariable Long id,
            @RequestBody ReferralCodeRequest codeRequest){

        return ResponseEntity.ok(adminReferralCodeService.editReferralCode(id,codeRequest));
    }
}
