package com.example.webapp.BidNow.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * Request Message Dto
 * for broadcasting message across active users.
 * Api: sendMessage api in controller AuctionChatController
 *
 */
public class ChatMessageRequest {
    @Size(min = 0,max =500 )
    @NotBlank(message = "You cannot send blank or empty message")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
