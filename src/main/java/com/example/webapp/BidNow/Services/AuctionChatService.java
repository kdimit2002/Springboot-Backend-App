package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.ChatMessageRequest;
import com.example.webapp.BidNow.Dtos.ChatMessageResponse;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.AuctionMessage;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.AuctionMessageRepository;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

@Service
public class AuctionChatService {

    private final AuctionRepository auctionRepository;
    private final AuctionMessageRepository auctionMessageRepository;
    private final UserEntityRepository userEntityRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserActivityService userActivityService;
    private final BidRepository bidRepository;


    public AuctionChatService(AuctionRepository auctionRepository,
                              AuctionMessageRepository auctionMessageRepository,
                              UserEntityRepository userEntityRepository,
                              SimpMessagingTemplate messagingTemplate, UserActivityService userActivityService,
                              BidRepository bidRepository) {
        this.auctionRepository = auctionRepository;
        this.auctionMessageRepository = auctionMessageRepository;
        this.userEntityRepository = userEntityRepository;
        this.messagingTemplate = messagingTemplate;
        this.userActivityService = userActivityService;
        this.bidRepository = bidRepository;
    }

//    @Transactional(readOnly = true)
//    public List<ChatMessageResponse> getMessages(Long auctionId) {
//        List<AuctionMessage> messages =
//                auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auctionId);
//
//        return messages.stream()
//                .map(this::toResponse)
//                .toList();
//    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long auctionId) {
        List<AuctionMessage> messages =
                auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auctionId);

        final int MAX_MESSAGES = 25;

        // count ανά sender
        java.util.Map<Long, Integer> perUserCount = new java.util.HashMap<>();
        java.util.List<ChatMessageResponse> result = new java.util.ArrayList<>();

        for (AuctionMessage m : messages) {
            Long senderId = m.getSender().getId();
            int soFar = perUserCount.getOrDefault(senderId, 0) + 1;
            perUserCount.put(senderId, soFar);

            int remaining = MAX_MESSAGES - soFar;
            if (remaining < 0) remaining = 0;

            ChatMessageResponse dto = toResponse(m);
            dto.setRemainingMessages(remaining);

            result.add(dto);
        }

        return result;
    }


    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public ChatMessageResponse sendMessage(Long auctionId,
                                           ChatMessageRequest request) {

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Content is empty"
            );
        }

        UserEntity sender = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Auction not found"
                ));

        if (auction.getEndDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This auction is expired"
            );
        }

        Long userId = sender.getId();

        boolean hasBidOnThisAuction =
                bidRepository.existsByAuctionIdAndBidderId(auctionId, userId);
        boolean hasCreatedAnyAuction =
                auctionRepository.existsByOwner_Id(userId);
        boolean hasWonAtLeastOneAuction =
                auctionRepository.existsByWinner_Id(userId);

        boolean canChat =
                hasBidOnThisAuction || hasCreatedAnyAuction || hasWonAtLeastOneAuction;

        if (!canChat) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to chat in this auction"
            );
        }

        final int MAX_MESSAGES = 25;

        // 🔹 Πόσα μηνύματα έχει ήδη στείλει ο χρήστης σε αυτό το auction;
        long alreadySent = auctionMessageRepository
                .countByAuctionIdAndSenderId(auctionId, userId);

        // 🔹 Rate limit 25 μηνύματα ανά χρήστη ανά auction
        if (alreadySent >= MAX_MESSAGES) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the limit of 25 messages in this auction chat"
            );
        }

        // 🔹 Δημιουργία & αποθήκευση μηνύματος
        AuctionMessage msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setSender(sender);
        msg.setContent(request.getContent().trim());

        AuctionMessage saved = auctionMessageRepository.save(msg);
        userActivityService.saveUserActivityAsync(
                Endpoint.SEND_MESSAGE,
                "User: " + getUserFirebaseId() + " sent message to auction: " + auctionId
        );

        // πόσα απομένουν ΜΕΤΑ από αυτό το μήνυμα
        int remaining = MAX_MESSAGES - (int) (alreadySent + 1);
        if (remaining < 0) remaining = 0;

        ChatMessageResponse dto = toResponse(saved);
        dto.setRemainingMessages(remaining);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/topic/auctions/" + auctionId + "/chat",
                                dto
                        );
                    }
                }
        );

        return dto;
    }



    /**
     * Στέλνει system μήνυμα στο chat μιας auction ότι ένας χρήστης απενεργοποιήθηκε
     */
    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public ChatMessageResponse sendUserDisabledSystemMessage(Long auctionId, Long disabledUserId) {

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found"));

        UserEntity disabledUser = userEntityRepository.findById(disabledUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // TODO: Προτείνεται να έχεις ειδικό system χρήστη, π.χ. "SYSTEM"
        UserEntity systemUser = userEntityRepository.findByEmail("system@bidnow.app").orElseThrow(()-> new ResourceNotFoundException("System user was not found"));

        if (systemUser == null) {
            throw new RuntimeException("System user not configured (system@bidnow.app)");
        }

        String content = "User \"" + disabledUser.getUsername()
                + "\" was disabled by an administrator. Their bids in this auction are no longer active.";

        AuctionMessage msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setSender(systemUser);
        msg.setContent(content);

        AuctionMessage saved = auctionMessageRepository.save(msg);
        ChatMessageResponse dto = toResponse(saved);

        // WebSocket broadcast στο chat της δημοπρασίας
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/topic/auctions/" + auctionId + "/chat",
                                dto
                        );
                    }
                }
        );

        return dto;
    }

    private ChatMessageResponse toResponse(AuctionMessage m) {
        ChatMessageResponse dto = new ChatMessageResponse();
        dto.setId(m.getId());
        dto.setContent(m.getContent());
        dto.setCreatedAt(m.getCreatedAt());

        // προσαρμόζεις ανάλογα με UserEntity
        dto.setSenderFirebaseId(m.getSender().getFirebaseId());
        dto.setSenderDisplayName(m.getSender().getUsername()); // ή fullName, ή ό,τι έχεις

        return dto;
    }

}
