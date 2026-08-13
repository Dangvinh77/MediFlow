package com.mediflow.pharmacy.infrastructure.persistence.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;
import java.util.UUID;
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
}
