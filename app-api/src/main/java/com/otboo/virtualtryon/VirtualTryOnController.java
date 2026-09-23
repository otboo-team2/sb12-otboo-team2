package com.otboo.virtualtryon;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.virtualtryon.dto.VirtualTryOnJobResponse;
import com.otboo.virtualtryon.dto.VirtualTryOnRequest;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fittings")
@RequiredArgsConstructor
public class VirtualTryOnController {

    private final VirtualTryOnService virtualTryOnService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<VirtualTryOnJobResponse> submit(
        @LoginUser AuthPrincipal me,
        @Valid @RequestPart("request") VirtualTryOnRequest request,
        @RequestPart(value = "modelImage", required = false) MultipartFile modelImage
    ) {
        var response = virtualTryOnService.submitAndRespond(me.userId(), request, modelImage);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<VirtualTryOnJobResponse> getJob(
        @LoginUser AuthPrincipal me,
        @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(virtualTryOnService.getJobResponse(jobId, me.userId()));
    }
}
