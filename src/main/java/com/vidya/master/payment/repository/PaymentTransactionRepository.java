package com.vidya.master.payment.repository;

import com.vidya.master.payment.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    Optional<PaymentTransaction> findByProviderOrderId(String providerOrderId);

    Page<PaymentTransaction> findBySchoolId(UUID schoolId, Pageable pageable);

    List<PaymentTransaction> findByOnboardingId(UUID onboardingId);

    List<PaymentTransaction> findBySubscriptionId(UUID subscriptionId);

    List<PaymentTransaction> findByStatus(com.vidya.master.payment.entity.PaymentStatus status);

    long countByStatus(com.vidya.master.payment.entity.PaymentStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = :status")
    java.math.BigDecimal sumAmountByStatus(@org.springframework.data.repository.query.Param("status") com.vidya.master.payment.entity.PaymentStatus status);
}
