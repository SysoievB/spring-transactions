# Quick Start Guide

Get up and running with the Spring Transaction Study project in 5 minutes!

## Prerequisites

- Java 17+
- Maven 3.6+
- Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)

## Installation

### Step 1: Clone or Extract
```bash
cd spring-transaction-study
```

### Step 2: Build
```bash
mvn clean install
```

### Step 3: Run Application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Quick Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Suite
```bash
# Test isolation levels
mvn test -Dtest=IsolationLevelTest

# Test propagation behaviors
mvn test -Dtest=PropagationBehaviorTest

# Test retry mechanisms
mvn test -Dtest=RetryServiceTest
```

## Explore the Code

### 1. Isolation Levels
Open: `src/main/java/com/example/transaction/service/IsolationLevelService.java`

Try changing isolation levels and running tests:
```java
@Transactional(isolation = Isolation.READ_COMMITTED)  // Change this
public Account transfer(...) {
    // Your code
}
```

### 2. Propagation Behaviors
Open: `src/main/java/com/example/transaction/service/PropagationBehaviorService.java`

Experiment with different propagation:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)  // Try different ones
public void method() {
    // Your code
}
```

### 3. Retry Mechanisms
Open: `src/main/java/com/example/transaction/service/RetryService.java`

Test different retry strategies:
```java
@Retryable(
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0)  // Adjust these
)
public void operation() {
    // Your code
}
```

## H2 Console

Access the H2 database console at: http://localhost:8080/h2-console

**Connection Details:**
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (leave empty)

## Common Tasks

### View Transaction Logs
```sql
SELECT * FROM transaction_logs;
```

### View All Accounts
```sql
SELECT * FROM accounts;
```

### Clear Data
```sql
DELETE FROM transaction_logs;
DELETE FROM accounts;
```

## Example Scenarios to Try

### Scenario 1: Dirty Read Test
```java
// In one terminal
mvn test -Dtest=IsolationLevelTest#testReadUncommitted

// Watch the logs to see how READ_UNCOMMITTED allows dirty reads
```

### Scenario 2: Audit Log Independence
```java
// In one terminal
mvn test -Dtest=PropagationBehaviorTest#testRequiresNewAuditPersistence

// Notice how audit logs persist even when main transaction rolls back
```

### Scenario 3: Retry with Exponential Backoff
```java
// In one terminal
mvn test -Dtest=RetryServiceTest#testExponentialBackoffRetry

// Observe the increasing delays between retry attempts
```

## Next Steps

1. **Read Documentation:**
   - [Isolation Levels Guide](docs/ISOLATION_LEVELS.md)
   - [Propagation Guide](docs/PROPAGATION.md)
   - [Spring Retry Guide](docs/SPRING_RETRY.md)

2. **Experiment:**
   - Modify isolation levels and see the effects
   - Create your own service methods with different propagation
   - Add custom retry logic

3. **Add Features:**
   - Implement a new entity (e.g., Customer, Product)
   - Add more complex transaction scenarios
   - Integrate with real database (PostgreSQL)

## Troubleshooting

### Issue: Tests Failing
**Solution:** Make sure you've built the project first:
```bash
mvn clean install
```

### Issue: Port 8080 Already in Use
**Solution:** Change the port in `application.properties`:
```properties
server.port=8081
```

### Issue: Can't Connect to H2 Console
**Solution:** Make sure the application is running and try:
```
http://localhost:8080/h2-console
```

## Learning Path

### Beginner (Day 1)
1. Run the application
2. Run all tests and observe results
3. Read ISOLATION_LEVELS.md
4. Experiment with one isolation level

### Intermediate (Day 2-3)
1. Read PROPAGATION.md
2. Understand all 7 propagation types
3. Create custom service methods
4. Test concurrent scenarios

### Advanced (Day 4-5)
1. Read SPRING_RETRY.md
2. Implement complex retry scenarios
3. Combine isolation + propagation + retry
4. Optimize transaction performance

## Resources

- **Spring Documentation:** https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- **Spring Retry:** https://github.com/spring-projects/spring-retry
- **Project README:** [README.md](README.md)

## Getting Help

If you encounter issues:
1. Check the logs in console
2. Review the documentation
3. Look at the test examples
4. Debug step-by-step in your IDE

## Quick Reference Card

### Isolation Levels (Performance → Consistency)
```
READ_UNCOMMITTED → READ_COMMITTED → REPEATABLE_READ → SERIALIZABLE
    (Fastest)                                          (Most Consistent)
```

### Propagation Types
```
REQUIRED       → Default, join or create
REQUIRES_NEW   → Always new transaction
SUPPORTS       → Join if exists
NOT_SUPPORTED  → Never transaction
MANDATORY      → Must have transaction
NEVER          → Must not have transaction
NESTED         → Nested with savepoint
```

### Retry Patterns
```
Fixed:        @Backoff(delay = 1000)
Exponential:  @Backoff(delay = 1000, multiplier = 2.0)
With Max:     @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
Random:       @Backoff(delay = 1000, maxDelay = 5000, random = true)
```

## Happy Learning! 🚀

Start with the basics, experiment freely, and gradually increase complexity. The best way to learn is by doing!
