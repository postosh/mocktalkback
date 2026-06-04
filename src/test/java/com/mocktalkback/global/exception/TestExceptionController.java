package com.mocktalkback.global.exception;

import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Validated
@RestController
class TestExceptionController {

    @PostMapping("/test/validation")
    ApiEnvelope<Void> validate(@Valid @RequestBody TestRequest request) {
        return ApiEnvelope.ok();
    }

    @GetMapping("/test/constraint")
    ApiEnvelope<Void> constraint(@RequestParam("count") @Min(1) int count) {
        return ApiEnvelope.ok();
    }

    @GetMapping("/test/type/{id}")
    ApiEnvelope<Void> type(@PathVariable("id") Long id) {
        return ApiEnvelope.ok();
    }

    @PostMapping("/test/method")
    ApiEnvelope<Void> method() {
        return ApiEnvelope.ok();
    }

    @GetMapping("/test/auth")
    ApiEnvelope<Void> auth() {
        throw new BadCredentialsException("bad credentials");
    }

    @GetMapping("/test/denied")
    ApiEnvelope<Void> denied() {
        throw new AccessDeniedException("Access Denied");
    }

    @GetMapping("/test/illegal")
    ApiEnvelope<Void> illegalArgument() {
        throw new IllegalArgumentException("bad argument");
    }

    @GetMapping("/test/api")
    ApiEnvelope<Void> apiException() {
        throw new ApiException(ErrorCode.COMMON_BAD_REQUEST);
    }

    @GetMapping("/test/runtime")
    ApiEnvelope<Void> runtime() {
        throw new RuntimeException("boom");
    }

    @GetMapping("/test/upload")
    ApiEnvelope<Void> upload() {
        throw new MaxUploadSizeExceededException(50L);
    }

    static class TestRequest {
        @NotBlank
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}