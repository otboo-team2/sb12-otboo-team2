package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesAttributeDefCreateRequest;
import com.otboo.clothes.dto.ClothesAttributeDefDto;
import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/attribute-defs")
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService definitionService;

    @GetMapping
    public CursorResponse<ClothesAttributeDefDto> findAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @RequestParam(required = false, defaultValue = "ASCENDING") SortDirection sortDirection,
            @RequestParam(required = false) String keywordLike
    ) {
        int requestedLimit = limit == null ? CursorRequest.DEFAULT_LIMIT : limit;
        return definitionService.findAll(
                new CursorRequest(cursor, idAfter, requestedLimit, sortBy, sortDirection),
                keywordLike);
    }

    @PostMapping
    public ResponseEntity<ClothesAttributeDefDto> create(
            @Valid @RequestBody ClothesAttributeDefCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(definitionService.create(request));
    }

    @PatchMapping("/{definitionId}")
    public ResponseEntity<ClothesAttributeDefDto> update(
            @PathVariable UUID definitionId,
            @Valid @RequestBody ClothesAttributeDefUpdateRequest request) {
        return ResponseEntity.ok(definitionService.update(definitionId, request));
    }

    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID definitionId) {
        definitionService.delete(definitionId);
        return ResponseEntity.noContent().build();
    }
}
