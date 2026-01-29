package com.springtransactions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.retry.annotation.EnableRetry;

@EnableTransactionManagement
@EnableRetry
@SpringBootApplication
public class SpringTransactionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringTransactionsApplication.class, args);
    }

}
