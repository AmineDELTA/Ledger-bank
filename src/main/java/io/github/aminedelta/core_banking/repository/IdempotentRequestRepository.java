package io.github.aminedelta.core_banking.repository;
import io.github.aminedelta.core_banking.domain.IdempotentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, String> {
    
}