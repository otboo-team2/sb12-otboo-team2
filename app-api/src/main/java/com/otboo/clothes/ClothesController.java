package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.dto.ClothesUpdateRequest;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes")
public class ClothesController {

    private final ClothesService clothesService;

    @GetMapping
    public CursorResponse<ClothesDto> findAll(
            @RequestParam UUID ownerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) ClothesType typeEqual
    ) {
        int requestedLimit = limit == null ? CursorRequest.DEFAULT_LIMIT : limit;
        return clothesService.findAll(
                ownerId,
                typeEqual,
                new CursorRequest(cursor, idAfter, requestedLimit, "id", SortDirection.DESCENDING));
    }

    @PatchMapping(value = "/{clothesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> update(
            @PathVariable UUID clothesId,
            @LoginUser AuthPrincipal me,
            @Valid @RequestPart("request") ClothesUpdateRequest request
    ) {
        return ResponseEntity.ok(clothesService.update(me.userId(), clothesId, request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClothesDto> create(
            @LoginUser AuthPrincipal me,
            @Valid @RequestPart("request") ClothesCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clothesService.create(me.userId(), request));
    }
}
