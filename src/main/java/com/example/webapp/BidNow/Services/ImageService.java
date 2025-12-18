package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Image;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.RetryServices.R2RetryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

@Service
public class ImageService {

    private final AuctionRepository auctionRepository;
    private final R2RetryService r2RetryService;

    public ImageService(AuctionRepository auctionRepository,
                        R2RetryService r2RetryService) {
        this.auctionRepository = auctionRepository;
        this.r2RetryService = r2RetryService;
    }

    @Transactional
    public List<Image> uploadAuctionPhotos(Long auctionId,
                                           List<MultipartFile> files) {

        // Ο χρήστης πρέπει να είναι owner αυτής της auction
        Auction auction = auctionRepository
                .findByIdAndOwnerFirebaseId(auctionId, getUserFirebaseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        int existing = auction.getAuctionImages().size();
        if (existing + files.size() > 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot upload more than 8 images"
            );
        }

        int baseOrder = existing + 1;
        List<Image> saved = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            int sortOrder = baseOrder + i;

            // 1) Διαβάζουμε διαστάσεις εικόνας (τοπικό I/O)
            int w, h;
            try (InputStream in = file.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid image file"
                    );
                }
                w = img.getWidth();
                h = img.getHeight();
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Failed to read image file",
                        e
                );
            }

            // 2) Upload στο R2 ΜΕ retry λογική
            String url = r2RetryService.uploadImageWithRetry(
                    file,
                    auctionId,
                    sortOrder,
                    getUserFirebaseId()
            );

            // 3) Δημιουργία Image entity και σύνδεση με auction
            Image image = new Image();
            image.setAuction(auction);
            image.setUrl(url);
            image.setSizeBytes(file.getSize());
            image.setFormat(resolveFormat(file.getContentType(), file.getOriginalFilename()));
            image.setWidth(w);
            image.setHeight(h);
            image.setSortOrder(sortOrder);

            auction.getAuctionImages().add(image);
            saved.add(image);
        }

        // @Transactional + cascade = ALL → το auction.save θα γίνει αυτόματα
        return saved;
    }

    private String sanitize(String name) {
        if (name == null) return "unnamed";
        return name.trim().replaceAll("\\s+", "_");
    }

    private String resolveFormat(String contentType, String filename) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType.substring("image/".length()); // jpeg, png, webp...
        }
        int dot = filename.lastIndexOf('.');
        return (dot > 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "unknown";
    }
}