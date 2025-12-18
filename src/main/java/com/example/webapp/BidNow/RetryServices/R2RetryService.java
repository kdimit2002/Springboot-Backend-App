package com.example.webapp.BidNow.RetryServices;

import com.example.webapp.BidNow.Exceptions.R2StorageException;
import com.example.webapp.BidNow.Services.EmailService;
import com.example.webapp.BidNow.Services.R2StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Service
public class R2RetryService {

    private static final Logger log = LoggerFactory.getLogger(R2RetryService.class);

    private final R2StorageService r2StorageService;
    private final EmailService emailService;

    public R2RetryService(R2StorageService r2StorageService,
                          EmailService emailService) {
        this.r2StorageService = r2StorageService;
        this.emailService = emailService;
    }

    /**
     * Upload εικόνας στο R2 με retry.
     *
     * Retry για:
     *  - network errors
     *  - S3/R2 exceptions
     *  - I/O errors (wrapped σε R2StorageException)
     */
    @Retryable(
            recover = "recoverUploadImage",
            retryFor = {
                    ConnectException.class,
                    SocketTimeoutException.class,
                    ResourceAccessException.class
            },
            maxAttempts = 5,
            backoff = @Backoff(delay = 500, multiplier = 2) // 0.5s, 1s, 2s, 4s, 8s
    )
    public String uploadImageWithRetry(MultipartFile file,
                                       Long auctionId,
                                       int imageIndex,
                                       String ownerFirebaseUid) {

        try {
            log.warn("Attempting R2 upload for auctionId={}, index={}, owner={}",
                    auctionId, imageIndex, ownerFirebaseUid);

            String url = r2StorageService.uploadImage(file, auctionId, imageIndex, ownerFirebaseUid);

            log.info("Successfully uploaded to R2 (auctionId={}, index={}, url={})",
                    auctionId, imageIndex, url);

            return url;
        } catch (IOException e) {
            // Μετατρέπουμε το checked IOException σε unchecked R2StorageException
            // ώστε να ενεργοποιηθεί το @Retryable
            throw new R2StorageException("I/O error while uploading image to R2", e);
        }
    }

    /**
     * Εκτελείται ΜΟΝΟ όταν εξαντληθούν όλα τα retries και πάλι αποτύχει.
     */
    @Recover
    public String recoverUploadImage(Exception e,
                                     MultipartFile file,
                                     Long auctionId,
                                     int imageIndex,
                                     String ownerFirebaseUid) {

        log.error("Failed to upload image to R2 after retries. auctionId={}, owner={}, index={}, error={}",
                auctionId, ownerFirebaseUid, imageIndex, e.getMessage(), e);

        // Προαιρετικά: ενημέρωση admin
        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "R2 image upload failed",
                """
                Failed to upload image to R2 after multiple retries.
                
                Auction ID: %d
                Owner Firebase UID: %s
                Image index: %d
                
                Error: %s
                """.formatted(auctionId, ownerFirebaseUid, imageIndex, e.getMessage())
        );

        // Τελική εξαίρεση που θα φτάσει μέχρι τον controller (και στο GlobalExceptionHandler)
        throw new R2StorageException("Failed to upload image to R2 after retries", e);
    }
}
