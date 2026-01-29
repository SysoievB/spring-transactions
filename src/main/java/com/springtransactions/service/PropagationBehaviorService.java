package com.springtransactions.service;

import com.springtransactions.exception.AccountNotFoundException;
import com.springtransactions.model.Account;
import com.springtransactions.model.TransactionLog;
import com.springtransactions.repository.AccountRepository;
import com.springtransactions.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service demonstrating all Spring transaction propagation behaviors:
 * - REQUIRED: Join existing or create new transaction
 * - REQUIRES_NEW: Always create new transaction, suspend existing
 * - SUPPORTS: Join if exists, execute non-transactionally otherwise
 * - NOT_SUPPORTED: Execute non-transactionally, suspend existing
 * - MANDATORY: Must have existing transaction, throw exception otherwise
 * - NEVER: Must not have transaction, throw exception if exists
 * - NESTED: Execute in nested transaction if exists, behave like REQUIRED otherwise
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropagationBehaviorService {

    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;

    /**
     * REQUIRED (default): Most common propagation behavior
     * - If transaction exists, join it
     * - If no transaction exists, create a new one
     * - Single transaction boundary for entire method
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateAccountRequired(String accountNumber, BigDecimal amount) {
        log.info("Executing with REQUIRED propagation");
        
        transactionLogRepository.save(new TransactionLog(
            "UPDATE", 
            "Update with REQUIRED", 
            "DEFAULT", 
            "REQUIRED"
        ));

        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    /**
     * REQUIRES_NEW: Creates independent transaction
     * - Always creates new transaction
     * - Suspends current transaction if exists
     * - Commits/rolls back independently of outer transaction
     * - Useful for audit logging that should persist even if outer transaction rolls back
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransactionRequiresNew(String message) {
        log.info("Executing with REQUIRES_NEW propagation");
        
        // This will be committed even if the caller transaction rolls back
        transactionLogRepository.save(new TransactionLog(
            "AUDIT", 
            message, 
            "DEFAULT", 
            "REQUIRES_NEW"
        ));
    }

    /**
     * SUPPORTS: Flexible propagation
     * - If transaction exists, join it
     * - If no transaction exists, execute without transaction
     * - Good for read operations that can work with or without transactions
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Account getAccountSupports(String accountNumber) {
        log.info("Executing with SUPPORTS propagation");
        
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    }

    /**
     * NOT_SUPPORTED: Forces non-transactional execution
     * - Always executes without transaction
     * - Suspends current transaction if exists
     * - Useful for operations that should not participate in transactions
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void performNonTransactionalOperation(String message) {
        log.info("Executing with NOT_SUPPORTED propagation");
        
        // This executes outside any transaction context
        log.info("Non-transactional operation: {}", message);
    }

    /**
     * MANDATORY: Requires existing transaction
     * - Must be called within an existing transaction
     * - Throws exception if no transaction exists
     * - Useful for methods that should only be called as part of larger transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAccountMandatory(String accountNumber, BigDecimal amount) {
        log.info("Executing with MANDATORY propagation");
        
        transactionLogRepository.save(new TransactionLog(
            "UPDATE", 
            "Update with MANDATORY", 
            "DEFAULT", 
            "MANDATORY"
        ));

        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    /**
     * NEVER: Prohibits transactions
     * - Must be called without transaction
     * - Throws exception if transaction exists
     * - Useful for operations that must not be in transaction context
     */
    @Transactional(propagation = Propagation.NEVER)
    public void performNeverOperation(String message) {
        log.info("Executing with NEVER propagation");
        
        // This must execute outside transaction context
        log.info("Never operation: {}", message);
    }

    /**
     * NESTED: Nested transaction support
     * - If transaction exists, creates nested transaction (savepoint)
     * - Nested transaction can roll back independently
     * - Outer transaction can roll back nested transaction
     * - If no transaction exists, behaves like REQUIRED
     * - Only works with DataSourceTransactionManager
     */
    @Transactional(propagation = Propagation.NESTED)
    public void updateAccountNested(String accountNumber, BigDecimal amount) {
        log.info("Executing with NESTED propagation");
        
        transactionLogRepository.save(new TransactionLog(
            "UPDATE", 
            "Update with NESTED", 
            "DEFAULT", 
            "NESTED"
        ));

        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    // Orchestrator methods to demonstrate propagation behavior

    /**
     * Demonstrates REQUIRED with nested calls
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void transferWithRequiredPropagation(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Starting transfer with REQUIRED propagation");
        
        // These all join the same transaction
        updateAccountRequired(fromAccount, amount.negate());
        updateAccountRequired(toAccount, amount);
        
        log.info("Transfer completed with REQUIRED propagation");
    }

    /**
     * Demonstrates REQUIRES_NEW creating independent transactions
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void transferWithAuditLog(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("Starting transfer with audit logging");
        
        try {
            updateAccountRequired(fromAccount, amount.negate());
            updateAccountRequired(toAccount, amount);
            
            // This log will be committed even if transfer fails
            logTransactionRequiresNew("Transfer successful: " + fromAccount + " -> " + toAccount);
            
        } catch (Exception e) {
            // This log will still be committed
            logTransactionRequiresNew("Transfer failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Demonstrates NESTED allowing partial rollback
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void transferWithNestedSavepoint(String fromAccount, String toAccount, 
                                            BigDecimal amount, boolean failSecondUpdate) {
        log.info("Starting transfer with NESTED savepoint");
        
        // First update in outer transaction
        updateAccountRequired(fromAccount, amount.negate());
        
        try {
            // Second update in nested transaction (savepoint)
            if (failSecondUpdate) {
                throw new RuntimeException("Simulated failure in nested transaction");
            }
            updateAccountNested(toAccount, amount);
        } catch (Exception e) {
            log.warn("Nested transaction failed, but outer transaction can continue: {}", e.getMessage());
            // Nested transaction rolls back, but outer can continue
        }
    }

    /**
     * Demonstrates calling MANDATORY - will fail if called without transaction
     */
    public void callMandatoryWithoutTransaction(String accountNumber, BigDecimal amount) {
        // This will throw IllegalTransactionStateException
        updateAccountMandatory(accountNumber, amount);
    }

    /**
     * Demonstrates calling NEVER - will fail if called with transaction
     */
    @Transactional
    public void callNeverWithTransaction(String message) {
        // This will throw IllegalTransactionStateException
        performNeverOperation(message);
    }
}
