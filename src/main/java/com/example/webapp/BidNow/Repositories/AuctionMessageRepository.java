package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Dtos.ChatMessageResponse;
import com.example.webapp.BidNow.Entities.AuctionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuctionMessageRepository extends JpaRepository<AuctionMessage, Long> {

    List<AuctionMessage> findByAuctionIdOrderByCreatedAtAsc(Long auctionId);

    long countByAuctionIdAndSenderId(Long auctionId, Long id);

    List<AuctionMessage> findByAuctionId(Long id);
}
