package com.otboo.clothes.repository;

import com.otboo.clothes.entity.ClothesAttributeValue;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesAttributeValueRepository
        extends JpaRepository<ClothesAttributeValue, UUID> {

    @Query("""
            select value
            from ClothesAttributeValue value
            where value.clothes.id in :clothesIds
            order by value.clothes.id asc, value.createdAt asc, value.id asc
            """)
    List<ClothesAttributeValue> findAllByClothesIdIn(
            @Param("clothesIds") Collection<UUID> clothesIds
    );
}
