package com.example.webapp.BidNow.Dtos;

import java.time.LocalDateTime;

/**
 * Response chat message dto
 * for sending auctions messages
 * Apis:
 *  - getChat
 *  - sendMessage
 *  in AuctionChatController
 *
 *  And as a list of messages in AuctionResponseDto
 */
public class ChatMessageResponse {

    private Long id;
    private String senderDisplayName;
    private String senderFirebaseId;// todo: probably remove this in the future
    private String content;
    private LocalDateTime createdAt;

    private Integer remainingMessages;

    public ChatMessageResponse(){}


    public ChatMessageResponse(Long id, String senderDisplayName, String senderFirebaseId, String content, LocalDateTime createdAt,Integer remainingMessages) {
        this.id = id;
        this.senderDisplayName = senderDisplayName;
        this.senderFirebaseId = senderFirebaseId;
        this.content = content;
        this.createdAt = createdAt;
        this.remainingMessages = remainingMessages;
    }


    // getters/setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSenderDisplayName() {
        return senderDisplayName;
    }

    public void setSenderDisplayName(String senderDisplayName) {
        this.senderDisplayName = senderDisplayName;
    }

    public String getSenderFirebaseId() {
        return senderFirebaseId;
    }

    public void setSenderFirebaseId(String senderFirebaseId) {
        this.senderFirebaseId = senderFirebaseId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getRemainingMessages() {
        return remainingMessages;
    }

    public void setRemainingMessages(Integer remainingMessages) {
        this.remainingMessages = remainingMessages;
    }
}
