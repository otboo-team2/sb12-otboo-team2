package com.otboo.clothes.repository;

import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeSelectableValueRepository
        extends JpaRepository<ClothesAttributeSelectableValue, UUID> {
}
