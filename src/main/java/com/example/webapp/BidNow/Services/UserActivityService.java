package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Entities.UserActivity;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Repositories.UserActivityRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

@Service
public class UserActivityService {

    private final UserEntityRepository userEntityRepository;
    private final UserActivityRepository userActivityRepository;
    private final Executor userActivityExecutor;

    public UserActivityService(UserEntityRepository userEntityRepository,
                               UserActivityRepository userActivityRepository,
                               Executor userActivityExecutor) {
        this.userEntityRepository = userEntityRepository;
        this.userActivityRepository = userActivityRepository;
        this.userActivityExecutor = userActivityExecutor;
    }

    /**
     * Καλείς αυτή τη μέθοδο από άλλα services.
     * - Αν υπάρχει ενεργό @Transactional, κάνει register ένα callback
     *   που θα τρέξει ΜΕΤΑ το commit, σε άλλο thread.
     * - Αν δεν υπάρχει transaction, απλά στέλνει το task στο thread pool αμέσως.
     */
    public void saveUserActivityAsync(Endpoint endpoint, String details) {

        // ΠΟΛΥ ΣΗΜΑΝΤΙΚΟ: παίρνουμε ΤΩΡΑ το firebaseId,
        // γιατί στο async thread πιθανό να μην έχουμε SecurityContext.
        String firebaseId = getUserFirebaseId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Υπάρχει ενεργό transaction (π.χ. uploadAuctionPhotos)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Αυτό θα τρέξει ΜΕΤΑ το commit, σε άλλο thread
                    userActivityExecutor.execute(() ->
                            doSaveUserActivity(firebaseId, endpoint, details)
                    );
                }
            });
        } else {
            // Δεν υπάρχει transaction, τρέχουμε κατευθείαν async
            userActivityExecutor.execute(() ->
                    doSaveUserActivity(firebaseId, endpoint, details)
            );
        }
    }

    // Εδώ γίνεται το πραγματικό save, σε ξεχωριστό μικρό transaction που
    // ανοίγεται από τα Spring Data repositories (save()).
    protected void doSaveUserActivity(String firebaseId, Endpoint endpoint, String details) {
        UserEntity user = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserActivity userActivity = new UserActivity(
                user,
                endpoint,
                details,
                "None"
        );

        userActivityRepository.save(userActivity);
    }
}
