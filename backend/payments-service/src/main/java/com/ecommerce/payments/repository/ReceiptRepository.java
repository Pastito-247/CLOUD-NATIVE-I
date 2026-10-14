package com.ecommerce.payments.repository;

import com.ecommerce.payments.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByPaymentId(Long paymentId);

    List<Receipt> findByUserEmail(String userEmail);
}
