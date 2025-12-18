package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Bid;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BidRepository extends JpaRepository<Bid,Long> {



    @Query("""
        SELECT DISTINCT a
        FROM Auction a
        JOIN a.bids b
        JOIN b.bidder u
        WHERE u.firebaseId = :userFirebaseId
    """)
    Set<Auction> getAuctionsByFirebaseId(String userFirebaseId);

    List<Bid> findByBidderId(Long id);

    @Query("SELECT DISTINCT b.bidder " +
            "FROM Bid b " +
            "WHERE b.auction.id = :auctionId AND b.isEnabled = true")
    List<UserEntity> findDistinctBiddersByAuctionId(@Param("auctionId") Long auctionId);

    boolean existsByAuctionIdAndBidderId(Long auctionId, Long bidderId);



    Optional<Bid> findTopByAuction_IdAndIsEnabledTrueOrderByAmountDescCreatedAtDesc(Long auctionId);
}
