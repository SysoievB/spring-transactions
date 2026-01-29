# Transaction Isolation Levels - Deep Dive

## Understanding Isolation Levels

Transaction isolation levels define how changes made by one transaction are visible to other concurrent transactions. They balance between **consistency** (data correctness) and **concurrency** (performance).

## The Three Read Phenomena

### 1. Dirty Read
Reading **uncommitted** data from another transaction.

```
Time | Transaction A          | Transaction B
-----|------------------------|------------------
T1   | BEGIN                  |
T2   |                        | BEGIN
T3   | UPDATE balance = 900   |
T4   |                        | SELECT balance → 900 (DIRTY READ!)
T5   | ROLLBACK               |
T6   |                        | Uses wrong value!
```

**Problem**: Transaction B reads value that was never committed.

### 2. Non-Repeatable Read
Reading the **same row twice** and getting **different values**.

```
Time | Transaction A          | Transaction B
-----|------------------------|------------------
T1   | BEGIN                  |
T2   | SELECT balance → 1000  |
T3   |                        | BEGIN
T4   |                        | UPDATE balance = 900
T5   |                        | COMMIT
T6   | SELECT balance → 900   | (NON-REPEATABLE READ!)
```

**Problem**: Same SELECT gives different results within one transaction.

### 3. Phantom Read
Executing the **same query twice** and getting **different number of rows**.

```
Time | Transaction A              | Transaction B
-----|----------------------------|------------------
T1   | BEGIN                      |
T2   | SELECT * WHERE balance>500 |
T3   | → Returns 10 rows          |
T4   |                            | BEGIN
T5   |                            | INSERT (balance=1000)
T6   |                            | COMMIT
T7   | SELECT * WHERE balance>500 |
T8   | → Returns 11 rows          | (PHANTOM READ!)
```

**Problem**: New rows appear ("phantom") in repeated query.

## Isolation Levels Explained

### READ_UNCOMMITTED (Level 0)

**Allows**: Dirty reads, non-repeatable reads, phantom reads  
**Performance**: Highest  
**Consistency**: Lowest

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public void riskyRead() {
    // Can see uncommitted changes from other transactions
    // Fastest but dangerous - may read data that will be rolled back
}
```

**Real-world scenario:**
```
Banking System - WRONG USAGE:
- Transaction A: Transferring $1000 (not committed)
- Transaction B: Reads account balance with uncommitted $1000
- Transaction A: Fails and rolls back
- Transaction B: Made decisions based on money that never existed!
```

**When to use:**
- Never for financial data
- Maybe for approximate analytics/reporting
- When data consistency doesn't matter

---

### READ_COMMITTED (Level 1)

**Allows**: Non-repeatable reads, phantom reads  
**Prevents**: Dirty reads  
**Performance**: High  
**Consistency**: Medium

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void safeRead() {
    // Only reads committed data
    // But data can change between reads
}
```

**Real-world scenario:**
```
E-commerce - Cart Total Calculation:
Time | Transaction A (Calculate Total) | Transaction B (Update Price)
-----|----------------------------------|---------------------------
T1   | BEGIN                            |
T2   | Item A: $10                      |
T3   | Item B: $20                      |
T4   |                                  | BEGIN
T5   |                                  | UPDATE Item A price = $15
T6   |                                  | COMMIT
T7   | Re-read Item A: $15              | 
T8   | Total: $35 (inconsistent!)       |
```

**When to use:**
- Most web applications
- CRUD operations
- When some inconsistency is acceptable
- **Default for PostgreSQL, Oracle**

---

### REPEATABLE_READ (Level 2)

**Allows**: Phantom reads  
**Prevents**: Dirty reads, non-repeatable reads  
**Performance**: Medium  
**Consistency**: High

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void consistentRead() {
    Account acc = repository.find(1); // balance = $1000
    // ... complex calculation ...
    acc = repository.find(1); // still $1000 (repeatable!)
    // Data doesn't change during transaction
}
```

**Real-world scenario:**
```
Financial Report Generation:
Time | Transaction A (Generate Report) | Transaction B (Transfers)
-----|----------------------------------|------------------------
T1   | BEGIN (REPEATABLE_READ)          |
T2   | Read Account 1: $1000            |
T3   |                                  | BEGIN
T4   |                                  | UPDATE Account 1 = $500
T5   |                                  | COMMIT
T6   | Read Account 1: $1000            | (Still sees $1000!)
T7   | Calculate interest...            |
T8   | COMMIT                           |
```

**When to use:**
- Financial calculations
- Multi-step operations needing consistent data
- Reports requiring snapshot consistency
- **Default for MySQL**

---

### SERIALIZABLE (Level 3)

**Allows**: Nothing  
**Prevents**: Dirty reads, non-repeatable reads, phantom reads  
**Performance**: Lowest  
**Consistency**: Highest

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void fullIsolation() {
    // Complete isolation - as if transactions run one at a time
    // Highest consistency, lowest performance
}
```

**Real-world scenario:**
```
Money Transfer - Maximum Safety:
Time | Transaction A          | Transaction B
-----|------------------------|------------------
T1   | BEGIN (SERIALIZABLE)   |
T2   | Read Account A         |
T3   |                        | BEGIN (SERIALIZABLE)
T4   |                        | Read Account A → BLOCKED!
T5   | Update Account A       |
T6   | Update Account B       |
T7   | COMMIT                 |
T8   |                        | Now can proceed
```

**When to use:**
- Critical financial transactions
- Inventory management (prevent overselling)
- Audit trails
- When consistency is more important than performance

---

## Comparison Matrix

| Level              | Dirty Read | Non-Repeatable | Phantom | Locks | Performance | Use Case |
|--------------------|------------|----------------|---------|-------|-------------|----------|
| READ_UNCOMMITTED   | ✓ Yes      | ✓ Yes          | ✓ Yes   | None  | ★★★★★       | Analytics (risky) |
| READ_COMMITTED     | ✗ No       | ✓ Yes          | ✓ Yes   | Read  | ★★★★☆       | Web apps |
| REPEATABLE_READ    | ✗ No       | ✗ No           | ✓ Yes   | Read  | ★★★☆☆       | Reports |
| SERIALIZABLE       | ✗ No       | ✗ No           | ✗ No    | All   | ★☆☆☆☆       | Banking |

## Database Defaults

| Database   | Default Isolation Level |
|------------|------------------------|
| PostgreSQL | READ_COMMITTED         |
| MySQL      | REPEATABLE_READ        |
| Oracle     | READ_COMMITTED         |
| SQL Server | READ_COMMITTED         |
| H2         | READ_COMMITTED         |

## Implementation in Spring

### Method Level
```java
@Service
public class TransferService {
    
    // Standard web operation
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processOrder(Order order) { }
    
    // Financial calculation
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BigDecimal calculateInterest(Account account) { }
    
    // Critical transfer
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transferMoney(String from, String to, BigDecimal amount) { }
}
```

### With Timeout and Read-Only
```java
@Transactional(
    isolation = Isolation.REPEATABLE_READ,
    timeout = 30,
    readOnly = true
)
public Report generateReport() {
    // Consistent snapshot with timeout
}
```

## Common Mistakes

### ❌ Wrong: Mixing Isolation Levels
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void outer() {
    // Some logic
    inner(); // Uses SERIALIZABLE - CONFLICT!
}

@Transactional(isolation = Isolation.SERIALIZABLE)
public void inner() {
    // Different isolation level causes issues
}
```

### ✅ Correct: Consistent Isolation
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void outer() {
    inner(); // Same isolation level
}

@Transactional(propagation = Propagation.REQUIRED) // Joins outer transaction
public void inner() {
    // Uses same isolation level as outer
}
```

### ❌ Wrong: Over-isolation
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public List<Product> searchProducts(String keyword) {
    // SERIALIZABLE is overkill for simple search!
    return productRepository.findByName(keyword);
}
```

### ✅ Correct: Appropriate Isolation
```java
@Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
public List<Product> searchProducts(String keyword) {
    // READ_COMMITTED is sufficient
    return productRepository.findByName(keyword);
}
```

## Testing Isolation Levels

### Test Dirty Read Prevention
```java
@Test
void testDirtyReadPrevention() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    
    // Transaction 1: Updates but doesn't commit
    executor.submit(() -> {
        transactionTemplate.execute(status -> {
            account.setBalance(500);
            accountRepo.save(account);
            latch.countDown();
            Thread.sleep(2000); // Hold transaction
            status.setRollbackOnly();
            return null;
        });
    });
    
    // Transaction 2: Tries to read
    executor.submit(() -> {
        Thread.sleep(500); // Let T1 start first
        Account read = accountService.getWithReadCommitted(id);
        // Should see original value (1000), not uncommitted (500)
        assertEquals(1000, read.getBalance());
        latch.countDown();
    });
    
    latch.await();
}
```

## Performance Impact

### Benchmark Results (Relative)
```
Operation: 1000 concurrent reads

READ_UNCOMMITTED:   ~100ms  (baseline)
READ_COMMITTED:     ~120ms  (+20%)
REPEATABLE_READ:    ~180ms  (+80%)
SERIALIZABLE:       ~450ms  (+350%)
```

### Deadlock Risk
```
Risk increases with isolation level:

READ_UNCOMMITTED:   ○○○○○ (Very Low)
READ_COMMITTED:     ●○○○○ (Low)
REPEATABLE_READ:    ●●●○○ (Medium)
SERIALIZABLE:       ●●●●● (High)
```

## Best Practices

1. **Start with READ_COMMITTED** for most operations
2. **Use REPEATABLE_READ** for calculations requiring consistency
3. **Reserve SERIALIZABLE** for critical operations only
4. **Always set timeout** to prevent hanging transactions
5. **Use readOnly=true** for read operations (optimization)
6. **Monitor deadlocks** and adjust isolation if needed
7. **Test concurrent scenarios** to verify isolation behavior

## Summary

Choose your isolation level based on:

- **Data Criticality**: How important is consistency?
- **Concurrency Level**: How many concurrent users?
- **Performance Requirements**: How fast must it be?
- **Business Rules**: What consistency level does business require?

**General Rule**: Use the **lowest isolation level** that meets your consistency requirements.
