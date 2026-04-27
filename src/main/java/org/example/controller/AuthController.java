package org.example.controller;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import org.example.model.dto.ApiResponse;
import org.example.model.entity.FatLossUserProfile;
import org.example.service.AuthService;
import org.example.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private UserProfileService userProfileService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthService.AuthUser>> register(@RequestBody AuthRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.register(request.getUsername(), request.getPassword(), request.getNickname())));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthService.AuthUser>> login(@RequestBody AuthRequest request, HttpSession session) {
        try {
            return ResponseEntity.ok(ApiResponse.success(authService.login(request.getUsername(), request.getPassword(), session)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(ApiResponse.success("已退出登录"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> me(HttpSession session) {
        try {
            AuthService.AuthUser user = authService.currentUser(session);
            FatLossUserProfile profile = userProfileService.getByUserId(user.id());
            return ResponseEntity.ok(ApiResponse.success(new AuthMeResponse(user, profile)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @Setter
    @Getter
    public static class AuthRequest {
        private String username;
        private String password;
        private String nickname;
    }

    public record AuthMeResponse(AuthService.AuthUser user, FatLossUserProfile profile) {
    }


}
