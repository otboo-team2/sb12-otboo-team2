package com.otboo.clothes.repository;

import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ClothesAttributeSelectableValueRepository
        extends JpaRepository<ClothesAttributeSelectableValue, UUID> {

    @Query("""
            select value
            from ClothesAttributeSelectableValue value
            where value.definition.id in :definitionIds
            order by value.definition.id asc, value.createdAt asc, value.id asc
            """)
    List<ClothesAttributeSelectableValue> findAllByDefinitionIds(
            @Param("definitionIds") Collection<UUID> definitionIds
    );
}
