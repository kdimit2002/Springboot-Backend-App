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
    private static final int FUZZY_MIN_SCORE = 9;


    // Fuzzy search (EN – μπορείς να αλλάξεις σε Locale.forLanguageTag("el"))
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

    // ---------------------------------------------------
    // CREATE AUCTION
    // ---------------------------------------------------
    @CachePut(cacheNames = "auctionById", key = "#result.id")
    @Transactional
    public AuctionResponseDto createAuction(AuctionCreateRequest request) {

        // 1. Τρέχων χρήστης (firebaseId)
        UserEntity owner = userEntityRepository.findByFirebaseId(getUserFirebaseId()).orElseThrow(()->  new ResourceNotFoundException("User not found"));

        owner.setEligibleForChat(true);

        // 2. Anti-spam: max 5 auctions / 10 λεπτά
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        long recentAuctions = auctionRepository.countByOwnerAndCreatedAtAfter(owner, cutoff);
        if (recentAuctions >= 5) {
            throw new TooManyRequestsException(
                    "You have created too many auctions recently. Please try again later."
            );
        }

        // 3. Κατηγορία
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // 4. Δημιουργία Auction
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


        // Status αντί για isActive
        auction.setStatus(AuctionStatus.PENDING_APPROVAL);

        Auction saved = auctionRepository.save(auction);
        userActivityService.saveUserActivityAsync(Endpoint.CREATE_AUCTION,"User: " + getUserFirebaseId() + ", AuctionId: " + auction.getId());


        // 5. Μετά το COMMIT στέλνουμε email στον admin
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

        // Επιστρέφουμε FULL DETAILS DTO
        return toDetailsDto(saved);
    }

    // ---------------------------------------------------
    // GET ACTIVE AUCTIONS (LIST)  + FILTERS + SEARCH
    // ---------------------------------------------------
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

        // -----------------------------------
        // 1) ΧΩΡΙΣ keyword (search == null)
        // -----------------------------------
        if (!hasKeyword) {

            // 1a) ΧΩΡΙΣ location filter → μόνο DB
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

            // 1b) ΜΕ location filter → φέρνουμε όλα active, φιλτράρουμε in-memory
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

        // -----------------------------------
        // 2) ΜΕ keyword (search)
        // -----------------------------------
        if (!hasLocationFilter) {
            // 2a) Keyword αλλά ΧΩΡΙΣ location → όπως πριν

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

            // Fuzzy fallback ΧΩΡΙΣ location
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

        // 2b) Keyword ΚΑΙ location filter → DB + in-memory location + μετά fuzzy
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

        // Fuzzy fallback ΜΕ location
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


    // ---------------------------------------------------
    // GET EXPIRED (last N days) – LIST
    // ---------------------------------------------------
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

        // ΧΩΡΙΣ location filter → μόνο DB
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

        // ΜΕ location filter → φέρνουμε όλα expired, φιλτράρουμε in-memory
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


    // ---------------------------------------------------
    // GET auction/{id} – FULL DETAILS
    // ---------------------------------------------------
    @Transactional(readOnly = true)
    public AuctionResponseDto getAuctionById(Long auctionId) {
        log.info("Auction: {} cache miss", auctionId);
//        refreshExpiredAuctions();

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

        // Έλεγχος για endDate > 1 μήνα πριν
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        if (auction.getEndDate().isBefore(oneMonthAgo))
            throw new IllegalArgumentException("Auction expired more than 1 month ago");

        if (auction.getStatus() == AuctionStatus.PENDING_APPROVAL || auction.getStatus() == AuctionStatus.CANCELLED)
            throw new IllegalArgumentException("This is not an active auction");

        return toDetailsDto(auction);
    }

    // ---------------------------------------------------
    // APPROVE + PENDING (ADMIN)
    // ---------------------------------------------------
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

        // ✅ Notification στον owner (AFTER_COMMIT μέσω listener)
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
    // EXPIRED STATUS REFRESH
    // ---------------------------------------------------
//    @Transactional
//    public void refreshExpiredAuctions() {
//        LocalDateTime now = LocalDateTime.now();
//
//        List<Auction> toExpire = auctionRepository
//                .findByStatusAndEndDateBefore(AuctionStatus.ACTIVE, now);
//
//        for (Auction auction : toExpire) {
//            auction.setStatus(AuctionStatus.EXPIRED);
//        }
//    }

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

//        List<AuctionMessage> auctionMessages =
//                auctionMessageRepository.findByAuctionId(auction.getId());
//
//        List<ChatMessageResponse> chatMessageResponses = auctionMessages
//                .stream()
//                .map(c -> new ChatMessageResponse(
//                        c.getId(),
//                        c.getSender().getUsername(),
//                        c.getSender().getFirebaseId(),
//                        c.getContent(),
//                        c.getCreatedAt()
//                ))
//                .toList();

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

        // Location (αν δεν υπάρχει, απλά null ή "Unknown")
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
//            Bid topBid = auction.getBids().stream()
//                    .max(Comparator.comparing(Bid::getAmount))
//                    .orElse(null);
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


    // Map Location → LocationDto (Location του seller)
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

    // Fuzzy score για τίτλο + περιγραφή
    private int computeFuzzyScore(Auction auction, String keyword) {
        String title = auction.getTitle() != null ? auction.getTitle() : "";
        String description = auction.getDescription() != null ? auction.getDescription() : "";

        int scoreTitle = fuzzyScore.fuzzyScore(title, keyword);
        int scoreDesc = fuzzyScore.fuzzyScore(description, keyword);

        return Math.max(scoreTitle, scoreDesc);
    }

    // Μετατρέπει List -> Page (για fuzzy fallback)
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
        // Αν δεν έχει δοθεί ούτε region ούτε country → δεν φιλτράρουμε
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
        // Να ενημερωθούν σωστά τα EXPIRED
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

        // Τρέχων user
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
        // Βρίσκουμε τον current user από Firebase
        String firebaseId = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User not found"));

        // Default sorting: πιο πρόσφατα κερδισμένα πρώτα
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

        // Παίρνουμε όλα τα EXPIRED auctions όπου winner = αυτός ο user
        Page<Auction> pageAuctions = auctionRepository
                .findByWinnerAndStatus(user, AuctionStatus.EXPIRED, pageable);

        userActivityService.saveUserActivityAsync(Endpoint.GET_MY_WON_AUCTIONS,"User: " + getUserFirebaseId() + " had requested to see the auctions he won");


        // Για λίστα (λίγο data) χρησιμοποιούμε το list DTO
        return pageAuctions.map(this::toListDto);
    }



    @Transactional
    public Page<AuctionListItemDto> getMyPendingAuctions(
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        // sorting defaults
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

        // βρίσκουμε τον τωρινό χρήστη από το SecurityContext
        String firebaseId = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity owner = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User not found"));

        // φέρνουμε όλες τις PENDING_APPROVAL για τον συγκεκριμένο owner
        Page<Auction> pendingPage = auctionRepository.findByStatusAndOwner(
                AuctionStatus.PENDING_APPROVAL,
                owner,
                pageable
        );
        userActivityService.saveUserActivityAsync(Endpoint.GET_MY_PENDING_AUCTIONS,"User: " + getUserFirebaseId() + " had requested to see the auctions that are pending to be approved");


        // επιστρέφουμε τα “λίστα” DTO (όχι full details)
        return pendingPage.map(this::toListDto);
    }



    private boolean isCurrentUserEligibleForBid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false; // guest / χωρίς token
        }

        // Αν θες στο μέλλον να περιορίσεις π.χ. μόνο BIDDER:
        // return auth.getAuthorities().stream()
        //         .anyMatch(a -> a.getAuthority().equals("ROLE_BIDDER"));

        return true; // όποιος είναι authenticated με token μπορεί να κάνει bid
    }


    private boolean isCurrentUserEligibleForChat() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false; // guest → δεν μπορεί να γράψει στο chat
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
        // Αν ΔΕΝ είναι ACTIVE, τότε το θεωρούμε κλειστό για bid/chat
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            return true;
        }

        // Αν έχει endDate στο παρελθόν → επίσης κλειστό
        LocalDateTime now = LocalDateTime.now();
        return auction.getEndDate() != null && auction.getEndDate().isBefore(now);
    }

    private boolean computeEligibleForChat(Auction auction) {
        // Δεν έχει νόημα chat αν έχει λήξει
        if (auction.getEndDate() != null &&
                auction.getEndDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        // Αν δεν είμαστε logged in -> όχι chat
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

        // 1) Έχει κάνει bid σε ΑΥΤΗ τη δημοπρασία;
        boolean hasBidOnThisAuction =
                bidRepository.existsByAuctionIdAndBidderId(auction.getId(), userId);

        // 2) Έχει δημιουργήσει τουλάχιστον μία δημοπρασία;
        boolean hasCreatedAnyAuction =
                auctionRepository.existsByOwner_Id(userId);

        // 3) Έχει κερδίσει τουλάχιστον μία δημοπρασία;
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

        // Δεν επιτρέπουμε edit σε EXPIRED ή CANCELLED
        if (auction.getStatus() == AuctionStatus.EXPIRED
                || auction.getStatus() == AuctionStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot edit expired or cancelled auction");
        }

        // Αν δώσει categoryId, την αλλάζουμε
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

        // Αν θέλεις log:
         //userActivityService.saveUserActivityAsync(
        //         Endpoint.UPDATE_AUCTION,
        //         "Admin edited auction " + auctionId
        // );

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
                // Οι τρέχουσες ACTIVE δημοπρασίες του owner
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
                // Όλες οι CANCELLED δημοπρασίες του owner (χωρίς ημερομηνιακό φίλτρο)
                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.CANCELLED,
                                pageable
                        );
            }case "PENDING_APPROVAL" -> {
                // Όλες οι PENDING APPROVAL δημοπρασίες του owner (χωρίς ημερομηνιακό φίλτρο)
                pageAuctions = auctionRepository
                        .findByOwnerAndStatus(
                                owner,
                                AuctionStatus.PENDING_APPROVAL,
                                pageable
                        );
            }
            default -> throw new IllegalArgumentException("Invalid statusGroup: " + statusGroup);
        }

        // logging activity (αν θες το κρατάς)
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

            // ✅ Notification στον owner (AFTER_COMMIT μέσω listener)
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
                // Όλες οι CANCELLED δημοπρασίες του owner (χωρίς ημερομηνιακό φίλτρο)
                pageAuctions = auctionRepository
                        .findByStatus(
                                AuctionStatus.CANCELLED,
                                pageable
                        );
            }case "PENDING_APPROVAL" -> {
                // Όλες οι PENDING APPROVAL δημοπρασίες του owner (χωρίς ημερομηνιακό φίλτρο)
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
