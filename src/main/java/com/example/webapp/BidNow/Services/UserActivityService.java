package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Configs.AsyncConfig;
import com.example.webapp.BidNow.Entities.UserActivity;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Repositories.UserActivityRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * User activity service
 *
 * This is a logging service for auditing and
 * data analysis purposes
 */
@Service
public class UserActivityService {

    private final UserEntityRepository userEntityRepository;
    private final UserActivityRepository userActivityRepository;
    private final Executor userActivityExecutor;

    public UserActivityService(UserEntityRepository userEntityRepository,
                               UserActivityRepository userActivityRepository,
                               @Qualifier("userActivityExecutor") Executor userActivityExecutor) {
        this.userEntityRepository = userEntityRepository;
        this.userActivityRepository = userActivityRepository;
        this.userActivityExecutor = userActivityExecutor;
    }


    /**
     * This method is called from other services in order to log user's
     * activities
     *
     * Notes:
     *  - We have two scenarios where a thread pool is standby to serve
     *  these requests initialized in ({@link AsyncConfig} as userActivityExecutor:
     *
     *  1) Called by a transactional method and the main thread assigns the request to it's ThreadLocal
     *  synchronizations list in order to execute it after commit and prevent logging user's non-completed actions.
     *  After the transaction commits the main thread assigns the task to the thread pool
     *  2) Called by a non-transactional method where a thread instantly assigns the task to
     *  the ThreadPool
     *
     * @param endpoint The endpoint, api that was called by the user.
     * @param details The details of the user's action
     */
    public void saveUserActivityAsync(Endpoint endpoint, String details) {

        // Find user's firebase id in order to pass it to the pool
        String firebaseId = getUserFirebaseId();

        // Check if thread runs in a transactional method
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Register after commit function in threads ThreadLocal synchronization list
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // This will run in the thread pool after commit of the transaction
                    userActivityExecutor.execute(() ->
                            doSaveUserActivity(firebaseId, endpoint, details)
                    );
                }
            });
        } else {
            // Instantly assign the task to the pool (this isn't called through a transactional method)
            userActivityExecutor.execute(() ->
                    doSaveUserActivity(firebaseId, endpoint, details)
            );
        }
    }

    /**
     *
     * Commit logging in database
     *
     * @param firebaseId , user's firebase id
     * @param endpoint , api that user called
     * @param details , details of user's action
     */
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
