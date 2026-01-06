package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.*;
import com.example.webapp.BidNow.Entities.*;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Enums.Region;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Exceptions.TooManyRequestsException;
import com.example.webapp.BidNow.Repositories.*;
import org.apache.commons.text.similarity.FuzzyScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.webapp.BidNow.Dtos.AuctionAdminUpdateRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * Service for managing auctions
 *
 */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private final UserActivityService userActivityService;
    private final AuctionMessageRepository auctionMessageRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final NotificationRepository notificationRepository;
    private final CategoryRepository categoryRepository;
    private final UserEntityRepository userEntityRepository;
    private final LocationRepository locationRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    // Minimum fuzzy score for accepting fuzzy matches (higher => stricter matching).
    private static final int FUZZY_MIN_SCORE = 9;


    // Fuzzy search locale todo: (currently EN; change to Locale.forLanguageTag("el") if needed).
    private final FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);

    public AuctionService(UserActivityService userActivityService, AuctionMessageRepository auctionMessageRepository, AuctionRepository auctionRepository, NotificationRepository notificationRepository,
                          CategoryRepository categoryRepository,
                          UserEntityRepository userEntityRepository,
                          LocationRepository locationRepository,
                          EmailService emailService,
                          BidRepository bidRepository, ApplicationEventPublisher eventPublisher) {
        this.userActivityService = userActivityService;
        this.auctionMessageRepository = auctionMessageRepository;
        this.auctionRepository = auctionRepository;
        this.notificationRepository = notificationRepository;
        this.categoryRepository = categoryRepository;
        this.userEntityRepository = userEntityRepository;
        this.locationRepository = locationRepository;
        this.emailService = emailService;
        this.bidRepository= bidRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Create auction
     *
     * @param request
     * @return
     */
    @CachePut(cacheNames = "auctionById", key = "#result.id")
    @Transactional
    public AuctionResponseDto createAuction(AuctionCreateRequest request) {

        // Current authenticated user
        UserEntity owner = userEntityRepository.findByFirebaseId(getUserFirebaseId()).orElseThrow(()->  new ResourceNotFoundException("User not found"));

        // Mark user as eligible for chat (users that created auction can now chat in all auctions chats) .
        owner.setEligibleForChat(true);

        // Anti-spam: allow at most 5 auctions per 10 minutes per owner.
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        long recentAuctions = auctionRepository.countByOwnerAndCreatedAtAfter(owner, cutoff);
        if (recentAuctions >= 5) {
            //todo: send email
            throw new TooManyRequestsException(
                    "You have created too many auctions recently. Please try again later."
            );
        }

        // Validate category existence.
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Build auction entity from request.
        Auction auction = new Auction();
        auction.setCategory(category);
        auction.setOwner(owner);
        auction.setTitle(request.getTitle());
        auction.setShortDescription(request.getShortDescription()); // 👈
        auction.setDescription(request.getDescription());
        auction.setMinBidIncrement(request.getMinBidIncrement());
        auction.setStartingAmount(request.getStartingAmount());
        auction.setStartDate(request.getStartDate());
        auction.setEndDate(request.getEndDate());
        auction.setShippingCostPayer(request.getShippingCostPayer());


        // New auctions must be approved by admin first.
        auction.setStatus(AuctionStatus.PENDING_APPROVAL);

        // logging
        Auction saved = auctionRepository.save(auction);
        userActivityService.saveUserActivityAsync(Endpoint.CREATE_AUCTION,"User: " + getUserFirebaseId() + ", AuctionId: " + auction.getId());


        // Send admin email ONLY after the transaction commits successfully.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String adminEmail = "bidnowapp@gmail.com";

                String subject = "New auction pending approval";
                String body = """
                        A new auction was created and is waiting for your approval.
                        
                        Auction ID: %d
                        Title: %s
                        Seller: %s
                        Category: %s
                        
                        Please log in to the admin panel to review and approve it.
                        """.formatted(
                        saved.getId(),
                        saved.getTitle(),
                        saved.getOwner().getUsername(),
                        saved.getCategory() != null ? saved.getCategory().getName() : "None"
                );

                emailService.sendSimpleEmailAsync(adminEmail, subject, body);
            }
        });

        return toDetailsDto(saved);
    }

    /**
     * Get active auctions
     *
     * Uses pagination, filters and search
     * @param sortBy
     * @param direction
     * @param categoryId
     * @param page
     * @param size
     * @param keyword
     * @param region
     * @param country
     * @return
     */
    @Transactional
    public Page<AuctionListItemDto> getActiveAuctions(
            String sortBy,
            String direction,
            Long categoryId,
            int page,
            int size,
            String keyword,
            String region,
            String country
    ) {
//        refreshExpiredAuctions();

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate" -> Sort.by("endDate");
            default -> Sort.by("endDate");
        };
        if ("desc".equalsIgnoreCase(direction)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        LocalDateTime now = LocalDateTime.now();

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Region regionFilter = parseRegion(region);
        boolean hasLocationFilter =
                regionFilter != null || (country != null && !country.isBlank());


        if (!hasKeyword) {

            if (!hasLocationFilter) {
                Page<Auction> auctionsPage;
                if (categoryId != null) {
                    auctionsPage = auctionRepository
                            .findByStatusAndStartDateBeforeAndEndDateAfterAndCategoryId(
                                    AuctionStatus.ACTIVE, now, now, categoryId, pageable
                            );
                } else {
                    auctionsPage = auctionRepository
                            .findByStatusAndStartDateBeforeAndEndDateAfter(
                                    AuctionStatus.ACTIVE, now, now, pageable
                            );
                }
                return auctionsPage.map(this::toListDto);
            }

            List<Auction> base;
            if (categoryId != null) {
                base = auctionRepository
                        .findByStatusAndStartDateBeforeAndEndDateAfterAndCategoryId(
                                AuctionStatus.ACTIVE, now, now, categoryId, Pageable.unpaged()
                        )
                        .getContent();
            } else {
                base = auctionRepository
                        .findByStatusAndStartDateBeforeAndEndDateAfter(
                                AuctionStatus.ACTIVE, now, now, Pageable.unpaged()
                        )
                        .getContent();
            }

            List<Auction> filtered = base.stream()
                    .filter(a -> matchesLocation(a, regionFilter, country))
                    .sorted(buildAuctionComparator(sortBy, direction))
                    .toList();

            Page<Auction> pageResult = toPage(filtered, pageable);
            return pageResult.map(this::toListDto);
        }


        if (!hasLocationFilter) {

            Page<Auction> auctionsPage;
            if (categoryId != null) {
                auctionsPage = auctionRepository.searchActiveAuctionsByCategory(
                        AuctionStatus.ACTIVE, now, keyword, categoryId, pageable
                );
            } else {
                auctionsPage = auctionRepository.searchActiveAuctions(
                        AuctionStatus.ACTIVE, now, keyword, pageable
                );
            }

            if (!auctionsPage.isEmpty()) {
                return auctionsPage.map(this::toListDto);
            }

            List<Auction> candidates;
            if (categoryId != null) {
                candidates = auctionRepository
                        .findByStatusAndStartDateBeforeAndEndDateAfterAndCategoryId(
                                AuctionStatus.ACTIVE, now, now, categoryId, Pageable.unpaged()
                        )
                        .getContent();
            } else {
                candidates = auctionRepository
                        .findByStatusAndStartDateBeforeAndEndDateAfter(
                                AuctionStatus.ACTIVE, now, now, Pageable.unpaged()
                        )
                        .getContent();
            }

            String trimmedKeyword = keyword.trim();

            List<Map.Entry<Auction, Integer>> scored = candidates.stream()
                    .map(a -> Map.entry(a, computeFuzzyScore(a, trimmedKeyword)))
                    .filter(e -> e.getValue() >= FUZZY_MIN_SCORE)
                    .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                    .toList();

            List<Auction> fuzzyMatched = scored.stream()
                    .map(Map.Entry::getKey)
                    .toList();

            Page<Auction> fuzzyPage = toPage(fuzzyMatched, pageable);
            return fuzzyPage.map(this::toListDto);
        }

        List<Auction> baseSearch;
        if (categoryId != null) {
            baseSearch = auctionRepository.searchActiveAuctionsByCategory(
                    AuctionStatus.ACTIVE, now, keyword, categoryId, Pageable.unpaged()
            ).getContent();
        } else {
            baseSearch = auctionRepository.searchActiveAuctions(
                    AuctionStatus.ACTIVE, now, keyword, Pageable.unpaged()
            ).getContent();
        }

        List<Auction> filteredSearch = baseSearch.stream()
                .filter(a -> matchesLocation(a, regionFilter, country))
                .sorted(buildAuctionComparator(sortBy, direction))
                .toList();

        if (!filteredSearch.isEmpty()) {
            Page<Auction> pageResult = toPage(filteredSearch, pageable);
            return pageResult.map(this::toListDto);
        }

        List<Auction> baseCandidates;
        if (categoryId != null) {
            baseCandidates = auctionRepository
                    .findByStatusAndStartDateBeforeAndEndDateAfterAndCategoryId(
                            AuctionStatus.ACTIVE, now, now, categoryId, Pageable.unpaged()
                    ).getContent();
        } else {
            baseCandidates = auctionRepository
                    .findByStatusAndStartDateBeforeAndEndDateAfter(
                            AuctionStatus.ACTIVE, now, now, Pageable.unpaged()
                    ).getContent();
        }

        List<Auction> candidates = baseCandidates.stream()
                .filter(a -> matchesLocation(a, regionFilter, country))
                .toList();

        String trimmedKeyword = keyword.trim();

        List<Map.Entry<Auction, Integer>> scored = candidates.stream()
                .map(a -> Map.entry(a, computeFuzzyScore(a, trimmedKeyword)))
                .filter(e -> e.getValue() >= FUZZY_MIN_SCORE)
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .toList();

        List<Auction> fuzzyMatched = scored.stream()
                .map(Map.Entry::getKey)
                .toList();

        Page<Auction> fuzzyPage = toPage(fuzzyMatched, pageable);
        return fuzzyPage.map(this::toListDto);
    }


    /**
     * Get expired auctions method
     *
     * User's can see some recent expired auctions
     *
     * @param days
     * @param sortBy
     * @param direction
     * @param categoryId
     * @param page
     * @param size
     * @param region
     * @param country
     * @return
     */
    @Transactional
    public Page<AuctionListItemDto> getExpiredAuctionsLastDays(
            int days,
            String sortBy,
            String direction,
            Long categoryId,
            int page,
            int size,
            String region,
            String country
    ) {
//        refreshExpiredAuctions();

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate" -> Sort.by("endDate");
            default -> Sort.by("endDate");
        };
        if ("desc".equalsIgnoreCase(direction)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(days);

        Region regionFilter = parseRegion(region);
        boolean hasLocationFilter =
                regionFilter != null || (country != null && !country.isBlank());

        if (!hasLocationFilter) {
            Page<Auction> auctionsPage;
            if (categoryId != null) {
                auctionsPage = auctionRepository
                        .findByStatusAndEndDateBetweenAndCategoryId(
                                AuctionStatus.EXPIRED, from, now, categoryId, pageable
                        );
            } else {
                auctionsPage = auctionRepository
                        .findByStatusAndEndDateBetween(
                                AuctionStatus.EXPIRED, from, now, pageable
                        );
            }
            return auctionsPage.map(this::toListDto);
        }

        List<Auction> base;
        if (categoryId != null) {
            base = auctionRepository
                    .findByStatusAndEndDateBetweenAndCategoryId(
                            AuctionStatus.EXPIRED, from, now, categoryId, Pageable.unpaged()
                    )
                    .getContent();
        } else {
            base = auctionRepository
                    .findByStatusAndEndDateBetween(
                            AuctionStatus.EXPIRED, from, now, Pageable.unpaged()
                    )
                    .getContent();
        }

        List<Auction> filtered = base.stream()
                .filter(a -> matchesLocation(a, regionFilter, country))
                .sorted(buildAuctionComparator(sortBy, direction))
                .toList();

        Page<Auction> pageResult = toPage(filtered, pageable);
        return pageResult.map(this::toListDto);
    }


    /**
     * Get specific auction
     *
     * Users can view auction's full details
     *
     *
     * @param auctionId
     * @return
     */
    @Transactional(readOnly = true)
    public AuctionResponseDto getAuctionById(Long auctionId) {
        log.info("Auction: {} cache miss", auctionId);
//        refreshExpiredAuctions();

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        if (auction.getEndDate().isBefore(oneMonthAgo))
            throw new IllegalArgumentException("Auction expired more than 1 month ago");

        if (auction.getStatus() == AuctionStatus.PENDING_APPROVAL || auction.getStatus() == AuctionStatus.CANCELLED)
            throw new IllegalArgumentException("This is not an active auction");

        return toDetailsDto(auction);
    }

    /**
     * Admin approves an auction
     *
     * @param auctionId
     */
    @Transactional
    public void approveAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            throw new RuntimeException("Auction is already active");
        }

        if (auction.getStatus() == AuctionStatus.CANCELLED) {
            throw new RuntimeException("Cancelled auction cannot be approved");
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        auctionRepository.save(auction);

        Long ownerId = auction.getOwner().getId();

        String metadata = "{\"auctionId\":" + auction.getId()
                + ",\"newStatus\":\"ACTIVE\""
                + "}";

            eventPublisher.publishEvent(new NotificationEvent(
                ownerId,
                NotificationType.AUCTION_APPROVED,
                "Auction approved",
                "Your auction \"" + auction.getTitle() + "\" was approved and is now LIVE.",
                metadata
        ));
    }

    @Transactional(readOnly = true)
    public List<AuctionResponseDto> getPendingAuctions() {
        return auctionRepository.findByStatus(AuctionStatus.PENDING_APPROVAL)
                .stream()
                .map(this::toDetailsDto)
                .toList();
    }


    // ---------------------------------------------------
    // MAPPERS
    // ---------------------------------------------------

    private AuctionResponseDto toDetailsDto(Auction auction) {

        String categoryName = auction.getCategory() != null
                ? auction.getCategory().getName()
                : null;

        UserEntity owner = auction.getOwner();

        String sellerLocation = null;
        if (owner != null) {
            Location location = locationRepository.findByUser(owner).orElse(null);
            if (location != null) {
                sellerLocation = location.getRegion() + ", " + location.getCountry();
            }
        }

        List<String> imageUrls = auction.getAuctionImages() != null
                ? auction.getAuctionImages().stream()
                .sorted(Comparator.comparingInt(Image::getSortOrder))
                .limit(10)
                .map(Image::getUrl)
                .toList()
                : List.of();

        List<BidResponseDto> bids = auction.getBids().stream()
                .sorted(
                        Comparator.comparing(Bid::getAmount).reversed()
                )
                .map(b -> new BidResponseDto(
                        b.getId(),
                        b.getAmount(),
                        b.getBidder().getUsername(),
                        b.getCreatedAt(),
                        auction.getId()
                ))
                .toList();


        List<AuctionMessage> auctionMessages =
                auctionMessageRepository.findByAuctionIdOrderByCreatedAtAsc(auction.getId());

        final int MAX_MESSAGES = 25;
        java.util.Map<Long, Integer> perUserCount = new java.util.HashMap<>();
        java.util.List<ChatMessageResponse> chatMessageResponses = new java.util.ArrayList<>();

        for (AuctionMessage c : auctionMessages) {
            Long senderId = c.getSender().getId();
            int soFar = perUserCount.getOrDefault(senderId, 0) + 1;
            perUserCount.put(senderId, soFar);

            int remaining = MAX_MESSAGES - soFar;
            if (remaining < 0) remaining = 0;

            ChatMessageResponse dto = new ChatMessageResponse(
                    c.getId(),
                    c.getSender().getUsername(),
                    c.getSender().getFirebaseId(),
                    c.getContent(),
                    c.getCreatedAt(),
                    remaining
            );
            chatMessageResponses.add(dto);
        }


        boolean eligibleForBid = false;
        boolean eligibleForChat = false;

        if (!isAuctionClosedForInteraction(auction)) {
            eligibleForBid = isCurrentUserEligibleForBid();
            eligibleForChat = computeEligibleForChat(auction);
        }


        return new AuctionResponseDto(
                auction.getId(),
                auction.getTitle(),
                categoryName,
                owner.getUsername(),
                sellerLocation,
                auction.getShortDescription(),
                auction.getDescription(),
                auction.getStartingAmount(),
                auction.getMinBidIncrement(),
                auction.getStartDate(),
                auction.getEndDate(),
                auction.getStatus(),
                auction.getShippingCostPayer(),
                imageUrls,
                chatMessageResponses,
                bids,
                eligibleForBid,
                eligibleForChat
        );
    }


    private AuctionListItemDto toListDto(Auction auction) {

        String categoryName = auction.getCategory() != null
                ? auction.getCategory().getName()
                : null;

        UserEntity owner = auction.getOwner();

        String sellerLocation = null;
        if (owner != null) {
            Location location = locationRepository.findByUser(owner).orElse(null);
            if (location != null) {
                sellerLocation = location.getRegion() + ", " + location.getCountry();
            }
        }

        // main image
        String mainImageUrl = auction.getAuctionImages() != null
                && !auction.getAuctionImages().isEmpty()
                ? auction.getAuctionImages().get(0).getUrl()
                : null;

        // top bid
        BigDecimal topBidAmount = null;
        String topBidderUsername = null;
        if (auction.getBids() != null && !auction.getBids().isEmpty()) {
            Bid topBid = auction.getBids().isEmpty()
                    ? null
                    : auction.getBids().get(0);
            if (topBid != null) {
                topBidAmount = topBid.getAmount();
                topBidderUsername = topBid.getBidder().getUsername();
            }
        }
        boolean eligibleForBid = false;
        if (!isAuctionClosedForInteraction(auction)) {
            eligibleForBid = isCurrentUserEligibleForBid();
        }

        return new AuctionListItemDto(
                auction.getId(),
                auction.getTitle(),
                categoryName,
                owner.getUsername(),
                sellerLocation,
                auction.getShortDescription(),   // 👈 μικρή περιγραφή
                auction.getStartingAmount(),
                auction.getMinBidIncrement(),
                topBidAmount,
                topBidderUsername,
                mainImageUrl,
                auction.getEndDate(),
                auction.getStatus(),
                eligibleForBid
        );
    }


    private LocationDto mapLocation(UserEntity owner) {
        if (owner == null) {
            return null;
        }
        Location location = locationRepository.findByUser(owner).orElseThrow(()->new RuntimeException());

        return new LocationDto(
                location.getCountry(),
                location.getRegion(),
                location.getCity(),
                location.getAddressLine(),
                location.getPostalCode());
    }

    private int computeFuzzyScore(Auction auction, String keyword) {
        String title = auction.getTitle() != null ? auction.getTitle() : "";
        String description = auction.getDescription() != null ? auction.getDescription() : "";

        int scoreTitle = fuzzyScore.fuzzyScore(title, keyword);
        int scoreDesc = fuzzyScore.fuzzyScore(description, keyword);

        return Math.max(scoreTitle, scoreDesc);
    }

    private <T> Page<T> toPage(List<T> list, Pageable pageable) {
        int total = list.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        if (start > end) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        return new PageImpl<>(list.subList(start, end), pageable, total);
    }


    private Region parseRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        try {
            return Region.valueOf(region.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid region: " + region);
        }
    }

    private boolean matchesLocation(Auction auction, Region regionFilter, String countryFilter) {
        if (regionFilter == null && (countryFilter == null || countryFilter.isBlank())) {
            return true;
        }

        UserEntity owner = auction.getOwner();
        if (owner == null) {
            return false;
        }

        Location loc = locationRepository.findByUser(owner).orElse(null);
        if (loc == null) {
            return false;
        }

        boolean regionOk = true;
        boolean countryOk = true;

        if (regionFilter != null) {
            regionOk = loc.getRegion() == regionFilter;
        }

        if (countryFilter != null && !countryFilter.isBlank()) {
            countryOk = loc.getCountry() != null &&
                    loc.getCountry().equalsIgnoreCase(countryFilter.trim());
        }

        return regionOk && countryOk;
    }

    private Comparator<Auction> buildAuctionComparator(String sortBy, String direction) {
        Comparator<Auction> comparator;
        if ("startDate".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(Auction::getStartDate);
        } else {
            comparator = Comparator.comparing(Auction::getEndDate);
        }
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }


    @Transactional
    public Page<AuctionListItemDto> getMyActiveBidAuctions(
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate" -> Sort.by("endDate");
            default -> Sort.by("endDate");
        };
        if ("desc".equalsIgnoreCase(direction)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now();

        Page<Auction> pageAuctions = auctionRepository.findActiveAuctionsUserHasBid(
                AuctionStatus.ACTIVE,
                now,
                user,
                pageable
        );
        userActivityService.saveUserActivityAsync(Endpoint.GET_MY_ACTIVE_BID_AUCTIONS,"User: " + getUserFirebaseId() + " had requested to see the auctions he bided");


        return pageAuctions.map(this::toListDto);
    }


    @Transactional
    public Page<AuctionListItemDto> getMyWonAuctions(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        String firebaseId = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User not found"));

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "desc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate"   -> Sort.by("endDate");
            default          -> Sort.by("endDate");
        };

        if ("asc".equalsIgnoreCase(direction)) {
            sort = sort.ascending();
        } else {
            sort = sort.descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Auction> pageAuctions = auctionRepository
                .findByWinnerAndStatus(user, AuctionStatus.EXPIRED, pageable);

        userActivityService.saveUserActivityAsync(Endpoint.GET_MY_WON_AUCTIONS,"User: " + getUserFirebaseId() + " had requested to see the auctions he won");


        return pageAuctions.map(this::toListDto);
    }



    @Transactional
    public Page<AuctionListItemDto> getMyPendingAuctions(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";       // π.χ. ταξινόμηση με βάση πότε τελειώνουν
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";        // πρώτα αυτά που λήγουν πιο σύντομα
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate"   -> Sort.by("endDate");
            default          -> Sort.by("endDate");
        };

        if ("desc".equalsIgnoreCase(direction)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        String firebaseId = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity owner = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User not found"));

        Page<Auction> pendingPage = auctionRepository.findByStatusAndOwner(
                AuctionStatus.PENDING_APPROVAL,
                owner,
                pageable
        );
        userActivityService.saveUserActivityAsync(Endpoint.GET_MY_PENDING_AUCTIONS,"User: " + getUserFirebaseId() + " had requested to see the auctions that are pending to be approved");


        return pendingPage.map(this::toListDto);
    }



    private boolean isCurrentUserEligibleForBid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false; // guest / χωρίς token
        }


        return true;
    }


    private boolean isCurrentUserEligibleForChat() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }

        String firebaseId = auth.getName();
        return userEntityRepository.findByFirebaseId(firebaseId)
                .map(UserEntity::isEligibleForChat)
                .orElse(false);
    }


    private boolean isExpired(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        return auction.getStatus() == AuctionStatus.EXPIRED
                || auction.getEndDate().isBefore(now);
    }

    private boolean isAuctionClosedForInteraction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        return auction.getEndDate() != null && auction.getEndDate().isBefore(now);
    }

    private boolean computeEligibleForChat(Auction auction) {
        if (auction.getEndDate() != null &&
                auction.getEndDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        final String firebaseId;
        try {
            firebaseId = getUserFirebaseId();
        } catch (Exception ex) {
            return false;
        }

        Optional<UserEntity> optUser =
                userEntityRepository.findByFirebaseId(firebaseId);

        if (optUser.isEmpty()) {
            return false;
        }

        UserEntity user = optUser.get();
        Long userId = user.getId();

        boolean hasBidOnThisAuction =
                bidRepository.existsByAuctionIdAndBidderId(auction.getId(), userId);

        boolean hasCreatedAnyAuction =
                auctionRepository.existsByOwner_Id(userId);

        boolean hasWonAnyAuction =
                auctionRepository.existsByWinner_Id(userId);

        return hasBidOnThisAuction
                || hasCreatedAnyAuction
                || hasWonAnyAuction;
    }




    @Transactional
    public AuctionResponseDto adminEditAuction(Long auctionId,
                                               AuctionAdminUpdateRequest request) {

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

        if (auction.getStatus() == AuctionStatus.EXPIRED
                || auction.getStatus() == AuctionStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot edit expired or cancelled auction");
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            auction.setCategory(category);
        }

        if (request.getTitle() != null) {
            auction.setTitle(request.getTitle().trim());
        }

        if (request.getShortDescription() != null) {
            auction.setShortDescription(request.getShortDescription().trim());
        }

        if (request.getDescription() != null) {
            auction.setDescription(request.getDescription().trim());
        }

        if (request.getStartingAmount() != null) {
            auction.setStartingAmount(request.getStartingAmount());
        }

        if (request.getMinBidIncrement() != null) {
            auction.setMinBidIncrement(request.getMinBidIncrement());
        }

        if (request.getStartDate() != null) {
            auction.setStartDate(request.getStartDate());
        }

        if (request.getEndDate() != null) {
            auction.setEndDate(request.getEndDate());
        }

        if (request.getShippingCostPayer() != null) {
            auction.setShippingCostPayer(request.getShippingCostPayer());
        }

        if(request.getAuctionStatus() != null){
            auction.setStatus(request.getAuctionStatus());
        }

        Auction saved = auctionRepository.save(auction);

        return toDetailsDto(saved);
    }


    @Transactional
    public Page<AuctionListItemDto> getMyAuctions(
            int page,
            int size,
            String sortBy,
            String direction,
            String statusGroup      // ACTIVE | RECENT_EXPIRED | OLD | CANCELLED
    ) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "desc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate"   -> Sort.by("endDate");
            default          -> Sort.by("endDate");
        };

        if ("asc".equalsIgnoreCase(direction)) {
            sort = sort.ascending();
        } else {
            sort = sort.descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        String firebaseId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        UserEntity owner = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now();

        String group = (statusGroup == null || statusGroup.isBlank())
                ? "ACTIVE"
                : statusGroup.trim().toUpperCase(Locale.ROOT);

        Page<Auction> pageAuctions;

        switch (group) {
            case "ACTIVE" -> {
                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.ACTIVE,
                                pageable
                        );
            }
            case "EXPIRED" -> {

                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.EXPIRED,
                                pageable
                        );
            }
            case "CANCELLED" -> {
                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.CANCELLED,
                                pageable
                        );
            }case "PENDING_APPROVAL" -> {
                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.PENDING_APPROVAL,
                                pageable
                        );
            }
            default -> throw new IllegalArgumentException("Invalid statusGroup: " + statusGroup);
        }

        userActivityService.saveUserActivityAsync(
                Endpoint.GET_MY_AUCTIONS,
                "User: " + firebaseId +
                        ", statusGroup=" + group +
                        ", page=" + page +
                        ", size=" + size
        );

        return pageAuctions.map(this::toListDto);
    }


        @Transactional
        public void cancelAuction(Long auctionId) {
            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

            if (auction.getStatus() == AuctionStatus.CANCELLED) {
                throw new RuntimeException("Auction is already cancelled");//Todo: check if getAuction endpoint doesn't show cancelled auctions
            }
            auction.setStatus(AuctionStatus.CANCELLED);

            auctionRepository.save(auction);

            Long ownerId = auction.getOwner().getId();

            String metadata = "{\"auctionId\":" + auction.getId()
                    + ",\"newStatus\":\"CANCELLED\""
                    + "}";

            eventPublisher.publishEvent(new NotificationEvent(
                    ownerId,
                    NotificationType.AUCTION_CANCELLED,
                    "Auction cancelled",
                    "Your auction \"" + auction.getTitle() + "\" was cancelled due to missing or not clear info.",
                    metadata
            ));
        }


    @Transactional
    public Page<AuctionListItemDto> adminGetNonActiveAuctions(int page, int size, String sortBy, String direction, String statusGroup) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "endDate";
        }
        if (direction == null || direction.isBlank()) {
            direction = "desc";
        }

        Sort sort = switch (sortBy) {
            case "startDate" -> Sort.by("startDate");
            case "endDate"   -> Sort.by("endDate");
            default          -> Sort.by("endDate");
        };

        if ("asc".equalsIgnoreCase(direction)) {
            sort = sort.ascending();
        } else {
            sort = sort.descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        LocalDateTime now = LocalDateTime.now();

        String group = (statusGroup == null || statusGroup.isBlank())
                ? "EXPIRED"
                : statusGroup.trim().toUpperCase(Locale.ROOT);

        Page<Auction> pageAuctions;

        switch (group) {
            case "EXPIRED" -> {

                pageAuctions = auctionRepository
                        .findByStatus(
                                AuctionStatus.EXPIRED,
                                pageable
                        );
            }
            case "CANCELLED" -> {
                pageAuctions = auctionRepository
                        .findByStatus(
                                AuctionStatus.CANCELLED,
                                pageable
                        );
            }case "PENDING_APPROVAL" -> {
                pageAuctions = auctionRepository
                        .findByStatus(
                                AuctionStatus.PENDING_APPROVAL,
                                pageable
                        );
            }
            default -> throw new IllegalArgumentException("Invalid statusGroup: " + statusGroup);
        }

        userActivityService.saveUserActivityAsync(
                Endpoint.GET_MY_AUCTIONS,
                "Admin: " + getUserFirebaseId() +
                        ", statusGroup=" + group +
                        ", page=" + page +
                        ", size=" + size
        );

        return pageAuctions.map(this::toListDto);
    }

}
