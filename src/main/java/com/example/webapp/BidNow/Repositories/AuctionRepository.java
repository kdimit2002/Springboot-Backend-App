package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    long countByOwnerAndCreatedAtAfter(UserEntity owner, LocalDateTime createdAfter);

    Page<Auction> findByStatusAndStartDateBeforeAndEndDateAfter(
            AuctionStatus status,
            LocalDateTime now1,
            LocalDateTime now2,
            Pageable pageable
    );

    Page<Auction> findByStatusAndStartDateBeforeAndEndDateAfterAndCategoryId(
            AuctionStatus status,
            LocalDateTime now1,
            LocalDateTime now2,
            Long categoryId,
            Pageable pageable
    );

    Page<Auction> findByStatusAndEndDateBetween(
            AuctionStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    Page<Auction> findByStatusAndEndDateBetweenAndCategoryId(
            AuctionStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Long categoryId,
            Pageable pageable
    );

    List<Auction> findByStatus(AuctionStatus status);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    List<Auction> findByStatusAndEndDateBefore(AuctionStatus status, LocalDateTime now);


    @Query("""
           SELECT a FROM Auction a
           WHERE a.status = :status
             AND a.startDate <= :now
             AND a.endDate >= :now
             AND (
                    LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR a.description LIKE CONCAT('%', :keyword, '%')
             )
           """)
    Page<Auction> searchActiveAuctions(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // Search with category
    @Query("""
           SELECT a FROM Auction a
           WHERE a.status = :status
             AND a.startDate <= :now
             AND a.endDate >= :now
             AND a.category.id = :categoryId
             AND (
                    LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR a.description LIKE CONCAT('%', :keyword, '%')
             )
           """)
    Page<Auction> searchActiveAuctionsByCategory(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    Optional<Auction> findByIdAndOwnerFirebaseId(Long auctionId, String userFirebaseId);

    List<Auction> findByOwnerId(Long id);

    List<Auction> findDistinctByBids_Bidder_Id(Long id);


    List<Auction> findByStatusAndEndDateBetween(
            AuctionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );




    /**
     * ACTIVE auctions that user has bid
     */
    @Query("""
           SELECT DISTINCT a FROM Auction a
           JOIN a.bids b
           WHERE a.status = :status
             AND a.startDate <= :now
             AND a.endDate >= :now
             AND b.bidder = :bidder
           """)
    Page<Auction> findActiveAuctionsUserHasBid(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now,
            @Param("bidder") UserEntity bidder,
            Pageable pageable
    );

    /**
     * User's won auctions.
     */
    @Query("""
           SELECT DISTINCT a FROM Auction a
           JOIN a.bids b
           WHERE a.status = :status
             AND b.bidder = :bidder
             AND b.amount = (
                SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = a
             )
           """)
    Page<Auction> findWonAuctionsByUser(
            @Param("status") AuctionStatus status,
            @Param("bidder") UserEntity bidder,
            Pageable pageable
    );



    Page<Auction> findByStatusAndOwner(
            AuctionStatus status,
            UserEntity owner,
            Pageable pageable
    );

    // Expired auctions that there was no winner
    List<Auction> findByStatusAndEndDateBeforeAndWinnerIsNull(
            AuctionStatus status,
            LocalDateTime now
    );

    Page<Auction> findByWinnerAndStatus(
            UserEntity winner,
            AuctionStatus status,
            Pageable pageable
    );


    boolean existsByWinner_Id(Long userId);

    boolean existsByOwner_Id(Long userId);



    Page<Auction> findByOwnerAndStatusAndStartDateBeforeAndEndDateAfter(
            UserEntity owner,
            AuctionStatus status,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Page<Auction> findByOwnerAndStatusAndEndDateBetween(
            UserEntity owner,
            AuctionStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    Page<Auction> findByOwnerAndStatusAndEndDateBefore(
            UserEntity owner,
            AuctionStatus status,
            LocalDateTime before,
            Pageable pageable
    );

    Page<Auction> findByOwnerAndStatus(
            UserEntity owner,
            AuctionStatus status,
            Pageable pageable
    );




    List<Auction> findByStatusAndEndDateBetweenAndEndingSoonNotifiedFalse(
            AuctionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

}


