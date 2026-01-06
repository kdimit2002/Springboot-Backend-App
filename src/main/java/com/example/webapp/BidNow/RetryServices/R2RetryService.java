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

/**
 * Retry service for cloudflare external R2 object storage
 * Retry service is used for consistency between the application's database
 * on connectivity and transient errors
 *
 */
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
     * Upload image to R2 with retry.
     *
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
            throw new R2StorageException("I/O error while uploading image to R2", e);
        }
    }

    /**
     * Recovery method.
     * Triggered after 5 failed attempts to upload image to R2.
     */
    @Recover
    public String recoverUploadImage(Exception e,
                                     MultipartFile file,
                                     Long auctionId,
                                     int imageIndex,
                                     String ownerFirebaseUid) {

        log.error("Failed to upload image to R2 after retries. auctionId={}, owner={}, index={}, error={}",
                auctionId, ownerFirebaseUid, imageIndex, e.getMessage(), e);

        // Inform admin
        emailService.sendSimpleEmailAsync( // Informational email to BidNow if something went wrong
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

        throw new R2StorageException("Failed to upload image to R2 after retries", e);
    }
}
