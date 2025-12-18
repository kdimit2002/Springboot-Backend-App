package com.example.webapp.BidNow.helpers;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * @Author Kendeas
 */
public class ImageFileValidator {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Validate Auction list of photos
     *
     */
    public static void validateList(List<MultipartFile> files) throws IOException{
        for(MultipartFile file : files)validate(file);
    }

    /**
     *
     * Validate each photo
     * maxSize = 5
     * format = png,jpg,webp
     * binary signature = png,jpg,webp
     * Read file as image (if not image then null)
     *
     */
    // ToDo: HEIC format
    public static boolean validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File is too large (max 5MB).");
        }

        String mime = file.getContentType();
        byte[] data = file.getBytes();

        if (!isSupportedMimeType(mime)) {
            throw new IllegalArgumentException("Unsupported image format. Only JPG, PNG, and WebP are allowed.");
        }

        if (!hasValidMagicBytes(data)) {
            throw new IllegalArgumentException("Invalid image file signature.");
        }

        // Read file as image (if not image then null)
        BufferedImage img = ImageIO.read(file.getInputStream());
        if (img == null) {
            throw new IllegalArgumentException("Invalid or corrupted image.");
        }

        if (img.getWidth() < 1 || img.getHeight() < 1) {
            throw new IllegalArgumentException("Image has invalid dimensions.");
        }
        return true;
    }

    private static boolean isSupportedMimeType(String mime) {
        return mime != null && (
                mime.equals("image/jpeg") ||
                        mime.equals("image/png")  ||
                        mime.equals("image/webp")
        );
    }

    /**
     * Validate photo extensions png,jpeg,webp -> binary check
     *
     */
    private static boolean hasValidMagicBytes(byte[] data) {
        if (data.length < 12) return false;

        // JPEG FF D8
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return true;

        // PNG 89 50 4E 47
        if ((data[0] & 0xFF) == 0x89 && data[1]=='P' && data[2]=='N' && data[3]=='G') return true;

        // WebP RIFF....WEBP
        if (data[0]=='R' && data[1]=='I' && data[2]=='F' && data[3]=='F' &&
                data[8]=='W' && data[9]=='E' && data[10]=='B' && data[11]=='P') return true;

        return false;
    }
}
