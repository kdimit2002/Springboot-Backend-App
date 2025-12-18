package com.example.webapp.BidNow.Schedulers;

import com.example.webapp.BidNow.Dtos.EmailEvent;
import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Services.EmailService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AuctionReminderScheduler {

    private final ApplicationEventPublisher eventPublisher;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final EmailService emailService;

    public AuctionReminderScheduler(ApplicationEventPublisher eventPublisher, AuctionRepository auctionRepository,
                                    BidRepository bidRepository,
                                    EmailService emailService) {
        this.eventPublisher = eventPublisher;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.emailService = emailService;
    }

    /**
     * Τρέχει κάθε 5 λεπτά.
     * Στέλνει email για auctions που είναι περίπου 10 λεπτά πριν λήξουν
     * σε όλους τους bidders, ΜΟΝΟ μία φορά ανά auction.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional // ΟΧΙ readOnly, γιατί κάνουμε update στο auction flag
    public void sendEndingSoonReminders() {

        LocalDateTime now = LocalDateTime.now();

        // Παράθυρο: από 5 έως 10 λεπτά από τώρα
        LocalDateTime from = now.plusMinutes(5);
        LocalDateTime to = now.plusMinutes(10);


        // ACTIVE auctions που λήγουν στο παράθυρο και ΔΕΝ έχουν ειδοποιηθεί ακόμη
        List<Auction> endingSoonAuctions =
                auctionRepository.findByStatusAndEndDateBetweenAndEndingSoonNotifiedFalse(
                        AuctionStatus.ACTIVE,
                        from,
                        to
                );

        for (Auction auction : endingSoonAuctions) {

            long minutesRemaining = Duration.between(now, auction.getEndDate()).toMinutes();
            if (minutesRemaining < 0) {
                // Just in case, αν κάτι πήγε στραβά με την ώρα
                continue;
            }

            // Βρες όλους τους bidders αυτής της δημοπρασίας
            List<UserEntity> bidders =
                    bidRepository.findDistinctBiddersByAuctionId(auction.getId());

            for (UserEntity bidder : bidders) {

                String toEmail = bidder.getEmail();
                if (toEmail == null || toEmail.isBlank()) {
                    continue; // προαιρετικά skip αν δεν έχει email
                }

                String subject = "Auction \"" + auction.getTitle()
                        + "\" ends in " + minutesRemaining + " minutes";

                String body = """
                        Hello %s,
                        
                        This is a reminder that the auction "%s" is about to end.
                        
                        Remaining time: %d minutes
                        
                        Description:
                        %s
                        
                        If you want to increase your bid, please visit the auction page before it ends.
                        
                        Best regards,
                        BidNow Team
                        """.formatted(
                        bidder.getUsername(),
                        auction.getTitle(),
                        minutesRemaining,
                        auction.getDescription() != null ? auction.getDescription() : "-"
                );

                eventPublisher.publishEvent(new EmailEvent(toEmail, subject, body));

                String metadata = "{\"auctionId\":" + auction.getId() + ",\"reminder\":\"ENDING_SOON_10MIN\"}";

                eventPublisher.publishEvent(new NotificationEvent(
                        bidder.getId(),
                        NotificationType.AUCTION_ENDING_SOON,
                        "Auction ending soon",
                        "Auction \"" + auction.getTitle() + "\" ends in " + minutesRemaining + " minutes.",
                        metadata
                ));
            }

            // Μαρκάρουμε ότι στείλαμε “ending soon” reminder
            auction.setEndingSoonNotified(true);
            // Δεν χρειάζεται explicit save αν το Auction είναι managed από JPA,
            // αλλά μπορείς να βάλεις και auctionRepository.save(auction) αν θες πιο ξεκάθαρα.


        }
    }
}
