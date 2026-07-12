package io.github.aminedelta.core_banking.repository;

import io.github.aminedelta.core_banking.domain.TransactionHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionHeaderRepository extends JpaRepository<TransactionHeader, UUID> {
}