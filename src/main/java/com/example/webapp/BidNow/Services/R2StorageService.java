package com.example.webapp.BidNow.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * R2StorageService
 *
 * - Uploads image files to Cloudflare R2.
 * - Generates a unique, structured object key to avoid collisions.
 * - Returns a public URL for direct client access.
 *
 */

@Service
public class R2StorageService {

    private final S3Client r2Client;
    private final String bucket;
    private final String publicBaseUrl;


    public R2StorageService(S3Client r2Client,
                            @Value("${cloudflare.r2.bucket}") String bucket,
                            @Value("${cloudflare.r2.public-base-url}") String publicBaseUrl) {
        this.r2Client = r2Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }


    /**
     * Called by image service
     *
     * Calls cloudflare r2 storage apis to upload the image there
     * with the key filename
     * @param file
     * @param auctionId
     * @param imageIndex
     * @param ownerFirebaseUid
     * @return
     * @throws IOException
     */
    public String uploadImage(MultipartFile file,
                                     Long auctionId,
                                     int imageIndex,
                                     String ownerFirebaseUid) throws IOException {

        String key = "images/" + ownerFirebaseUid + "/" + auctionId + "/" + imageIndex + "/" // filename in r2
                + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        r2Client.putObject(
                putRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        return publicBaseUrl + key;
    }

    // ToDo Load image to a database

}
