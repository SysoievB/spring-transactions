# Spring Transaction Study Project

A comprehensive study project demonstrating Spring Data JPA transactions with various isolation levels, propagation behaviors, and Spring Retry integration.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Transaction Isolation Levels](#transaction-isolation-levels)
- [Transaction Propagation](#transaction-propagation)
- [Spring Retry Integration](#spring-retry-integration)
- [Running Tests](#running-tests)
- [Examples](#examples)

## Overview

This project provides practical examples and tests for understanding Spring transaction management, including:

- **All 5 Transaction Isolation Levels**: READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE, DEFAULT
- **All 7 Propagation Behaviors**: REQUIRED, REQUIRES_NEW, SUPPORTS, NOT_SUPPORTED, MANDATORY, NEVER, NESTED
- **Spring Retry Integration**: Fixed backoff, exponential backoff, recovery methods, selective retry
- **Concurrent Transaction Scenarios**: Demonstrating isolation level effects
- **Real-world Use Cases**: Banking transfer scenarios, audit logging, error recovery

## Features

### Transaction Isolation Levels

1. **READ_UNCOMMITTED**: Allows dirty reads (lowest isolation)
2. **READ_COMMITTED**: Prevents dirty reads (default for most DBs)
3. **REPEATABLE_READ**: Prevents non-repeatable reads
4. **SERIALIZABLE**: Complete transaction isolation (highest)
5. **DEFAULT**: Uses database default

### Transaction Propagation

1. **REQUIRED**: Join existing or create new (default)
2. **REQUIRES_NEW**: Always create new, suspend existing
3. **SUPPORTS**: Join if exists, non-transactional otherwise
4. **NOT_SUPPORTED**: Always non-transactional
5. **MANDATORY**: Must have existing transaction
6. **NEVER**: Must not have transaction
7. **NESTED**: Savepoint-based nested transactions

### Spring Retry Features

- Fixed delay retry
- Exponential backoff
- Maximum attempts configuration
- Selective retry by exception type
- Recovery methods for fallback
- Integration with transaction management

## Project Structure

```
spring-transaction-study/
├── src/
│   ├── main/
│   │   ├── java/com/example/transaction/
│   │   │   ├── model/
│   │   │   │   ├── Account.java
│   │   │   │   └── TransactionLog.java
│   │   │   ├── repository/
│   │   │   │   ├── AccountRepository.java
│   │   │   │   └── TransactionLogRepository.java
│   │   │   ├── service/
│   │   │   │   ├── IsolationLevelService.java
│   │   │   │   ├── PropagationBehaviorService.java
│   │   │   │   └── RetryService.java
│   │   │   ├── exception/
│   │   │   │   ├── AccountNotFoundException.java
│   │   │   │   ├── InsufficientFundsException.java
│   │   │   │   └── TransientException.java
│   │   │   └── TransactionStudyApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/example/transaction/
│           ├── isolation/
│           │   └── IsolationLevelTest.java
│           ├── propagation/
│           │   └── PropagationBehaviorTest.java
│           └── retry/
│               └── RetryServiceTest.java
├── pom.xml
└── README.md
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) PostgreSQL for production-like testing

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd spring-transaction-study
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

4. Run tests:
```bash
mvn test
```

### H2 Console

The application runs with H2 in-memory database. Access the H2 console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: jdbc:h2:mem:testdb
- Username: sa
- Password: (leave empty)

## Transaction Isolation Levels

### READ_UNCOMMITTED

**Characteristics:**
- Lowest isolation level
- Allows dirty reads (uncommitted data)
- Highest performance, lowest consistency
- May see data that will be rolled back

**Use Cases:**
- Read-heavy operations where consistency isn't critical
- Reporting where approximate data is acceptable

**Example:**
```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public Account transfer(String from, String to, BigDecimal amount) {
    // Can read uncommitted changes from other transactions
}
```

### READ_COMMITTED

**Characteristics:**
- Only reads committed data
- Prevents dirty reads
- Allows non-repeatable reads (data can change between reads)
- Default for PostgreSQL, Oracle

**Use Cases:**
- Most business applications
- Standard CRUD operations

**Example:**
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public Account transfer(String from, String to, BigDecimal amount) {
    // Only sees committed data
    // Same row might have different values on repeated reads
}
```

### REPEATABLE_READ

**Characteristics:**
- Ensures same data on repeated reads
- Prevents dirty reads and non-repeatable reads
- May still see phantom reads (new rows)
- Default for MySQL

**Use Cases:**
- Financial calculations requiring consistency
- Multi-step operations needing stable data

**Example:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Account transfer(String from, String to, BigDecimal amount) {
    Account account = accountRepo.find(id); // Balance: $1000
    // ... processing ...
    account = accountRepo.find(id); // Still $1000 (repeatable)
}
```

### SERIALIZABLE

**Characteristics:**
- Highest isolation level
- Complete isolation (as if transactions run serially)
- Prevents all concurrency anomalies
- Lowest performance, may cause deadlocks

**Use Cases:**
- Critical financial transactions
- Operations requiring absolute consistency
- Audit trail generation

**Example:**
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public Account transfer(String from, String to, BigDecimal amount) {
    // Complete isolation from other transactions
    // Highest consistency guarantee
}
```

### Isolation Level Comparison

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance |
|-------|------------|---------------------|--------------|-------------|
| READ_UNCOMMITTED | ✓ | ✓ | ✓ | Highest |
| READ_COMMITTED | ✗ | ✓ | ✓ | High |
| REPEATABLE_READ | ✗ | ✗ | ✓ | Medium |
| SERIALIZABLE | ✗ | ✗ | ✗ | Lowest |

## Transaction Propagation

### REQUIRED (Default)

**Behavior:**
- Join existing transaction if present
- Create new transaction if none exists
- Most common propagation

**Use Cases:**
- Standard service methods
- Business logic requiring transactions

**Example:**
```java
@Transactional(propagation = Propagation.REQUIRED)
public void updateAccount(String accountNumber, BigDecimal amount) {
    // Joins existing transaction or creates new one
}
```

### REQUIRES_NEW

**Behavior:**
- Always creates new transaction
- Suspends current transaction if exists
- Independent commit/rollback

**Use Cases:**
- Audit logging (must persist even if main transaction fails)
- Independent operations within larger transaction

**Example:**
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAuditEntry(String message) {
    // This will commit even if caller transaction rolls back
}
```

### SUPPORTS

**Behavior:**
- Join transaction if exists
- Execute non-transactionally if none exists
- Flexible approach

**Use Cases:**
- Read operations
- Methods that work with or without transactions

**Example:**
```java
@Transactional(propagation = Propagation.SUPPORTS)
public Account getAccount(String accountNumber) {
    // Works with or without transaction context
}
```

### NOT_SUPPORTED

**Behavior:**
- Always execute without transaction
- Suspends current transaction if exists

**Use Cases:**
- Operations that shouldn't be transactional
- External API calls

**Example:**
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void sendEmail(String message) {
    // Executes outside transaction context
}
```

### MANDATORY

**Behavior:**
- Must be called within existing transaction
- Throws exception if no transaction exists

**Use Cases:**
- Sub-operations that must be part of larger transaction
- Enforcing transactional boundaries

**Example:**
```java
@Transactional(propagation = Propagation.MANDATORY)
public void updateAccountBalance(String accountNumber, BigDecimal amount) {
    // Must be called from within a transaction
}
```

### NEVER

**Behavior:**
- Must be called without transaction
- Throws exception if transaction exists

**Use Cases:**
- Operations that must not be in transaction
- Non-transactional reporting

**Example:**
```java
@Transactional(propagation = Propagation.NEVER)
public void generateReport() {
    // Must not be in transaction context
}
```

### NESTED

**Behavior:**
- Creates nested transaction (savepoint) if transaction exists
- Behaves like REQUIRED if no transaction exists
- Nested transaction can rollback independently

**Use Cases:**
- Partial rollback scenarios
- Optional operations within transaction

**Example:**
```java
@Transactional(propagation = Propagation.NESTED)
public void updateOptionalData(String data) {
    // Can rollback without affecting outer transaction
}
```

## Spring Retry Integration

### Basic Retry Configuration

```java
@Retryable(
    retryFor = {TransientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
@Transactional
public Account transfer(String from, String to, BigDecimal amount) {
    // Will retry up to 3 times on TransientException
}
```

### Exponential Backoff

```java
@Retryable(
    retryFor = {TransientException.class},
    maxAttempts = 5,
    backoff = @Backoff(
        delay = 1000,
        multiplier = 2.0,
        maxDelay = 30000
    )
)
@Transactional
public Account transfer(String from, String to, BigDecimal amount) {
    // Retries with delays: 1s, 2s, 4s, 8s, 16s
}
```

### Recovery Methods

```java
@Retryable(
    retryFor = {TransientException.class},
    maxAttempts = 3,
    recover = "recoverFromFailure"
)
@Transactional
public Account transfer(String from, String to, BigDecimal amount) {
    // Attempts transfer
}

@Recover
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Account recoverFromFailure(TransientException e, 
                                 String from, String to, BigDecimal amount) {
    // Fallback behavior when all retries fail
}
```

### Selective Retry

```java
@Retryable(
    retryFor = {TransientException.class, RuntimeException.class},
    noRetryFor = {InsufficientFundsException.class},
    maxAttempts = 3
)
@Transactional
public Account transfer(String from, String to, BigDecimal amount) {
    // Retries on transient errors
    // Does not retry on business logic errors
}
```

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=IsolationLevelTest
mvn test -Dtest=PropagationBehaviorTest
mvn test -Dtest=RetryServiceTest
```

### Run with PostgreSQL
```bash
mvn test -Dspring.profiles.active=postgres
```

### Test Coverage

The project includes comprehensive tests for:
- All isolation levels with concurrent scenarios
- All propagation behaviors with edge cases
- Retry mechanisms with various configurations
- Recovery methods and fallback scenarios
- Integration of isolation, propagation, and retry

## Examples

### Example 1: Banking Transfer with Serializable Isolation

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferMoney(String from, String to, BigDecimal amount) {
    Account fromAccount = accountRepository.findByAccountNumber(from)
        .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
    
    Account toAccount = accountRepository.findByAccountNumber(to)
        .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));
    
    if (fromAccount.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException("Insufficient funds");
    }
    
    fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
    toAccount.setBalance(toAccount.getBalance().add(amount));
    
    accountRepository.save(fromAccount);
    accountRepository.save(toAccount);
}
```

### Example 2: Audit Logging with REQUIRES_NEW

```java
@Transactional(propagation = Propagation.REQUIRED)
public void processPayment(PaymentRequest request) {
    try {
        // Process payment
        paymentService.process(request);
        
        // Log success - will commit even if payment processing fails later
        auditService.logSuccess(request);
    } catch (Exception e) {
        // Log failure - will still commit
        auditService.logFailure(request, e);
        throw e;
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logSuccess(PaymentRequest request) {
    auditRepository.save(new AuditLog("SUCCESS", request));
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logFailure(PaymentRequest request, Exception e) {
    auditRepository.save(new AuditLog("FAILURE", request, e));
}
```

### Example 3: Retry with Exponential Backoff

```java
@Retryable(
    retryFor = {TransientException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 2.0)
)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferWithRetry(String from, String to, BigDecimal amount) {
    // Will retry on transient failures
    // Each retry has increasing delay: 1s, 2s, 4s, 8s
    transferMoney(from, to, amount);
}

@Recover
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recoverFromTransfer(TransientException e, 
                               String from, String to, BigDecimal amount) {
    // Fallback: log the failure for manual intervention
    logFailedTransfer(from, to, amount, e.getMessage());
}
```

## Key Learnings

### 1. Isolation Level Selection

- **Use READ_COMMITTED** for most applications (good balance)
- **Use REPEATABLE_READ** for financial calculations
- **Use SERIALIZABLE** only when absolutely necessary (performance cost)
- **Avoid READ_UNCOMMITTED** unless you understand the risks

### 2. Propagation Behavior Selection

- **Use REQUIRED** as default (most common)
- **Use REQUIRES_NEW** for audit logging and independent operations
- **Use MANDATORY** to enforce transactional boundaries
- **Use NESTED** for optional operations that can fail independently

### 3. Retry Best Practices

- Only retry on transient/temporary errors
- Don't retry on business logic errors
- Use exponential backoff for external services
- Always have a recovery/fallback strategy
- Be careful with retry + SERIALIZABLE (can cause more conflicts)

### 4. Common Pitfalls

- **Mixing isolation levels**: Be consistent within a transaction chain
- **Long transactions with high isolation**: Increases deadlock risk
- **Retrying non-idempotent operations**: Can cause duplicate processing
- **No timeout configuration**: Transactions can hang indefinitely

## Performance Considerations

### Isolation Level Performance Impact

From fastest to slowest:
1. READ_UNCOMMITTED (fastest, least consistent)
2. READ_COMMITTED
3. REPEATABLE_READ
4. SERIALIZABLE (slowest, most consistent)

### Optimization Tips

1. Keep transactions short
2. Use appropriate isolation level (don't over-isolate)
3. Consider optimistic locking for read-heavy workloads
4. Use retry judiciously (exponential backoff for external services)
5. Monitor and tune timeout configurations

## Additional Resources

- [Spring Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Retry Documentation](https://github.com/spring-projects/spring-retry)
- [ACID Properties](https://en.wikipedia.org/wiki/ACID)
- [Database Isolation Levels](https://www.postgresql.org/docs/current/transaction-iso.html)

## License

Apache License 2.0

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for improvements.
