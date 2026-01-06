package com.example.webapp.BidNow.Schedulers;
import com.example.webapp.BidNow.Dtos.EmailEvent;
import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Bid;
import com.example.webapp.BidNow.Entities.ReferralCodeUsage;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Repositories.ReferralCodeUsageRepository;
import com.example.webapp.BidNow.Services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

@Component
public class AuctionWinnerExpiredScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionWinnerExpiredScheduler.class);

    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final AuctionRepository auctionRepository;

    public AuctionWinnerExpiredScheduler(BidRepository bidRepository, ApplicationEventPublisher eventPublisher, AuctionRepository auctionRepository) {
        this.bidRepository = bidRepository;
        this.eventPublisher = eventPublisher;
        this.auctionRepository = auctionRepository;
    }


    /**
     * Runs repeatedly (every 80 seconds).
     * Finds ACTIVE auctions that already ended and marks them as EXPIRED.
     *
     * Then:
     * - If there are no bids: notify the owner that the auction ended with no bids.
     * - If there is a winner: set winner, enable chat for winner/owner,
     *   send notifications (won/lost/owner) and send emails (winner + owner).
     *
     * Uses @Transactional because we update auction fields (status, winner, flags).
     */
    @Scheduled(fixedDelay = 80_000)
    @Transactional
    public void markExpiredAuctionsAndNotifyWinners() {

        LocalDateTime now = LocalDateTime.now();

        // Find auctions that expired
        List<Auction> toExpire =
                auctionRepository.findByStatusAndEndDateBefore(AuctionStatus.ACTIVE, now);

        if (toExpire.isEmpty()) return;

        log.info("Found {} auctions to expire", toExpire.size());

        for (Auction auction : toExpire) {

            log.info("Expiring auction id={} title='{}'", auction.getId(), auction.getTitle());

            // 1) Set auction EXPIRED
            auction.setStatus(AuctionStatus.EXPIRED);

            // 2) Find Winner = max enabled bid
            Bid winningBid = null;
            if (auction.getBids() != null && !auction.getBids().isEmpty()) {
                winningBid = auction.getBids().stream()
                        .filter(Bid::isEnabled)
                        .max(Comparator.comparing(Bid::getAmount))
                        .orElse(null);
            }

            // If the auction has no bids
            if (winningBid == null) {
                log.info("Auction id={} expired with no bids", auction.getId());

                Long ownerId = auction.getOwner().getId();
                String metadata = "{\"auctionId\":" + auction.getId() + ",\"result\":\"NO_BIDS\"}";

                eventPublisher.publishEvent(new NotificationEvent(
                        ownerId,
                        NotificationType.AUCTION_ENDED_FOR_OWNER,
                        "Your auction ended",
                        "Your auction \"" + auction.getTitle() + "\" ended with no bids.",
                        metadata
                ));

                continue;
            }

            UserEntity winner = winningBid.getBidder();
            BigDecimal winningAmount = winningBid.getAmount();

            auction.setWinner(winner);
            winner.setEligibleForChat(true);
            auction.getOwner().setEligibleForChat(true);

            // NOTIFICATION: AUCTION_WON
            String wonMetadata = "{\"auctionId\":" + auction.getId()
                    + ",\"winningAmount\":\"" + winningAmount.toPlainString() + "\"}";

            // After transaction commit send notification asynchronously to the winner
            eventPublisher.publishEvent(new NotificationEvent(
                    winner.getId(),
                    NotificationType.AUCTION_WON,
                    "You won an auction",
                    "You won \"" + auction.getTitle() + "\" with the amount of €" + winningAmount.toPlainString(),
                    wonMetadata
            ));
            Long ownerId = auction.getOwner().getId();

            String ownerMetadata = "{\"auctionId\":" + auction.getId()
                    + ",\"winnerId\":" + winner.getId()
                    + ",\"winnerUsername\":\"" + winner.getUsername() + "\""
                    + ",\"winningAmount\":\"" + winningAmount.toPlainString() + "\"}";

            // After transaction commit send notification asynchronously to the owner
            eventPublisher.publishEvent(new NotificationEvent(
                    ownerId,
                    NotificationType.AUCTION_ENDED_FOR_OWNER,
                    "Your auction ended",
                    "Your auction \"" + auction.getTitle() + "\" ended. Winner: "
                            + winner.getUsername() + " with " + winningAmount.toPlainString(),
                    ownerMetadata
            ));

            // NOTIFICATION: AUCTION_LOST (σε όλους τους άλλους bidders)
            List<UserEntity> bidders =
                    bidRepository.findDistinctBiddersByAuctionId(auction.getId());

            for (UserEntity bidder : bidders) {
                if (bidder.getId().equals(winner.getId())) continue;

                String lostMetadata = "{\"auctionId\":" + auction.getId()
                        + ",\"winningAmount\":\"" + winningAmount.toPlainString() + "\""
                        + ",\"winnerUsername\":\"" + winner.getUsername() + "\"}";

                eventPublisher.publishEvent(new NotificationEvent(
                        bidder.getId(),
                        NotificationType.AUCTION_LOST,
                        "Auction ended",
                        "You lost \"" + auction.getTitle() + "\". Winning bid: " + winningAmount.toPlainString(),
                        lostMetadata
                ));
            }

            // EMAIL to winner.
            String winnerEmail = winner.getEmail();
            if (winnerEmail != null && !winnerEmail.isBlank()) {
                String subject = "You won the auction: " + auction.getTitle();

                String body = """
                        Congratulations! 🎉
                        
                        You have won the auction:
                        
                        Title: %s
                        Winning bid: %s
                        
                        Please log in to BidNow to see more details and complete the process.
                        """.formatted(auction.getTitle(), winningAmount.toPlainString());

                eventPublisher.publishEvent(new EmailEvent(winnerEmail, subject, body));
            }

            // EMAIL to owner
            String ownerEmail = auction.getOwner().getEmail();
            if (ownerEmail != null && !ownerEmail.isBlank()) {
                String subjectOwner = "Your auction " + auction.getTitle()
                        + " has a winner: " + winner.getUsername();

                String bodyOwner = """
                        Your auction has ended successfully!
                        
                        Auction title: %s
                        Winner: %s
                        Winning bid: %s
                        
                        Please log in to BidNow to proceed with the next steps.
                        """.formatted(auction.getTitle(), winner.getUsername(), winningAmount.toPlainString());

                eventPublisher.publishEvent(new EmailEvent(ownerEmail, subjectOwner, bodyOwner));
            }
        }
    }
}
