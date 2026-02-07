package com.example.webapp.BidNow;

import com.example.webapp.BidNow.Dtos.ReferralCodeDtoAdminResponse;
import com.example.webapp.BidNow.Dtos.ReferralCodeRequest;
import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.ReferralCodeRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.Services.AdminReferralCodeService;
import com.example.webapp.BidNow.Services.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReferralCodeServiceTest {

    @Mock private UserEntityRepository userEntityRepository;
    @Mock private ReferralCodeRepository referralCodeRepository;
    @Mock private UserActivityService userActivityService;

    @InjectMocks
    private AdminReferralCodeService service;


    private ReferralCodeRequest validReq() {
        return new ReferralCodeRequest(
                "ABC123",
                10L,
                100L,
                50L,
                20,
                false
        );
    }



    private UserEntity user(long id, String firebaseId) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFirebaseId(firebaseId);
        return u;
    }

    @Test
    void createReferralCode_happyPath_savesReferralCode() {
        ReferralCodeRequest req = validReq();
        UserEntity u = user(10L, "fb-10");

        when(userEntityRepository.findById(10L)).thenReturn(Optional.of(u));
        when(referralCodeRepository.existsByOwner_FirebaseId("fb-10")).thenReturn(false);
        when(referralCodeRepository.existsByCode("ABC123")).thenReturn(false);

        service.createReferralCode(req);

        // Save called once with correct entity
        ArgumentCaptor<ReferralCode> captor = ArgumentCaptor.forClass(ReferralCode.class);
        verify(referralCodeRepository, times(1)).save(captor.capture());

        ReferralCode saved = captor.getValue();
        assertEquals("ABC123", saved.getCode());
        assertEquals(u, saved.getOwner());
        assertEquals(100L, saved.getRewardPoints());
        assertEquals(50L, saved.getOwnerRewardPoints());
        assertEquals(20, saved.getMaxUses());
        assertEquals(0, saved.getUsesSoFar());
        assertEquals(false, saved.getDisabled());
    }

    @Test
    void createReferralCode_userNotFound_throws_andDoesNotSave() {
        ReferralCodeRequest req = validReq();

        when(userEntityRepository.findById(10L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createReferralCode(req)
        );
        assertTrue(ex.getMessage().contains("User was not found"));

        verify(referralCodeRepository, never()).save(any());
        verify(userActivityService, never()).saveUserActivityAsync(any(), anyString());
    }

    @Test
    void createReferralCode_userAlreadyOwner_throws_andDoesNotSave() {
        ReferralCodeRequest req = validReq();
        UserEntity u = user(10L, "fb-10");

        when(userEntityRepository.findById(10L)).thenReturn(Optional.of(u));
        when(referralCodeRepository.existsByOwner_FirebaseId("fb-10")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createReferralCode(req)
        );
        assertTrue(ex.getMessage().contains("already a referral code owner"));

        verify(referralCodeRepository, never()).save(any());
        verify(userActivityService, never()).saveUserActivityAsync(any(), anyString());
    }

    @Test
    void createReferralCode_codeAlreadyExists_throws_andDoesNotSave() {
        ReferralCodeRequest req = validReq();
        UserEntity u = user(10L, "fb-10");

        when(userEntityRepository.findById(10L)).thenReturn(Optional.of(u));
        when(referralCodeRepository.existsByOwner_FirebaseId("fb-10")).thenReturn(false);
        when(referralCodeRepository.existsByCode("ABC123")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createReferralCode(req)
        );
        assertTrue(ex.getMessage().contains("already a referral code"));

        verify(referralCodeRepository, never()).save(any());
        verify(userActivityService, never()).saveUserActivityAsync(any(), anyString());
    }

    /////////////////////// EDIT REFERRAL CODE /////////////////////////

    @Test
    void editReferralCode_happyPath_updatesSavesLogsAndReturnsDto() {
        Long id = 1L;

        UserEntity owner = new UserEntity();
        owner.setId(10L);

        ReferralCode rc = new ReferralCode();
        rc.setId(id);
        rc.setOwner(owner);
        rc.setCode("OLD");
        rc.setMaxUses(1);
        rc.setRewardPoints(1L);
        rc.setOwnerRewardPoints(1L);
        rc.setDisabled(true);

        ReferralCodeRequest dto = new ReferralCodeRequest(
                "NEW", 10L, 100L, 50L, 20, false
        );

        when(referralCodeRepository.findById(id)).thenReturn(Optional.of(rc));

        ReferralCodeDtoAdminResponse resp = service.editReferralCode(id, dto);

        verify(referralCodeRepository, times(1)).findById(id);

        ArgumentCaptor<ReferralCode> captor = ArgumentCaptor.forClass(ReferralCode.class);
        verify(referralCodeRepository, times(1)).save(captor.capture());

        ReferralCode saved = captor.getValue();
        assertEquals("NEW", saved.getCode());
        assertEquals(20, saved.getMaxUses());
        assertEquals(100L, saved.getRewardPoints());
        assertEquals(50L, saved.getOwnerRewardPoints());
        assertFalse(saved.getDisabled());
        assertSame(owner, saved.getOwner()); // owner δεν αλλάζει

        verify(userActivityService, times(1))
                .saveUserActivityAsync(Endpoint.EDIT_REFERRAL_CODE, "Admin edited referral code: NEW");

        // mapping check (ό,τι fields έχει το dto response σου)
        assertEquals("NEW", resp.code());
    }

    @Test
    void editReferralCode_notFound_throwsAndDoesNotSaveOrLog() {
        when(referralCodeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.editReferralCode(1L, new ReferralCodeRequest("NEW", 10L, 1L, 1L, 1, false)));

        verify(referralCodeRepository, never()).save(any());
        verify(userActivityService, never()).saveUserActivityAsync(any(), any());
    }

    @Test
    void editReferralCode_changeCreator_throwsAndDoesNotSaveOrLog() {
        UserEntity owner = new UserEntity();
        owner.setId(10L);

        ReferralCode rc = new ReferralCode();
        rc.setId(1L);
        rc.setOwner(owner);

        when(referralCodeRepository.findById(1L)).thenReturn(Optional.of(rc));

        ReferralCodeRequest dto = new ReferralCodeRequest("NEW", 999L, 1L, 1L, 1, false);

        assertThrows(IllegalArgumentException.class,
                () -> service.editReferralCode(1L, dto));

        verify(referralCodeRepository, never()).save(any());
        verify(userActivityService, never()).saveUserActivityAsync(any(), any());
    }



}
