package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesAttributeDefCreateRequest;
import com.otboo.clothes.dto.ClothesAttributeDefDto;
import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/attribute-defs")
public class ClothesAttributeDefinitionController {

    private final ClothesAttributeDefinitionService definitionService;

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
