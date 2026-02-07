package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.ReferralCodeDtoAdminResponse;
import com.example.webapp.BidNow.Dtos.ReferralCodeRequest;
import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.ReferralCodeRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 *
 * Referral Code Service
 *
 * Note:
 * - This is an admin Service where admin can inspect,
 * edit, add referral codes.
 *
 */
@Service
public class AdminReferralCodeService {

    private final UserActivityService userActivityService;
    private final ReferralCodeRepository referralCodeRepository;
    private final UserEntityRepository userEntityRepository;

    public AdminReferralCodeService(UserActivityService userActivityService, ReferralCodeRepository referralCodeRepository, UserEntityRepository userEntityRepository) {
        this.userActivityService = userActivityService;
        this.referralCodeRepository = referralCodeRepository;
        this.userEntityRepository = userEntityRepository;
    }


    /**
     * Returns a list of regerral codes so that admin can:
     * - View referral codes and their usage counts.
     * - Support investigation/auditing/edits of referral activity and achievements.
     *
     * @param page zero-based page index (negative values are treated as 0)
     * @param size page size (defaults to 20 if <= 0, max 100)
     * @return a page of referral codes mapped to admin DTOs
     */
    @Transactional(readOnly = true)
    public Page<ReferralCodeDtoAdminResponse> getReferralCodes(int page, int size) {

        if (size > 100) size = 100;

        if (size <= 0) size = 20;

        if (page < 0) page = 0;

        if ((long)page * size > 200000){ // Todo: Maybe change in future
            throw new IllegalArgumentException("Page number too large");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

        return referralCodeRepository
                .findAll(pageable)
                .map(this::referralToDto);
    }


    /**
     * Maps a ReferralCode entity to the admin DTO response.
     */
    private ReferralCodeDtoAdminResponse referralToDto(ReferralCode code){
        return new ReferralCodeDtoAdminResponse(
                code.getId(), code.getCode(), code.getOwner().getId(), code.getRewardPoints(),
                code.getOwnerRewardPoints(), code.getMaxUses(), code.getUsesSoFar(),code.getDisabled()
        );

    }

    /**
     * Admin can create a new referral code for a specific user
     *
     * @param codeDto request dto of admin
     */
    @Transactional
    public void createReferralCode(ReferralCodeRequest codeDto){
        UserEntity user = userEntityRepository.findById(codeDto.ownerId()).orElseThrow(()-> new IllegalArgumentException("User was not found"));
        if(referralCodeRepository.existsByOwner_FirebaseId(user.getFirebaseId()))
            throw new IllegalArgumentException("User: " + user.getFirebaseId() +  " is already a referral code owner");
        if(referralCodeRepository.existsByCode(codeDto.code()))
            throw new IllegalArgumentException("There is already a referral code with code name: " + codeDto.code());
        ReferralCode referralCode = new ReferralCode(codeDto.code(),
                user,
                codeDto.rewardPoints(),
                codeDto.ownerRewardPoints(),
                codeDto.maxUses(),
                0,
                codeDto.isDisabled());
        userActivityService.saveUserActivityAsync(Endpoint.CREATE_REFERRAL_CODE,"Admin created referral code: "+ codeDto.code() + " for user " + user.getId());

        referralCodeRepository.save(referralCode);
    }


    /**
     * Admin can edit an existing referral code
     * in the application except it's owner
     *
     * @param id referral code id
     * @param codeDto containing the new values
     * @return
     */
    @Transactional
    public ReferralCodeDtoAdminResponse editReferralCode(Long id, ReferralCodeRequest codeDto){
        ReferralCode referralCode = updateReferralCodeFromDto(id,codeDto);
        referralCodeRepository.save(referralCode);

        userActivityService.saveUserActivityAsync(Endpoint.EDIT_REFERRAL_CODE,"Admin edited referral code: "+ codeDto.code());

        return referralToDto(referralCode);
    }


    /**
     * Maps the admin DTO request to a ReferralCode entity response.
     */
    private ReferralCode updateReferralCodeFromDto(Long id, ReferralCodeRequest dto){
        ReferralCode referralCode = referralCodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Referral code: " + id + " not found"));

        if(!Objects.equals(referralCode.getOwner().getId(), dto.ownerId()))
            throw new IllegalArgumentException("Cannot change creator");

        referralCode.setCode(dto.code());
        referralCode.setMaxUses(dto.maxUses());
        referralCode.setRewardPoints(dto.rewardPoints());
        referralCode.setOwnerRewardPoints(dto.ownerRewardPoints());
        referralCode.setDisabled(dto.isDisabled());

        return referralCode;
    }

    /**
     * Admin can view a specific referral code
     *
     * @param code
     * @return
     */
    @Transactional(readOnly = true)
    public ReferralCodeDtoAdminResponse getReferralCode(String code) {
        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(IllegalArgumentException::new);
        return new ReferralCodeDtoAdminResponse(referralCode.getId(), referralCode.getCode(), referralCode.getOwner().getId(),
                referralCode.getRewardPoints(), referralCode.getOwnerRewardPoints(), referralCode.getMaxUses(),referralCode.getUsesSoFar(),referralCode.getDisabled());

    }



}
