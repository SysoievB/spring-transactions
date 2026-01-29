# Spring Retry - Complete Guide

## What is Spring Retry?

Spring Retry provides declarative retry support for Spring applications. It allows you to automatically retry failed operations with configurable retry logic, backoff policies, and recovery mechanisms.

## Why Use Retry?

```
Problem: Transient failures in distributed systems
- Network timeouts
- Database deadlocks
- Service temporarily unavailable
- Connection pool exhausted
- Serialization conflicts

Solution: Retry with intelligent backoff
```

## Setup

### Dependencies
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### Enable Retry
```java
@SpringBootApplication
@EnableRetry
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Basic Retry

### Simple Retry
```java
@Service
public class PaymentService {
    
    @Retryable(maxAttempts = 3)
    public void processPayment(Payment payment) {
        // Will retry up to 3 times if any exception is thrown
        paymentGateway.charge(payment);
    }
}
```

**Timeline:**
```
Attempt 1: processPayment() → Exception → Retry
Attempt 2: processPayment() → Exception → Retry
Attempt 3: processPayment() → Exception → Give up
```

### Retry Specific Exceptions
```java
@Retryable(
    retryFor = {TimeoutException.class, IOException.class},
    maxAttempts = 3
)
public void callExternalService() {
    // Only retries on TimeoutException or IOException
    externalService.call();
}
```

### Exclude Exceptions from Retry
```java
@Retryable(
    retryFor = {Exception.class},
    noRetryFor = {BusinessException.class, ValidationException.class},
    maxAttempts = 3
)
public void processOrder(Order order) {
    // Retries on any Exception
    // EXCEPT BusinessException and ValidationException
    // (those are non-retryable business errors)
}
```

## Backoff Strategies

### 1. Fixed Delay
```java
@Retryable(
    maxAttempts = 4,
    backoff = @Backoff(delay = 1000)
)
public void operation() {
    // Retry with 1 second delay between attempts
    // Timeline: 0s → 1s → 2s → 3s
}
```

**Timeline:**
```
Attempt 1: [Execute] → Fail
           Wait 1000ms
Attempt 2: [Execute] → Fail
           Wait 1000ms
Attempt 3: [Execute] → Fail
           Wait 1000ms
Attempt 4: [Execute]
```

### 2. Exponential Backoff
```java
@Retryable(
    maxAttempts = 5,
    backoff = @Backoff(
        delay = 1000,
        multiplier = 2.0
    )
)
public void operationWithExponential() {
    // Retry with exponentially increasing delays
    // Timeline: 0s → 1s → 3s → 7s → 15s
    // Delays: 1s, 2s, 4s, 8s
}
```

**Timeline:**
```
Attempt 1: [Execute] → Fail
           Wait 1000ms (1s)
Attempt 2: [Execute] → Fail
           Wait 2000ms (1s * 2)
Attempt 3: [Execute] → Fail
           Wait 4000ms (2s * 2)
Attempt 4: [Execute] → Fail
           Wait 8000ms (4s * 2)
Attempt 5: [Execute]
```

### 3. Exponential with Max Delay
```java
@Retryable(
    maxAttempts = 10,
    backoff = @Backoff(
        delay = 1000,
        multiplier = 2.0,
        maxDelay = 10000
    )
)
public void operationWithMaxDelay() {
    // Exponential backoff capped at 10 seconds
    // Timeline: 1s, 2s, 4s, 8s, 10s, 10s, 10s...
}
```

### 4. Random Delay
```java
@Retryable(
    maxAttempts = 5,
    backoff = @Backoff(
        delay = 1000,
        maxDelay = 5000,
        random = true
    )
)
public void operationWithJitter() {
    // Random delay between 1s and 5s
    // Prevents thundering herd problem
}
```

## Recovery Methods

### Basic Recovery
```java
@Service
public class TransferService {
    
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 3,
        recover = "recoverFromFailure"
    )
    public Result transfer(String from, String to, BigDecimal amount) {
        // Try to transfer money
        return transferMoney(from, to, amount);
    }
    
    @Recover
    public Result recoverFromFailure(TransientException e, 
                                     String from, String to, BigDecimal amount) {
        // Called after all retries exhausted
        log.error("Transfer failed after retries: {} -> {}", from, to, e);
        
        // Return fallback result
        return Result.failed("Transfer failed, please try again later");
    }
}
```

**Recovery Method Rules:**
1. Must be annotated with `@Recover`
2. **First parameter** must be the exception type
3. **Remaining parameters** must match the original method
4. **Return type** must match the original method

### Multiple Recovery Methods
```java
@Service
public class ServiceWithMultipleRecovery {
    
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3
    )
    public String processData(String data) {
        // Try processing
        return process(data);
    }
    
    // Specific recovery for TimeoutException
    @Recover
    public String recoverFromTimeout(TimeoutException e, String data) {
        log.error("Timeout processing: {}", data);
        return "TIMEOUT";
    }
    
    // Specific recovery for IOException
    @Recover
    public String recoverFromIO(IOException e, String data) {
        log.error("IO error processing: {}", data);
        return "IO_ERROR";
    }
    
    // General recovery for any other Exception
    @Recover
    public String recoverFromError(Exception e, String data) {
        log.error("Error processing: {}", data);
        return "ERROR";
    }
}
```

## Integration with Transactions

### Pattern 1: Retry Entire Transaction
```java
@Service
public class OrderService {
    
    @Retryable(
        retryFor = {TransientException.class, DeadlockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500)
    )
    @Transactional
    public void processOrder(Order order) {
        // Entire transaction is retried on failure
        orderRepository.save(order);
        inventoryService.reserve(order.getItems());
        paymentService.charge(order);
    }
}
```

**Timeline (with failure):**
```
Try 1:
[Transaction Start]
  → Save order ✓
  → Reserve inventory ✓
  → Charge payment ✗ (DeadlockException)
[Transaction Rollback]
Wait 500ms

Try 2:
[Transaction Start]
  → Save order ✓
  → Reserve inventory ✓
  → Charge payment ✓
[Transaction Commit]
```

### Pattern 2: Retry with REQUIRES_NEW
```java
@Service
public class PaymentService {
    
    @Retryable(
        retryFor = {TransientException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPayment(Payment payment) {
        // Each retry is in its own independent transaction
        paymentRepository.save(payment);
        chargePaymentGateway(payment);
    }
}
```

### Pattern 3: Retry with SERIALIZABLE
```java
@Service
public class InventoryService {
    
    @Retryable(
        retryFor = {SerializationException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 100, multiplier = 1.5)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void reserveItem(String itemId, int quantity) {
        // SERIALIZABLE may cause serialization failures
        // Retry automatically handles these conflicts
        Item item = itemRepository.findById(itemId);
        
        if (item.getQuantity() < quantity) {
            throw new InsufficientStockException();
        }
        
        item.setQuantity(item.getQuantity() - quantity);
        itemRepository.save(item);
    }
}
```

## Real-World Examples

### Example 1: External API Call with Retry
```java
@Service
@Slf4j
public class PaymentGatewayService {
    
    private final RestTemplate restTemplate;
    private final AtomicInteger attemptCounter = new AtomicInteger(0);
    
    @Retryable(
        retryFor = {RestClientException.class, TimeoutException.class},
        noRetryFor = {HttpClientErrorException.class}, // Don't retry 4xx errors
        maxAttempts = 5,
        backoff = @Backoff(
            delay = 2000,
            multiplier = 2.0,
            maxDelay = 30000
        ),
        recover = "recoverPayment"
    )
    public PaymentResponse charge(PaymentRequest request) {
        int attempt = attemptCounter.incrementAndGet();
        log.info("Charging payment - attempt #{}", attempt);
        
        try {
            // Call external payment gateway
            ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "https://payment-gateway.com/charge",
                request,
                PaymentResponse.class
            );
            
            attemptCounter.set(0); // Reset on success
            return response.getBody();
            
        } catch (Exception e) {
            log.warn("Payment attempt #{} failed: {}", attempt, e.getMessage());
            throw e;
        }
    }
    
    @Recover
    public PaymentResponse recoverPayment(Exception e, PaymentRequest request) {
        log.error("Payment failed after all retries. Request: {}", request, e);
        
        // Save to dead letter queue for manual processing
        deadLetterQueue.send(request);
        
        attemptCounter.set(0); // Reset counter
        
        return PaymentResponse.failed(
            "Payment processing failed. Transaction queued for manual review."
        );
    }
}
```

**Usage:**
```
Timeline for failed API:
0s:   Attempt 1 → Timeout
      Wait 2s
2s:   Attempt 2 → Timeout
      Wait 4s (2s * 2)
6s:   Attempt 3 → Timeout
      Wait 8s (4s * 2)
14s:  Attempt 4 → Timeout
      Wait 16s (8s * 2)
30s:  Attempt 5 → Timeout
      → Recovery method called
      → Save to dead letter queue
```

### Example 2: Database Operation with Optimistic Locking
```java
@Service
public class ProductService {
    
    @Retryable(
        retryFor = {OptimisticLockingFailureException.class},
        maxAttempts = 10,
        backoff = @Backoff(
            delay = 50,
            multiplier = 1.2,
            maxDelay = 500
        )
    )
    @Transactional
    public Product updateStock(Long productId, int quantityChange) {
        // Optimistic locking may fail on concurrent updates
        // Retry automatically handles version conflicts
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        
        int newQuantity = product.getQuantity() + quantityChange;
        
        if (newQuantity < 0) {
            throw new InsufficientStockException(
                "Cannot reduce stock below 0"
            );
        }
        
        product.setQuantity(newQuantity);
        
        // Save may throw OptimisticLockingFailureException
        // if another transaction updated the product
        return productRepository.save(product);
    }
}
```

### Example 3: Batch Processing with Partial Retry
```java
@Service
public class DataImportService {
    
    @Transactional
    public ImportResult importRecords(List<Record> records) {
        ImportResult result = new ImportResult();
        
        for (Record record : records) {
            try {
                importSingleRecord(record);
                result.incrementSuccess();
            } catch (Exception e) {
                result.incrementFailure();
                log.error("Failed to import record: {}", record.getId(), e);
            }
        }
        
        return result;
    }
    
    @Retryable(
        retryFor = {TransientException.class, DataIntegrityViolationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500)
    )
    @Transactional(propagation = Propagation.NESTED)
    public void importSingleRecord(Record record) {
        // Each record is retried independently
        // Uses NESTED to allow partial rollback
        
        validateRecord(record);
        
        Entity entity = convertToEntity(record);
        entityRepository.save(entity);
        
        updateStatistics(record);
    }
}
```

### Example 4: Circuit Breaker Pattern
```java
@Service
public class ExternalServiceClient {
    
    private final CircuitBreakerState state = new CircuitBreakerState();
    
    @Retryable(
        retryFor = {ServiceUnavailableException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public Response callService(Request request) {
        if (state.isOpen()) {
            throw new CircuitOpenException("Circuit breaker is open");
        }
        
        try {
            Response response = externalService.call(request);
            state.recordSuccess();
            return response;
            
        } catch (Exception e) {
            state.recordFailure();
            throw new ServiceUnavailableException(e);
        }
    }
    
    @Recover
    public Response recoverFromFailure(Exception e, Request request) {
        state.openCircuit();
        return Response.fallback();
    }
}
```

## Retry Stateful vs Stateless

### Stateless Retry (Default)
```java
@Retryable(maxAttempts = 3)
public void statelessOperation() {
    // Each retry attempt is independent
    // No state maintained between retries
}
```

### Stateful Retry
```java
@Retryable(
    maxAttempts = 3,
    stateful = true, // Maintain state
    include = {DataAccessException.class}
)
public void statefulOperation(String id) {
    // State is maintained across retries
    // Useful for operations that modify state
}
```

## Testing Retry Logic

### Test with Mock Failures
```java
@SpringBootTest
class RetryServiceTest {
    
    @Autowired
    private PaymentService paymentService;
    
    @MockBean
    private PaymentGateway paymentGateway;
    
    @Test
    void testRetrySuccessAfterFailures() {
        // Fail twice, succeed on third attempt
        when(paymentGateway.charge(any()))
            .thenThrow(new TransientException("Timeout"))
            .thenThrow(new TransientException("Timeout"))
            .thenReturn(new PaymentResponse("SUCCESS"));
        
        // Execute
        PaymentResponse response = paymentService.processPayment(payment);
        
        // Verify
        assertEquals("SUCCESS", response.getStatus());
        verify(paymentGateway, times(3)).charge(any());
    }
    
    @Test
    void testRecoveryAfterAllRetries() {
        // Always fail
        when(paymentGateway.charge(any()))
            .thenThrow(new TransientException("Timeout"));
        
        // Execute
        PaymentResponse response = paymentService.processPayment(payment);
        
        // Verify recovery method was called
        assertEquals("FAILED", response.getStatus());
        verify(paymentGateway, times(3)).charge(any());
    }
}
```

### Test Retry Timing
```java
@Test
void testExponentialBackoff() {
    long start = System.currentTimeMillis();
    
    try {
        retryService.operationWithExponential();
    } catch (Exception e) {
        // Expected to fail
    }
    
    long duration = System.currentTimeMillis() - start;
    
    // Should take approximately: 1s + 2s + 4s = 7s
    assertTrue(duration >= 7000);
    assertTrue(duration < 8000);
}
```

## Best Practices

### 1. Choose Appropriate Exceptions
```java
// ✅ Good: Only retry transient errors
@Retryable(
    retryFor = {TimeoutException.class, TransientException.class},
    noRetryFor = {BusinessException.class, ValidationException.class}
)

// ❌ Bad: Retry all exceptions
@Retryable(retryFor = {Exception.class})
```

### 2. Use Exponential Backoff for External Services
```java
// ✅ Good: Exponential backoff with max delay
@Retryable(
    maxAttempts = 5,
    backoff = @Backoff(
        delay = 1000,
        multiplier = 2.0,
        maxDelay = 30000
    )
)

// ❌ Bad: Fixed short delay for external service
@Retryable(
    maxAttempts = 10,
    backoff = @Backoff(delay = 100)
)
```

### 3. Always Provide Recovery
```java
// ✅ Good: Has recovery method
@Retryable(
    maxAttempts = 3,
    recover = "recoverFromFailure"
)
public Result operation() { }

@Recover
public Result recoverFromFailure(Exception e) {
    // Fallback logic
}

// ❌ Bad: No recovery, exception propagates
@Retryable(maxAttempts = 3)
public Result operation() { }
// What happens after all retries fail?
```

### 4. Be Careful with Idempotency
```java
// ✅ Good: Idempotent operation
@Retryable(maxAttempts = 3)
@Transactional
public void updateBalance(String accountId, BigDecimal newBalance) {
    Account account = accountRepo.findById(accountId);
    account.setBalance(newBalance); // Sets to specific value
    accountRepo.save(account);
}

// ❌ Dangerous: Non-idempotent operation
@Retryable(maxAttempts = 3)
@Transactional
public void addToBalance(String accountId, BigDecimal amount) {
    Account account = accountRepo.findById(accountId);
    account.setBalance(account.getBalance().add(amount)); // Adds to current
    accountRepo.save(account);
    // If this partially succeeds, retry will add again!
}
```

### 5. Monitor Retry Metrics
```java
@Service
@Slf4j
public class MonitoredService {
    
    private final MeterRegistry meterRegistry;
    
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3
    )
    public void operation() {
        meterRegistry.counter("operation.attempts").increment();
        
        try {
            // Operation logic
            meterRegistry.counter("operation.success").increment();
        } catch (Exception e) {
            meterRegistry.counter("operation.failure").increment();
            throw e;
        }
    }
    
    @Recover
    public void recover(Exception e) {
        meterRegistry.counter("operation.exhausted").increment();
        log.error("All retries exhausted", e);
    }
}
```

## Common Pitfalls

### Pitfall 1: Self-Invocation
```java
// ❌ Won't work: Self-invocation
@Service
public class MyService {
    
    public void caller() {
        this.retryableMethod(); // Retry won't work!
    }
    
    @Retryable
    public void retryableMethod() {
        // Retry proxy is bypassed
    }
}

// ✅ Works: Call through proxy
@Service
public class MyService {
    
    @Autowired
    private MyService self; // Inject self
    
    public void caller() {
        self.retryableMethod(); // Retry works!
    }
    
    @Retryable
    public void retryableMethod() {
        // Called through proxy
    }
}
```

### Pitfall 2: Retry + Transaction Rollback
```java
// ⚠️ Be careful: Each retry rolls back entire transaction
@Retryable(maxAttempts = 3)
@Transactional
public void operation() {
    // Step 1: Save data
    dataRepository.save(data);
    
    // Step 2: Call external service (fails)
    externalService.call();
    
    // On failure: ALL of Step 1 is rolled back
    // Then entire method retries from scratch
}
```

## Summary

| Feature | Use For | Example |
|---------|---------|---------|
| Basic Retry | Simple retry logic | API calls |
| Fixed Backoff | Consistent delays | Database operations |
| Exponential Backoff | External services | Payment gateways |
| Recovery Methods | Fallback behavior | Dead letter queue |
| Selective Retry | Business vs transient errors | Order processing |
| Transaction Integration | Atomic operations | Money transfers |

**Key Takeaways:**
1. Only retry transient errors
2. Use exponential backoff for external services
3. Always provide recovery methods
4. Ensure operations are idempotent
5. Monitor retry metrics
6. Test retry behavior thoroughly
