package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Dtos.ChatMessageRequest;
import com.example.webapp.BidNow.Dtos.ChatMessageResponse;
import com.example.webapp.BidNow.Services.AuctionChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for auction chat endpoints.
 *
 * Base path: /api/auctions
 * Provides: fetch chat messages and send a new message for a specific auction.
 */
@RestController
@RequestMapping("/api/auctions/chat")
public class AuctionChatController {

    private final AuctionChatService auctionChatService;



    public AuctionChatController(AuctionChatService auctionChatService) {
        this.auctionChatService = auctionChatService;
    }

    /**
     * Get all chat messages for a specific auction.
     *
     * GET /api/auctions/{auctionId}/chat
     *
     * @param auctionId auction id
     * @return list of chat messages for the auction
     */
    @GetMapping("/{auctionId}/getChat")
    public ResponseEntity<List<ChatMessageResponse>> getChat(
            @PathVariable Long auctionId) {

        List<ChatMessageResponse> messages = auctionChatService.getMessages(auctionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * Send a new chat message for a specific auction.
     *
     * This Api does some checks (user is eligible to bid in
     * this auction, user overcome the messaging limit count etc.)
     *
     * And then broadcasts user's message across all active users.
     *
     *
     * POST /api/auctions/{auctionId}/chat
     *
     * @param auctionId auction id
     * @param request   message payload (e.g. content, receiver info, etc.)
     * @return the created message
     */    @PostMapping("/{auctionId}/sendMessage")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long auctionId,
            @RequestBody ChatMessageRequest request) {

        ChatMessageResponse message = auctionChatService.sendMessage(auctionId, request);
        return ResponseEntity.ok(message);
    }

}
