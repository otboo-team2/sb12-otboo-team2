package com.otboo.clothes.repository;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {

    Optional<Clothes> findByIdAndOwnerId(UUID clothesId, UUID ownerId);

    @Query("""
            select clothes
            from Clothes clothes
            where clothes.ownerId = :ownerId
              and (:typeEqual is null or clothes.type = :typeEqual)
              and (:favorite is null or clothes.favorite = :favorite)
              and (:cursor is null or clothes.id < :cursor)
            order by clothes.id desc
            """)
    List<Clothes> findAfterIdDescending(
            @Param("ownerId") UUID ownerId,
            @Param("typeEqual") ClothesType typeEqual,
            @Param("favorite") Boolean favorite,
            @Param("cursor") UUID cursor,
            Pageable pageable
    );

    @Query("""
            select count(clothes)
            from Clothes clothes
            where clothes.ownerId = :ownerId
              and (:typeEqual is null or clothes.type = :typeEqual)
              and (:favorite is null or clothes.favorite = :favorite)
            """)
    long countByOwnerIdAndTypeAndFavorite(
            @Param("ownerId") UUID ownerId,
            @Param("typeEqual") ClothesType typeEqual,
            @Param("favorite") Boolean favorite
    );
}
