package org.ozonLabel.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.user.dto.PremiumRequestDto;
import org.ozonLabel.user.dto.UpdateOzonCredentialsDto;
import org.ozonLabel.user.dto.UpdateProfileDto;
import org.ozonLabel.user.dto.UserResponseDto;
import org.ozonLabel.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMe(Authentication auth) {
        String email = auth.getName();
        UserResponseDto dto = userService.getCurrentUser(email);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> updateProfile(
            Authentication auth,
            @Valid @RequestBody UpdateProfileDto dto) {

        String email = auth.getName();
        UserResponseDto updated = userService.updateProfile(email, dto);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/me/ozon-credentials")
    public ResponseEntity<UserResponseDto> updateOzonCredentials(
            Authentication auth,
            @Valid @RequestBody UpdateOzonCredentialsDto dto) {

        String email = auth.getName();
        UserResponseDto updated = userService.updateOzonCredentials(email, dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/request-premium")
    public ResponseEntity<ApiResponse> requestPremium(
            Authentication auth,
            @Valid @RequestBody PremiumRequestDto dto) {

        String requesterEmail = auth.getName();
        userService.requestPremiumAccess(requesterEmail, dto);

        return ResponseEntity.ok(ApiResponse.success("Заявка отправлена! Мы свяжемся с вами в ближайшее времяz"));
    }
}