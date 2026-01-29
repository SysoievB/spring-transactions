# Transaction Propagation - Deep Dive

## What is Transaction Propagation?

Transaction propagation defines how methods participate in transactions when they're called from within other transactional methods. It answers the question: **"Should this method join the existing transaction or create its own?"**

## Visual Overview

```
Caller Transaction [═══════════════════════════════]
                    ↓
                    Called Method
                    ↓
    REQUIRED:       [joins same transaction]
    REQUIRES_NEW:   [new transaction═════]
    SUPPORTS:       [joins or no transaction]
    NOT_SUPPORTED:  [no transaction]
    MANDATORY:      [must have transaction or ERROR]
    NEVER:          [must NOT have transaction or ERROR]
    NESTED:         [nested savepoint───]
```

## All 7 Propagation Types

### 1. REQUIRED (Default)

**Behavior**: Join existing transaction OR create new one  
**Most Common**: Yes (90% of use cases)

```java
@Transactional(propagation = Propagation.REQUIRED)
public void methodA() {
    // If called with transaction → joins it
    // If called without transaction → creates new one
    methodB(); // B joins A's transaction
}

@Transactional(propagation = Propagation.REQUIRED)
public void methodB() {
    // Joins methodA's transaction
    // Both commit or rollback together
}
```

**Timeline:**
```
Transaction boundary [═══════════════════════════════]
                     ↓
methodA()            [────────────────]
                     ↓
methodB()            [joins same tx   ]
                     ↓
Both commit/rollback [═══════════════════════════════]
```

**Real-world example:**
```java
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);           // Part of transaction
    updateInventory(order.getItems());     // Joins same transaction
    sendNotification(order.getCustomer()); // Joins same transaction
}

@Transactional(propagation = Propagation.REQUIRED)
public void updateInventory(List<Item> items) {
    // If processOrder rolls back, this rolls back too
}
```

**When to use:**
- Default choice for most methods
- Business logic that should be atomic
- Operations that must succeed/fail together

---

### 2. REQUIRES_NEW

**Behavior**: ALWAYS create new transaction, suspend existing  
**Independent**: Commits/rolls back separately

```java
@Transactional
public void methodA() {
    // Transaction A [═══════════════════════════════]
    doSomething();
    
    methodB(); // Creates Transaction B
               // Transaction A suspended
               
    continueWithA();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    // Transaction B [══════]
    // Commits independently
    // Even if A rolls back, B stays committed
}
```

**Timeline:**
```
Transaction A [══════════════════════════════════════]
              ↓           ↓                    ↓
methodA()     [──────────→suspend→────────────────]
                          ↓
Transaction B             [═══════]
                          ↓
methodB()                 [commits independently]
```

**Real-world example - Audit Logging:**
```java
@Transactional
public void transferMoney(String from, String to, BigDecimal amount) {
    try {
        // Main transaction
        Account fromAcc = accountRepo.findById(from);
        Account toAcc = accountRepo.findById(to);
        
        fromAcc.subtract(amount);
        toAcc.add(amount);
        
        accountRepo.save(fromAcc);
        accountRepo.save(toAcc);
        
        // Log success - will commit even if transfer fails later
        auditLog("SUCCESS: Transfer completed");
        
    } catch (Exception e) {
        // Log failure - still commits
        auditLog("FAILURE: " + e.getMessage());
        throw e;
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void auditLog(String message) {
    // This ALWAYS commits, even if parent transaction rolls back
    logRepository.save(new AuditEntry(message));
}
```

**When to use:**
- Audit logging (must persist regardless)
- Sending notifications
- Recording errors/failures
- Any operation that must complete independently

---

### 3. SUPPORTS

**Behavior**: Join if exists, run without transaction otherwise  
**Flexible**: Works both ways

```java
@Transactional
public void withTransaction() {
    // Transaction [═══════════════════]
    methodSupports(); // Joins transaction
}

public void withoutTransaction() {
    // No transaction
    methodSupports(); // Runs without transaction
}

@Transactional(propagation = Propagation.SUPPORTS)
public Data methodSupports() {
    // Adapts to caller's context
    return dataRepository.findAll();
}
```

**Timeline - Scenario 1 (With Transaction):**
```
Transaction [══════════════════════]
            ↓
Caller      [────────]
            ↓
SUPPORTS    [joins   ]
```

**Timeline - Scenario 2 (Without Transaction):**
```
No Transaction
            ↓
Caller      [────────]
            ↓
SUPPORTS    [no tx   ]
```

**Real-world example:**
```java
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public List<Product> searchProducts(String keyword) {
    // If called from transactional method → joins transaction
    // If called from REST controller → no transaction
    // Either way, it works!
    return productRepository.findByNameContaining(keyword);
}

// Called with transaction
@Transactional
public void processOrderWithSearch() {
    List<Product> products = searchProducts("laptop");
    // searchProducts joins this transaction
}

// Called without transaction
@GetMapping("/search")
public ResponseEntity<List<Product>> search(@RequestParam String q) {
    List<Product> products = searchProducts(q);
    // searchProducts runs without transaction
    return ResponseEntity.ok(products);
}
```

**When to use:**
- Read-only operations
- Methods called from both transactional and non-transactional contexts
- Utility methods
- Search/query methods

---

### 4. NOT_SUPPORTED

**Behavior**: ALWAYS run without transaction, suspend if exists

```java
@Transactional
public void methodA() {
    // Transaction A [═══════════════════════════]
    doSomething();
    
    methodB(); // Suspends transaction
               // Runs without transaction
               
    continueWithA(); // Resumes transaction
}

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void methodB() {
    // No transaction
    // Runs even if caller has transaction
}
```

**Timeline:**
```
Transaction [══════════════════════════════════]
            ↓           ↓              ↓
methodA()   [──────────→suspend→──────────────]
                        ↓
NOT_SUPPORTED           [no tx]
```

**Real-world example:**
```java
@Transactional
public void processLargeReport() {
    // Transaction for critical updates
    updateCriticalData();
    
    // Generate report without transaction (long-running)
    generatePdfReport();
    
    // Continue with transaction
    finalizeUpdates();
}

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void generatePdfReport() {
    // Long-running operation
    // Should not hold database transaction
    // No locks on database
    byte[] pdf = reportGenerator.generate();
    fileSystem.save(pdf);
}
```

**When to use:**
- Long-running operations (file I/O, reports)
- External service calls
- Operations that shouldn't hold database connections
- Read-only operations that don't need transaction overhead

---

### 5. MANDATORY

**Behavior**: MUST be called with transaction, throw error otherwise

```java
public void caller() {
    // No transaction
    methodMandatory(); // ❌ IllegalTransactionStateException
}

@Transactional
public void callerWithTx() {
    // Has transaction
    methodMandatory(); // ✓ Works fine
}

@Transactional(propagation = Propagation.MANDATORY)
public void methodMandatory() {
    // Must be called within a transaction
    // Enforces transactional boundary
}
```

**Real-world example:**
```java
@Service
public class OrderService {
    
    @Transactional
    public void placeOrder(Order order) {
        orderRepository.save(order);
        
        // These MUST be part of the same transaction
        reserveInventory(order.getItems());
        processPayment(order.getPayment());
        
        // If any fails, entire order rolls back
    }
    
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveInventory(List<Item> items) {
        // MUST be called within a transaction
        // Prevents accidental standalone calls
        for (Item item : items) {
            inventoryRepository.reserve(item);
        }
    }
    
    @Transactional(propagation = Propagation.MANDATORY)
    public void processPayment(Payment payment) {
        // MUST be part of larger transaction
        paymentRepository.save(payment);
    }
}
```

**When to use:**
- Sub-operations that should never run standalone
- Enforce transactional boundaries
- Prevent accidental non-transactional calls
- Critical operations that must be atomic with caller

---

### 6. NEVER

**Behavior**: MUST NOT have transaction, throw error if exists

```java
@Transactional
public void callerWithTx() {
    methodNever(); // ❌ IllegalTransactionStateException
}

public void caller() {
    methodNever(); // ✓ Works fine
}

@Transactional(propagation = Propagation.NEVER)
public void methodNever() {
    // Must be called without transaction
}
```

**Real-world example:**
```java
@Transactional(propagation = Propagation.NEVER)
public Report generateYearEndReport() {
    // Very long-running operation
    // Must not be in transaction to avoid:
    // - Locking database for extended time
    // - Transaction timeout
    // - Resource exhaustion
    
    List<Transaction> allTransactions = 
        transactionRepository.findAllByYear(year);
    
    return reportBuilder
        .withData(allTransactions)
        .generateComprehensiveReport();
}

@GetMapping("/reports/year-end")
public ResponseEntity<Report> getYearEndReport() {
    // Called without transaction ✓
    Report report = reportService.generateYearEndReport();
    return ResponseEntity.ok(report);
}

@Transactional
public void processWithReport() {
    // This would fail! ❌
    generateYearEndReport(); // IllegalTransactionStateException
}
```

**When to use:**
- Operations that should never be transactional
- Very long-running batch jobs
- Operations that need to span multiple transactions
- Enforce non-transactional execution

---

### 7. NESTED

**Behavior**: Create nested transaction (savepoint) if exists  
**Partial Rollback**: Nested can rollback without affecting outer

```java
@Transactional
public void methodA() {
    // Outer transaction [═══════════════════════════]
    updateCritical(); // Must succeed
    
    try {
        methodB(); // Nested transaction (savepoint)
    } catch (Exception e) {
        // methodB rolled back to savepoint
        // But methodA can continue
    }
    
    finalizeUpdates(); // Still in outer transaction
}

@Transactional(propagation = Propagation.NESTED)
public void methodB() {
    // Nested transaction (savepoint)
    // Can rollback independently
}
```

**Timeline:**
```
Outer Transaction [════════════════════════════════]
                  ↓                        ↓
methodA()         [────────────────────────────────]
                  ↓        ↓        ↓
updateCritical()  [────────]
                           ↓
Nested (Savepoint)         [──SP──]
                           ↓
methodB()                  [fails]
                           ↓
Rollback to SP             [←──]
                                   ↓
finalizeUpdates()                  [─────────]
                                            ↓
Commit Outer                                [COMMIT]
```

**Real-world example:**
```java
@Transactional
public void importCustomers(List<CustomerData> customers) {
    int successCount = 0;
    int failCount = 0;
    
    for (CustomerData data : customers) {
        try {
            // Try to import each customer in nested transaction
            importSingleCustomer(data);
            successCount++;
        } catch (Exception e) {
            // This customer failed, but others can succeed
            log.error("Failed to import customer: {}", data.getEmail(), e);
            failCount++;
        }
    }
    
    // Save summary (always commits if we get here)
    saveSummary(successCount, failCount);
}

@Transactional(propagation = Propagation.NESTED)
public void importSingleCustomer(CustomerData data) {
    // Each customer in its own nested transaction
    // If this fails, only THIS customer is rolled back
    // Other customers are not affected
    
    Customer customer = new Customer(data);
    customerRepository.save(customer);
    
    if (data.hasOrders()) {
        importOrders(customer, data.getOrders());
    }
}
```

**Another example - Optional Features:**
```java
@Transactional
public void createUserAccount(UserData userData) {
    // Critical: User creation (must succeed)
    User user = new User(userData);
    userRepository.save(user);
    
    // Optional: Create profile (can fail)
    try {
        createUserProfile(user, userData);
    } catch (Exception e) {
        log.warn("Profile creation failed, continuing anyway");
    }
    
    // Optional: Send welcome email (can fail)
    try {
        sendWelcomeEmail(user);
    } catch (Exception e) {
        log.warn("Welcome email failed, continuing anyway");
    }
    
    // This still commits the user, even if optional parts failed
}

@Transactional(propagation = Propagation.NESTED)
public void createUserProfile(User user, UserData data) {
    // Optional operation
    // Failure here doesn't affect user creation
    Profile profile = new Profile(user, data);
    profileRepository.save(profile);
}
```

**When to use:**
- Batch processing with partial success
- Optional features that shouldn't break main flow
- Importers/processors where some items can fail
- **Note**: Only works with DataSourceTransactionManager

**Limitations:**
```java
// ❌ NESTED doesn't work with JTA
@Transactional(propagation = Propagation.NESTED)
public void method() {
    // Will throw exception if using JTA transaction manager
}

// ✓ Only works with JDBC DataSourceTransactionManager
```

---

## Propagation Decision Tree

```
Does method need transaction?
│
├─ YES
│  │
│  ├─ Should join caller's transaction?
│  │  │
│  │  ├─ YES → Use REQUIRED (default)
│  │  │
│  │  └─ NO → Should create independent transaction?
│  │     │
│  │     ├─ YES → Use REQUIRES_NEW
│  │     │
│  │     └─ Should allow partial rollback?
│  │        │
│  │        └─ YES → Use NESTED
│  │
│  └─ Must enforce transaction exists?
│     │
│     └─ YES → Use MANDATORY
│
└─ NO
   │
   ├─ Should adapt to caller?
   │  │
   │  └─ YES → Use SUPPORTS
   │
   ├─ Must run without transaction?
   │  │
   │  ├─ Should suspend existing?
   │  │  │
   │  │  └─ YES → Use NOT_SUPPORTED
   │  │
   │  └─ Should forbid transaction?
   │     │
   │     └─ YES → Use NEVER
```

## Propagation Comparison Matrix

| Propagation    | Existing TX      | No Existing TX  | Creates New | Independent | Use Case |
|----------------|------------------|-----------------|-------------|-------------|----------|
| REQUIRED       | Joins            | Creates new     | If needed   | No          | Default  |
| REQUIRES_NEW   | New (suspend)    | Creates new     | Always      | Yes         | Audit log |
| SUPPORTS       | Joins            | No transaction  | Never       | N/A         | Read ops |
| NOT_SUPPORTED  | Suspend          | No transaction  | Never       | N/A         | Long ops |
| MANDATORY      | Joins            | ERROR           | Never       | No          | Sub-ops  |
| NEVER          | ERROR            | No transaction  | Never       | N/A         | Reports  |
| NESTED         | Nested (SP)      | Creates new     | If needed   | Partial     | Batch    |

## Common Patterns

### Pattern 1: Service Layer (REQUIRED)
```java
@Service
public class OrderService {
    
    @Transactional // REQUIRED is default
    public void createOrder(OrderRequest request) {
        Order order = new Order(request);
        orderRepository.save(order);
        inventoryService.reserve(order.getItems());
        paymentService.process(order.getPayment());
    }
}
```

### Pattern 2: Audit Layer (REQUIRES_NEW)
```java
@Service
public class AuditService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String details) {
        auditRepository.save(new AuditEntry(action, details));
    }
}
```

### Pattern 3: Query Layer (SUPPORTS)
```java
@Service
public class ProductService {
    
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<Product> search(String query) {
        return productRepository.findByQuery(query);
    }
}
```

### Pattern 4: Batch Processing (NESTED)
```java
@Service
public class BatchService {
    
    @Transactional
    public BatchResult processItems(List<Item> items) {
        BatchResult result = new BatchResult();
        
        for (Item item : items) {
            try {
                processItem(item);
                result.incrementSuccess();
            } catch (Exception e) {
                result.incrementFailure();
                log.error("Item failed: {}", item, e);
            }
        }
        
        return result;
    }
    
    @Transactional(propagation = Propagation.NESTED)
    private void processItem(Item item) {
        // Each item in its own savepoint
    }
}
```

## Testing Propagation

```java
@Test
void testRequiresNewIndependence() {
    // Outer transaction that will rollback
    transactionTemplate.execute(status -> {
        accountService.withdraw(accountId, 100);
        
        // This should commit independently
        auditService.log("Withdrawal attempted");
        
        status.setRollbackOnly();
        return null;
    });
    
    // Withdrawal should be rolled back
    assertEquals(1000, getBalance(accountId));
    
    // But audit log should exist
    assertEquals(1, auditLogRepository.count());
}
```

## Best Practices

1. **Default to REQUIRED** unless you have a specific reason
2. **Use REQUIRES_NEW** sparingly (performance cost)
3. **MANDATORY enforces** good transaction boundaries
4. **NESTED** is great for batch but check JTA compatibility
5. **Document your choice** when using non-default propagation
6. **Test edge cases** especially for REQUIRES_NEW and NESTED

## Summary

| When you need...                        | Use              |
|-----------------------------------------|------------------|
| Standard business logic                 | REQUIRED         |
| Independent audit logging               | REQUIRES_NEW     |
| Flexible read operations                | SUPPORTS         |
| Long-running non-transactional work     | NOT_SUPPORTED    |
| Enforce transactional context           | MANDATORY        |
| Prevent transactional context           | NEVER            |
| Partial success in batch                | NESTED           |
