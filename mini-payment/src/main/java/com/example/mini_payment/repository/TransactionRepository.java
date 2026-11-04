package com.example.mini_payment.repository;

import com.example.mini_payment.entity.Transaction;
import com.example.mini_payment.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.targetAccountNumber = :accNum AND t.status = :status")
    BigDecimal sumAmountByTargetAccountNumberAndStatus(@Param("accNum") String accNum, @Param("status") TransactionStatus status);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.sourceAccountNumber = :accNum AND t.status = :status")
    BigDecimal sumAmountBySourceAccountNumberAndStatus(@Param("accNum") String accNum, @Param("status") TransactionStatus status);
}