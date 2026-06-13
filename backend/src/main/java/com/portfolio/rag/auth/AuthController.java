package com.portfolio.rag.auth;

import com.portfolio.rag.auth.dto.LoginRequest;
import com.portfolio.rag.auth.dto.LoginResponse;
import com.portfolio.rag.auth.dto.LogoutRequest;
import com.portfolio.rag.auth.dto.RefreshRequest;
import com.portfolio.rag.auth.dto.RegisterRequest;
import com.portfolio.rag.auth.dto.RegisterResponse;
import com.portfolio.rag.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(SecurityUtils.getCurrentUserId(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
