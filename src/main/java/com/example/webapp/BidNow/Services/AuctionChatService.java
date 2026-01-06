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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * Service for chatting between user's for reach auction
 *
 */
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


    //todo: use caching for large auction messages or pagination
    /**
     * Getting auctions' messages
     *
     * @param auctionId , the auction that the messages belong to
     * @return List of messages and details about them
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long auctionId) {
        List<AuctionMessage> messages =
                auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auctionId);

        final int MAX_MESSAGES = 25;

        // counter for each sender's remaining messages //todo: make it only for the corresponding sender
        Map<Long, Integer> perUserCount = new HashMap<>();
        // List of messages and their metadata
        List<ChatMessageResponse> result = new ArrayList<>();

        for (AuctionMessage m : messages) {
            Long senderId = m.getSender().getId();
            int soFar = perUserCount.getOrDefault(senderId, 0) + 1;
            perUserCount.put(senderId, soFar);

            int remaining = MAX_MESSAGES - soFar;// calculate remaining messages for each user
            if (remaining < 0) remaining = 0;

            ChatMessageResponse dto = toResponse(m);
            dto.setRemainingMessages(remaining);

            result.add(dto);
        }

        return result;
    }


    /**
     * Storing a message and broadcasting it to all active users
     * via websockets.
     *
     * Notes:
     *  - User can send a message to the auction if he meets the requirements
     *  (has bid to the auction, won an auction in the past, placed an auction in the past)
     *  - Remove auction from cache if someone send message ( Cache must have auction's newest version)
     * @param auctionId
     * @param request
     * @return
     */
    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public ChatMessageResponse sendMessage(Long auctionId,
                                           ChatMessageRequest request) {

        if (request.getContent() == null || request.getContent().isBlank()) {
            // message cannot be empty
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

        // cannot send a message to an expired auction
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

        // true if user meets the requirements
        boolean canChat =
                hasBidOnThisAuction || hasCreatedAnyAuction || hasWonAtLeastOneAuction;

        if (!canChat) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to chat in this auction"
            );
        }

        final int MAX_MESSAGES = 25;

        // Number of messages that user has sent to this auction
        long alreadySent = auctionMessageRepository
                .countByAuctionIdAndSenderId(auctionId, userId);

        // Rate limit. Each user can send up to 25 messages for each auction
        if (alreadySent >= MAX_MESSAGES) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the limit of 25 messages in this auction chat"
            );
        }

        // Create and save message in DB
        AuctionMessage msg = new AuctionMessage();
        msg.setAuction(auction);
        msg.setSender(sender);
        msg.setContent(request.getContent().trim());

        AuctionMessage saved = auctionMessageRepository.save(msg);
        userActivityService.saveUserActivityAsync(
                Endpoint.SEND_MESSAGE,
                "User: " + getUserFirebaseId() + " sent message to auction: " + auctionId
        );

        // Calculate user's remaining messages for this auction
        int remaining = MAX_MESSAGES - (int) (alreadySent + 1);
        if (remaining < 0) remaining = 0;

        ChatMessageResponse dto = toResponse(saved);
        dto.setRemainingMessages(remaining);

        // Broadcast message across all active users
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
     * todo: maybe remove this
     */
    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public ChatMessageResponse sendUserDisabledSystemMessage(Long auctionId, Long disabledUserId) {

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found"));

        UserEntity disabledUser = userEntityRepository.findById(disabledUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));


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

        // WebSocket broadcast
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

    // Map AuctionMessage to dto
    private ChatMessageResponse toResponse(AuctionMessage m) {
        ChatMessageResponse dto = new ChatMessageResponse();
        dto.setId(m.getId());
        dto.setContent(m.getContent());
        dto.setCreatedAt(m.getCreatedAt());

        dto.setSenderFirebaseId(m.getSender().getFirebaseId());
        dto.setSenderDisplayName(m.getSender().getUsername());

        return dto;
    }

}
