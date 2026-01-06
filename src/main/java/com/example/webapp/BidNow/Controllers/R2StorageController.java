package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Image;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Services.ImageService;
import com.example.webapp.BidNow.Services.R2StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;


/**
 * Controller for file/image uploads (auction photos).
 *
 * Base path: /api/files
 * Provides:
 *  - Uploads multiple images
 *  - Uploads single image
 *  for a specific auction.
 *
 */
@RestController
@RequestMapping("/api/files")
public class R2StorageController {

    private final ImageService imageService;

    public R2StorageController(ImageService imageService){
        this.imageService = imageService;
    }

    /**
     * Upload multiple images for a specific auction.
     *
     * POST /api/files/{auctionId}/images
     * Content-Type: multipart/form-data   ->
     * Field: "files" (multiple files)
     *
     * @param auctionId auction id
     * @param files     list of images uploaded from client (binary data)
     * @return list of public URLs for the uploaded images
     */
    @PostMapping(
            value = "/{auctionId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE   // Request is multipart/form-data (one or more parts: file,id not a single json)
    )
    public ResponseEntity<List<String>> uploadAuctionImages(
            @PathVariable Long auctionId,
            @RequestPart("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }

        List<Image> images = imageService.uploadAuctionPhotos(auctionId, files);

        // Return images public urls to frontend
        List<String> urls = images.stream()
                .map(Image::getUrl)
                .toList();

        return ResponseEntity.ok(urls);
    }

    /**
     * Upload a single image for a specific auction.
     *
     * Todo: if needed in future change the response type to a DTO
     */
    @PostMapping(
            value = "/{auctionId}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> uploadSingleAuctionImage(
            @PathVariable Long auctionId,
            @RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        List<Image> images = imageService.uploadAuctionPhotos(auctionId, List.of(file));
        String url = images.get(0).getUrl();

        return ResponseEntity.ok(url);
    }
}