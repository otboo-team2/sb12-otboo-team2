package com.otboo.clothes.repository;

import com.otboo.clothes.entity.ClothesAttributeDefinition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefinitionRepository
        extends JpaRepository<ClothesAttributeDefinition, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID definitionId);
}
