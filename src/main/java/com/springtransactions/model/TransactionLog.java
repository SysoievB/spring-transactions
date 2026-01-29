package com.springtransactions.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String isolationLevel;

    @Column
    private String propagationBehavior;

    @Column
    private Integer retryAttempt;

    public TransactionLog(String transactionType, String description, 
                         String isolationLevel, String propagationBehavior) {
        this.transactionType = transactionType;
        this.description = description;
        this.timestamp = LocalDateTime.now();
        this.isolationLevel = isolationLevel;
        this.propagationBehavior = propagationBehavior;
        this.retryAttempt = 0;
    }
}
