package com.example.meetingroom.controller;

import com.example.meetingroom.dto.LoginRequest;
import com.example.meetingroom.dto.LoginResponse;
import com.example.meetingroom.entity.User;
import com.example.meetingroom.repository.UserRepository;
import com.example.meetingroom.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /api/auth/login
 * Body: { "email": "...", "password": "..." }
 * Response: { "token": "...", "userId": 1, "username": "...", "role": "USER" }
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Email 或密碼錯誤"));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return ResponseEntity.ok(new LoginResponse(token, user.getId(), user.getUsername(), user.getRole()));
    }
}
