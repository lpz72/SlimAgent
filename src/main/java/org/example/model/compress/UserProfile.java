package org.example.model.compress;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户结构化信息
 */
@Data
public class UserProfile {
    private String gender;
    private Integer age;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal targetWeightKg;
    private String activityLevel;
    private String dietPreference;
    private String healthNotes;
    private String profileSummary;
}