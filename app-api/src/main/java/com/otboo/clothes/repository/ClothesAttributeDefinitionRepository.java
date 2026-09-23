package com.otboo.clothes.repository;

import com.otboo.clothes.entity.ClothesAttributeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefinitionRepository
        extends JpaRepository<ClothesAttributeDefinition, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID definitionId);

    @Query("""
            select definition
            from ClothesAttributeDefinition definition
            where (:keywordLike is null
                or lower(definition.name) like lower(concat('%', :keywordLike, '%')))
              and (:cursor is null
                or definition.name > :cursor
                or (definition.name = :cursor and definition.id > :idAfter))
            order by definition.name asc, definition.id asc
            """)
    List<ClothesAttributeDefinition> findAfterNameAscending(
            @Param("cursor") String cursor,
            @Param("idAfter") UUID idAfter,
            @Param("keywordLike") String keywordLike,
            Pageable pageable
    );

    @Query("""
            select definition
            from ClothesAttributeDefinition definition
            where (:keywordLike is null
                or lower(definition.name) like lower(concat('%', :keywordLike, '%')))
              and (:cursor is null
                or definition.name < :cursor
                or (definition.name = :cursor and definition.id < :idAfter))
            order by definition.name desc, definition.id desc
            """)
    List<ClothesAttributeDefinition> findAfterNameDescending(
            @Param("cursor") String cursor,
            @Param("idAfter") UUID idAfter,
            @Param("keywordLike") String keywordLike,
            Pageable pageable
    );

    @Query("""
            select definition
            from ClothesAttributeDefinition definition
            where (:keywordLike is null
                or lower(definition.name) like lower(concat('%', :keywordLike, '%')))
              and (:cursor is null
                or definition.createdAt > :cursor
                or (definition.createdAt = :cursor and definition.id > :idAfter))
            order by definition.createdAt asc, definition.id asc
            """)
    List<ClothesAttributeDefinition> findAfterCreatedAtAscending(
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            @Param("keywordLike") String keywordLike,
            Pageable pageable
    );

    @Query("""
            select definition
            from ClothesAttributeDefinition definition
            where (:keywordLike is null
                or lower(definition.name) like lower(concat('%', :keywordLike, '%')))
              and (:cursor is null
                or definition.createdAt < :cursor
                or (definition.createdAt = :cursor and definition.id < :idAfter))
            order by definition.createdAt desc, definition.id desc
            """)
    List<ClothesAttributeDefinition> findAfterCreatedAtDescending(
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            @Param("keywordLike") String keywordLike,
            Pageable pageable
    );

    @Query("""
            select count(definition)
            from ClothesAttributeDefinition definition
            where (:keywordLike is null
                or lower(definition.name) like lower(concat('%', :keywordLike, '%')))
            """)
    long countByKeywordLike(@Param("keywordLike") String keywordLike);
}
