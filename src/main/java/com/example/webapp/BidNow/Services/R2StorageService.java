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

@Service
public class R2StorageService {

    private final S3Client r2Client;
    private final String bucket;
    private final String publicBaseUrl; // https://pub-xxxx.r2.dev


    public R2StorageService(S3Client r2Client,
                            @Value("${cloudflare.r2.bucket}") String bucket,
                            @Value("${cloudflare.r2.public-base-url}") String publicBaseUrl) {
        this.r2Client = r2Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }


    // Todo: Retry Service
    public String uploadImage(MultipartFile file,
                                     Long auctionId,
                                     int imageIndex,
                                     String ownerFirebaseUid) throws IOException {

        String key = "images/" + ownerFirebaseUid + "/" + auctionId + "/" + imageIndex + "/"
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



//    public record CustomImage(byte[] image,String username,String format){}

//                if(ImageFileValidator.validate(file)){
//        compressedImage = imageCompressionService.compressAndResize(file, Purpose.USER);
//        String format = detectImageFormat(compressedImage),username = userRecord.getDisplayName();
//
//        if(format.equals("unknown")) {
//            throw new IllegalArgumentException("Unsupported file type");
//        }
//
//        CustomImage customImage = new CustomImage(compressedImage,username,format);//ToDo: change username, it may not be always unique
//        userPhoto = extractImageInfo(customImage);
//    }
//
//
//    /**
//     *
//     * Extract image metadata in order to store in sql database
//     */
//    private Photo extractImageInfo(CustomImage customImage) throws IOException {
//        String filename = customImage.username() + "." + customImage.format();
//        String url = "https://" + filename;//ToDo: this is prototype
//        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(customImage.image()));
//        int width = bufferedImage.getWidth(),height = bufferedImage.getHeight();
//        double sizeMB = customImage.image().length / (1024.00 * 1024.00);
//        return new UserPhoto(filename,url,sizeMB,customImage.format(),width,height,false);
//    }

}
