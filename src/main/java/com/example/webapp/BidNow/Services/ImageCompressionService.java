package com.example.webapp.BidNow.Services;


import com.example.webapp.BidNow.Enums.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.*;

/**
 * @Author Kendeas
 */
@Service
public class ImageCompressionService {

    // --- Ρυθμίσεις διαστάσεων ---
    private static final int USER_MAX_WIDTH   = 520;
    private static final int USER_MAX_HEIGHT  = 520;
    private static final int AUCTION_MAX_WIDTH  = 1600;
    private static final int AUCTION_MAX_HEIGHT = 1600;

    // --- Ρυθμίσεις ποιότητας / autotune ---
    private static final float JPEG_QUALITY_MIN = 0.60f;
    private static final float JPEG_QUALITY_MAX = 0.95f;
    private static final float WEBP_QUALITY_MIN = 0.55f;
    private static final float WEBP_QUALITY_MAX = 0.95f;

    // Κατώφλι «οπτικά μη αντιληπτή απώλεια» (τυπικά 40–42 dB).
    private static final double TARGET_PSNR_DB = 41.5; // ισορροπία χωρίς να «φαίνεται» με γυμνό μάτι

    // PNG είναι lossless: χρησιμοποιούμε μέγιστη συμπίεση (δεν χαλάει η ποιότητα).
    private static final float PNG_COMPRESSION = 1.0f;

    // --- Δημόσιο API ---

    /**
     * Αυτόματη συμπίεση/αλλαγή μεγέθους με επιλογή format & ποιότητας για ελάχιστο μέγεθος χωρίς αισθητή απώλεια.
     * Υποστηρίζει είσοδο JPG/PNG/WebP/HEIC (με plugins ImageIO).
     */
    public byte[] compressAndResize(MultipartFile file, Purpose purpose) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Φόρτωσε εικόνα (με plugin για HEIC/WEBP αν είναι διαθέσιμος)
        BufferedImage input = ImageIO.read(file.getInputStream());
        if (input == null) {
            throw new IllegalArgumentException("Invalid or unsupported image (install ImageIO plugins for HEIC/WEBP).");
        }

        // Διόρθωση χρωματικού χώρου -> sRGB (για συνέπεια σε browsers)
        input = toSRGB(input);

        // Υπολογισμός στόχου διαστάσεων (χωρίς upscaling)
        int[] dims = targetSize(input.getWidth(), input.getHeight(),
                (purpose == Purpose.USER) ? USER_MAX_WIDTH   : AUCTION_MAX_WIDTH,
                (purpose == Purpose.USER) ? USER_MAX_HEIGHT  : AUCTION_MAX_HEIGHT);
        int targetW = dims[0];
        int targetH = dims[1];

        // Αν έχει άλφα κανάλι;
        boolean hasAlpha = hasAlpha(input);

        // Επιλογή προτεινόμενου output format
        // - USER avatars: PNG (διαφάνεια, ασφαλές). Αν θέλεις μικρότερα, μπορείς να αλλάξεις σε WebP (lossless) άμεσα.
        // - AUCTION: WEBP (αν υπάρχει writer), αλλιώς JPG. Αν η εικόνα έχει άλφα -> WEBP (αν υπάρχει), αλλιώς PNG.
        String fmt = chooseBestOutputFormat(purpose, hasAlpha);

        // Αν δεν υπάρχει writer για WEBP, κάνε graceful fallback.
        if (fmt.equals("webp") && !hasImageWriter("webp")) {
            fmt = hasAlpha ? "png" : "jpg";
        }

        // PNG: απλά lossless re-encode με μέγιστη συμπίεση, αφού πρώτα γίνει resize.
        if ("png".equals(fmt)) {
            return encodeOnce(input, targetW, targetH, "png", PNG_COMPRESSION);
        }

        // Για lossy formats (jpg/webp): autotune ποιότητας με binary search βάσει PSNR
        float minQ = "jpg".equals(fmt) ? JPEG_QUALITY_MIN : WEBP_QUALITY_MIN;
        float maxQ = "jpg".equals(fmt) ? JPEG_QUALITY_MAX : WEBP_QUALITY_MAX;

        return encodeWithAutoQuality(input, targetW, targetH, fmt, minQ, maxQ, TARGET_PSNR_DB);
    }

    // --- Εσωτερικά βοηθητικά ---

    private static String chooseBestOutputFormat(Purpose purpose, boolean hasAlpha) {
        if (purpose == Purpose.USER) {
            // Διατηρούμε PNG για avatars ώστε να μη χαθεί διαφάνεια & να υπάρχει απόλυτη συμβατότητα.
            // (Εναλλακτικά, μπορείς να γυρίσεις σε WEBP lossless για ~μικρότερα αρχεία)
            return hasAlpha ? "png" : "jpg";
        } else {
            // AUCTION: προτίμησε WEBP (πολύ καλή συμπίεση). Με άλφα, WEBP έχει transparency.
            return "webp"; // θα γίνει fallback αν δεν υπάρχει writer
        }
    }

    private static boolean hasAlpha(BufferedImage img) {
        return img.getColorModel().hasAlpha();
    }

    private static BufferedImage toSRGB(BufferedImage input) {
        ColorSpace sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorConvertOp op = new ColorConvertOp(sRGB, null);
        BufferedImage converted = new BufferedImage(
                input.getWidth(),
                input.getHeight(),
                input.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        op.filter(input, converted);
        return converted;
    }

    private static int[] targetSize(int origW, int origH, int maxW, int maxH) {
        double wr = (double) maxW / origW;
        double hr = (double) maxH / origH;
        double ratio = Math.min(1.0, Math.min(wr, hr)); // ποτέ upscaling
        int w = Math.max(1, (int) Math.round(origW * ratio));
        int h = Math.max(1, (int) Math.round(origH * ratio));
        return new int[]{w, h};
    }

    /**
     * Μια μόνο κωδικοποίηση με Thumbnailator (resize + format + ποιότητα).
     * Χρησιμοποιεί useExifOrientation(true) για σωστό προσανατολισμό.
     */
    private static byte[] encodeOnce(BufferedImage input,
                                     int targetW, int targetH,
                                     String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(input)
                .size(targetW, targetH)
                .useExifOrientation(true)
                .outputFormat(format)
                .outputQuality(quality)
                .toOutputStream(baos);
        return baos.toByteArray();
    }

    /**
     * Δοκιμάζει ποιότητες με binary search μέχρι να πετύχει το PSNR κατώφλι
     * (ελάχιστη δυνατή ποιότητα που παραμένει «οπτικά μη αντιληπτή»).
     */
    private static byte[] encodeWithAutoQuality(BufferedImage original,
                                                int targetW, int targetH,
                                                String fmt,
                                                float qMin, float qMax,
                                                double targetPsnrDb) throws IOException {
        // Αρχικά, φτιάξε ένα "golden" resized reference (χωρίς απώλειες) για σύγκριση PSNR.
        byte[] refPng = encodeOnce(original, targetW, targetH, "png", PNG_COMPRESSION);
        BufferedImage reference = ImageIO.read(new ByteArrayInputStream(refPng));
        // Για PSNR, συγκρίνουμε RGB (αγνοούμε άλφα).
        reference = toOpaqueRGB(reference);

        // Binary search 6-7 βήματα επαρκούν
        float lo = qMin, hi = qMax;
        byte[] best = null;
        for (int i = 0; i < 7; i++) {
            float mid = (lo + hi) / 2f;
            byte[] cand = encodeOnce(original, targetW, targetH, fmt, mid);
            BufferedImage recon = ImageIO.read(new ByteArrayInputStream(cand));
            if (recon == null) {
                // αν ο writer αποτύχει (π.χ. δεν υπάρχει plugin), κάνε graceful fallback σε JPG
                if ("webp".equals(fmt)) {
                    return encodeWithAutoQuality(original, targetW, targetH, "jpg", JPEG_QUALITY_MIN, JPEG_QUALITY_MAX, targetPsnrDb);
                }
                throw new IOException("Failed to write image for format: " + fmt);
            }
            recon = toOpaqueRGB(recon);

            double psnr = psnr(reference, recon);
            if (Double.isInfinite(psnr) || psnr >= targetPsnrDb) {
                // Καλή ποιότητα — προσπάθησε ακόμα χαμηλότερη (για μικρότερο μέγεθος)
                best = cand;
                hi = mid;
            } else {
                // Υπερβολική απώλεια — ανέβασε ποιότητα
                lo = mid;
            }
        }
        // Ασφάλεια: αν για κάποιο λόγο δεν ορίστηκε best (π.χ. πάντα κάτω από κατώφλι), πάρε στο high τέλος
        if (best == null) {
            best = encodeOnce(original, targetW, targetH, fmt, Math.max(lo, (qMin + qMax) / 2f));
        }
        return best;
    }

    private static BufferedImage toOpaqueRGB(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) return img;
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(img, 0, 0, null); // απλό blend σε μαύρο υπόβαθρο (δεν πειράζει γιατί σε lossy δεν κρατάμε άλφα)
        return rgb;
    }

    /**
     * Υπολογισμός PSNR σε RGB (8-bit ανά κανάλι).
     */
    private static double psnr(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        long sumSq = 0L;
        long n = (long) w * h * 3L;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                int ra = (ca >> 16) & 0xFF, ga = (ca >> 8) & 0xFF, ba = ca & 0xFF;
                int rb = (cb >> 16) & 0xFF, gb = (cb >> 8) & 0xFF, bb = cb & 0xFF;
                int dr = ra - rb, dg = ga - gb, db = ba - bb;
                sumSq += (long) dr * dr + (long) dg * dg + (long) db * db;
            }
        }
        if (sumSq == 0) return Double.POSITIVE_INFINITY;
        double mse = (double) sumSq / (double) n;
        double maxI = 255.0;
        return 10.0 * Math.log10((maxI * maxI) / mse);
    }

    private static boolean hasImageWriter(String format) {
        return ImageIO.getImageWritersByFormatName(format).hasNext();
    }
}
