package com.springtransactions.service;

import com.springtransactions.exception.AccountNotFoundException;
import com.springtransactions.exception.InsufficientFundsException;
import com.springtransactions.exception.TransientException;
import com.springtransactions.model.Account;
import com.springtransactions.model.TransactionLog;
import com.springtransactions.repository.AccountRepository;
import com.springtransactions.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service demonstrating Spring Retry integration with transactions.
 * Shows different retry strategies, backoff policies, and recovery mechanisms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final AtomicInteger attemptCounter = new AtomicInteger(0);

    /**
     * Simple retry with fixed backoff
     * - Retries up to 3 times
     * - 1 second delay between retries
     * - Only retries on TransientException
     */
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional(propagation = Propagation.REQUIRED)
    public Account transferWithFixedRetry(String fromAccount, String toAccount, 
                                         BigDecimal amount, boolean simulateFailure) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with fixed retry", 
            "DEFAULT", 
            "REQUIRED"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        if (simulateFailure && attempt < 3) {
            log.warn("Simulating transient failure on attempt #{}", attempt);
            throw new TransientException("Simulated transient failure");
        }

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        attemptCounter.set(0); // Reset for next call
        return from;
    }

    /**
     * Retry with exponential backoff
     * - Retries up to 5 times
     * - Exponential backoff: 1s, 2s, 4s, 8s, 16s
     * - Multiplier of 2.0
     */
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 5,
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 30000
        )
    )
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED
    )
    public Account transferWithExponentialRetry(String fromAccount, String toAccount, BigDecimal amount) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer with exponential backoff - attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with exponential retry", 
            "READ_COMMITTED", 
            "REQUIRED"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        if (attempt < 3) {
            log.warn("Simulating transient failure on attempt #{}", attempt);
            throw new TransientException("Transient error - will retry with backoff");
        }

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        attemptCounter.set(0);
        return from;
    }

    /**
     * Retry with recovery method
     * - If all retries fail, recovery method is called
     * - Recovery method provides fallback behavior
     */
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500),
        recover = "recoverFromFailedTransfer"
    )
    @Transactional(propagation = Propagation.REQUIRED)
    public Account transferWithRecovery(String fromAccount, String toAccount, BigDecimal amount) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer with recovery - attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with recovery", 
            "DEFAULT", 
            "REQUIRED"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        // Always fail to demonstrate recovery
        throw new TransientException("Persistent failure requiring recovery");
    }

    /**
     * Recovery method for transferWithRecovery
     * - Must have same return type as original method
     * - First parameter must be the exception
     * - Remaining parameters must match original method
     */
    @Recover
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account recoverFromFailedTransfer(TransientException e, 
                                            String fromAccount, String toAccount, BigDecimal amount) {
        log.error("All retry attempts failed. Executing recovery: {}", e.getMessage());
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RECOVERY", 
            "Recovery from failed transfer: " + e.getMessage(), 
            "DEFAULT", 
            "REQUIRES_NEW"
        );
        transactionLogRepository.save(txLog);

        attemptCounter.set(0);
        
        // Return account in original state
        return accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    /**
     * Retry with multiple exception types
     * - Retries on TransientException and specific RuntimeExceptions
     * - Does not retry on InsufficientFundsException (business logic error)
     */
    @Retryable(
        retryFor = {TransientException.class, RuntimeException.class},
        noRetryFor = {InsufficientFundsException.class, AccountNotFoundException.class},
        maxAttempts = 4,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.REPEATABLE_READ
    )
    public Account transferWithSelectiveRetry(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String failureType
    ) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer with selective retry - attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with selective retry", 
            "REPEATABLE_READ", 
            "REQUIRED"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        // Simulate different failure types
        if ("transient".equals(failureType) && attempt < 2) {
            throw new TransientException("Transient failure - will retry");
        }
        
        if ("insufficient_funds".equals(failureType)) {
            // This will NOT be retried - business logic error
            throw new InsufficientFundsException("Insufficient funds - no retry");
        }

        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        attemptCounter.set(0);
        return from;
    }

    /**
     * Demonstrates retry with SERIALIZABLE isolation
     * - Useful for handling serialization failures
     * - Database may throw errors on concurrent access
     * - Retry can resolve temporary conflicts
     */
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 500, multiplier = 1.5)
    )
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.SERIALIZABLE
    )
    public Account transferWithSerializableRetry(String fromAccount, String toAccount, BigDecimal amount) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer with SERIALIZABLE isolation - attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with SERIALIZABLE and retry", 
            "SERIALIZABLE", 
            "REQUIRED"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        if (attempt < 2) {
            throw new TransientException("Simulating serialization conflict");
        }

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        attemptCounter.set(0);
        return from;
    }

    /**
     * Retry with REQUIRES_NEW propagation
     * - Each retry attempt is in a new transaction
     * - Failed attempts don't affect each other
     */
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account transferWithRequiresNewRetry(String fromAccount, String toAccount, BigDecimal amount) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Transfer with REQUIRES_NEW - attempt #{}", attempt);
        
        TransactionLog txLog = new TransactionLog(
            "TRANSFER_RETRY", 
            "Transfer with REQUIRES_NEW and retry", 
            "DEFAULT", 
            "REQUIRES_NEW"
        );
        txLog.setRetryAttempt(attempt);
        transactionLogRepository.save(txLog);

        if (attempt < 2) {
            throw new TransientException("Failure in independent transaction");
        }

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        attemptCounter.set(0);
        return from;
    }

    public void resetAttemptCounter() {
        attemptCounter.set(0);
    }
}
