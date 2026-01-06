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
     * Runs every 5 minutes.
     * Finds ACTIVE auctions that end in 5–10 minutes and sends a reminder to all bidders.
     * After sending, it marks the auction as "endingSoonNotified" so we don't send again.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional // ΟΧΙ readOnly, γιατί κάνουμε update στο auction flag
    public void sendEndingSoonReminders() {

        LocalDateTime now = LocalDateTime.now();

        // 5 to 10 minutes window to send notification
        LocalDateTime from = now.plusMinutes(5);
        LocalDateTime to = now.plusMinutes(10);


        // ACTIVE auctions that are in the window and not users weren't notified yet.
        List<Auction> endingSoonAuctions =
                auctionRepository.findByStatusAndEndDateBetweenAndEndingSoonNotifiedFalse(
                        AuctionStatus.ACTIVE,
                        from,
                        to
                );

        for (Auction auction : endingSoonAuctions) {

            long minutesRemaining = Duration.between(now, auction.getEndDate()).toMinutes();
            if (minutesRemaining < 0) {
                continue;
            }

            // Find all auction bidders
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

                eventPublisher.publishEvent(new NotificationEvent( //
                        bidder.getId(),
                        NotificationType.AUCTION_ENDING_SOON,
                        "Auction ending soon",
                        "Auction \"" + auction.getTitle() + "\" ends in " + minutesRemaining + " minutes.",
                        metadata
                ));
            }

            // Mark that bidders were notified
            auction.setEndingSoonNotified(true);

        }
    }
}
