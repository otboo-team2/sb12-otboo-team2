package com.otboo.virtualtryon;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.virtualtryon.dto.VirtualTryOnJobResponse;
import com.otboo.virtualtryon.dto.VirtualTryOnRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
        @RequestPart("request") VirtualTryOnRequest request,
        @RequestPart(value = "modelImage", required = false) MultipartFile modelImage
    ) {
        var job = virtualTryOnService.submit(me.userId(), request, modelImage);
        return ResponseEntity.ok(VirtualTryOnJobResponse.from(job));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<VirtualTryOnJobResponse> getJob(
        @LoginUser AuthPrincipal me,
        @PathVariable UUID jobId
    ) {
        var job = virtualTryOnService.findJob(jobId, me.userId());
        return ResponseEntity.ok(VirtualTryOnJobResponse.from(job));
    }
}
