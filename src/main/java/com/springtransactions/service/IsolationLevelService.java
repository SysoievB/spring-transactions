package com.springtransactions.service;

import com.springtransactions.exception.AccountNotFoundException;
import com.springtransactions.model.Account;
import com.springtransactions.model.TransactionLog;
import com.springtransactions.repository.AccountRepository;
import com.springtransactions.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service demonstrating all Spring transaction isolation levels:
 * - READ_UNCOMMITTED: Allows dirty reads, non-repeatable reads, and phantom reads
 * - READ_COMMITTED: Prevents dirty reads; allows non-repeatable reads and phantom reads
 * - REPEATABLE_READ: Prevents dirty reads and non-repeatable reads; allows phantom reads
 * - SERIALIZABLE: Prevents all concurrency issues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IsolationLevelService {

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;

    /**
     * READ_UNCOMMITTED: Lowest isolation level
     * - Allows reading uncommitted changes from other transactions (dirty reads)
     * - May see data that will be rolled back
     * - Highest performance, lowest consistency
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public Account transferWithReadUncommitted(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Executing transfer with READ_UNCOMMITTED isolation");
        
        transactionLogRepository.save(new TransactionLog(
            "TRANSFER", 
            "Transfer with READ_UNCOMMITTED", 
            "READ_UNCOMMITTED", 
            "REQUIRED"
        ));

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccount));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        return from;
    }

    /**
     * READ_COMMITTED: Default for most databases
     * - Only reads committed data
     * - Prevents dirty reads
     * - Can still see different data on repeated reads (non-repeatable reads)
     * - Can see new rows added by other transactions (phantom reads)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account transferWithReadCommitted(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Executing transfer with READ_COMMITTED isolation");
        
        transactionLogRepository.save(new TransactionLog(
            "TRANSFER", 
            "Transfer with READ_COMMITTED", 
            "READ_COMMITTED", 
            "REQUIRED"
        ));

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        // Simulating some processing time where other transactions might commit
        simulateProcessing();
        
        // Re-reading might show different balance if another transaction committed
        from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccount));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        return from;
    }

    /**
     * REPEATABLE_READ: Stricter than READ_COMMITTED
     * - Ensures same data is read throughout transaction
     * - Prevents dirty reads and non-repeatable reads
     * - Can still see phantom reads (new rows)
     * - Uses row-level locks or snapshot isolation
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Account transferWithRepeatableRead(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Executing transfer with REPEATABLE_READ isolation");
        
        transactionLogRepository.save(new TransactionLog(
            "TRANSFER", 
            "Transfer with REPEATABLE_READ", 
            "REPEATABLE_READ", 
            "REQUIRED"
        ));

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        BigDecimal initialBalance = from.getBalance();
        
        simulateProcessing();
        
        // Re-reading will show same balance as before (repeatable read)
        from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        log.info("Initial balance: {}, Current balance: {}", initialBalance, from.getBalance());
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccount));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        return from;
    }

    /**
     * SERIALIZABLE: Highest isolation level
     * - Complete isolation from other transactions
     * - Prevents dirty reads, non-repeatable reads, and phantom reads
     * - Transactions execute as if serially, one after another
     * - Lowest performance, highest consistency
     * - May cause more deadlocks and timeouts
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Account transferWithSerializable(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Executing transfer with SERIALIZABLE isolation");
        
        transactionLogRepository.save(new TransactionLog(
            "TRANSFER", 
            "Transfer with SERIALIZABLE", 
            "SERIALIZABLE", 
            "REQUIRED"
        ));

        // With SERIALIZABLE, this transaction has exclusive access to the data
        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccount));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        log.info("Transfer completed with SERIALIZABLE isolation");
        
        return from;
    }

    /**
     * DEFAULT: Uses the default isolation level of the underlying database
     * - For PostgreSQL: READ_COMMITTED
     * - For MySQL: REPEATABLE_READ
     * - For H2: READ_COMMITTED
     */
    @Transactional(isolation = Isolation.DEFAULT)
    public Account transferWithDefault(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Executing transfer with DEFAULT isolation");
        
        transactionLogRepository.save(new TransactionLog(
            "TRANSFER", 
            "Transfer with DEFAULT", 
            "DEFAULT", 
            "REQUIRED"
        ));

        Account from = accountRepository.findByAccountNumber(fromAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccount));
        
        Account to = accountRepository.findByAccountNumber(toAccount)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccount));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.save(from);
        accountRepository.save(to);

        return from;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public Account getAccountReadCommitted(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, readOnly = true)
    public Account getAccountSerializable(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    private void simulateProcessing() {
        try {
            Thread.sleep(100); // Simulate some processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
