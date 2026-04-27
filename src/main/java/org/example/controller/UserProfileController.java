package org.example.controller;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {
    @Autowired
    private AuthService authService;
    @Autowired
    private UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<FatLossUserProfile>> getProfile(HttpSession session) {
        try {
            Long userId = authService.requireUserId(session);
            return ResponseEntity.ok(ApiResponse.success(userProfileService.getByUserId(userId)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FatLossUserProfile>> saveProfile(@RequestBody ProfileRequest request, HttpSession session) {
        try {
            Long userId = authService.requireUserId(session);
            UserProfileService.ProfileRequest serviceRequest = new UserProfileService.ProfileRequest(
                    request.getGender(),
                    request.getAge(),
                    request.getHeightCm(),
                    request.getWeightKg(),
                    request.getTargetWeightKg(),
                    request.getActivityLevel(),
                    request.getDietPreference(),
                    request.getHealthNotes()
            );
            return ResponseEntity.ok(ApiResponse.success(userProfileService.saveProfile(userId, serviceRequest)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @Setter
    @Getter
    public static class ProfileRequest {
        private String gender;
        private Integer age;
        private BigDecimal heightCm;
        private BigDecimal weightKg;
        private BigDecimal targetWeightKg;
        private String activityLevel;
        private String dietPreference;
        private String healthNotes;
    }

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }
    }
}
