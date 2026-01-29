package com.springtransactions.repository;

import com.springtransactions.model.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    
    List<TransactionLog> findByTransactionType(String transactionType);
    
    List<TransactionLog> findByIsolationLevel(String isolationLevel);
    
    List<TransactionLog> findByPropagationBehavior(String propagationBehavior);
}
