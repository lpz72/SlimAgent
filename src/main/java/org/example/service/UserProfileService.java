package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.model.entity.FatLossUserProfile;
import org.example.mapper.FatLossUserProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserProfileService {
    @Autowired
    private FatLossUserProfileMapper profileMapper;

    public FatLossUserProfile getByUserId(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<FatLossUserProfile>()
                .eq(FatLossUserProfile::getUserId, userId));
    }

    public FatLossUserProfile saveProfile(Long userId, ProfileRequest request) {
        FatLossUserProfile profile = Optional.ofNullable(getByUserId(userId)).orElseGet(FatLossUserProfile::new);
        boolean isNew = profile.getId() == null;
        profile.setUserId(userId);
        profile.setGender(request.gender());
        profile.setAge(request.age());
        profile.setHeightCm(request.heightCm());
        profile.setWeightKg(request.weightKg());
        profile.setTargetWeightKg(request.targetWeightKg());
        profile.setActivityLevel(request.activityLevel());
        profile.setDietPreference(request.dietPreference());
        profile.setHealthNotes(request.healthNotes());
//        profile.setProfileSummary(buildProfileSummary(profile));
        profile.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            profile.setCreatedAt(LocalDateTime.now());
            profile.setIsDeleted(0);
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return profile;
    }

    public String buildStructuredContext(Long userId) {
        FatLossUserProfile profile = getByUserId(userId);
        if (profile == null) {
            return "当前用户暂未填写结构化减脂资料。请在必要时引导用户补充身高、体重、目标体重、年龄、性别、活动水平、饮食偏好和健康禁忌。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("用户结构化减脂资料：");
        append(builder, "性别", profile.getGender());
        append(builder, "年龄", profile.getAge());
        append(builder, "身高cm", profile.getHeightCm());
        append(builder, "当前体重kg", profile.getWeightKg());
        append(builder, "目标体重kg", profile.getTargetWeightKg());
        append(builder, "活动水平", profile.getActivityLevel());
        append(builder, "饮食偏好", profile.getDietPreference());
        append(builder, "健康备注", profile.getHealthNotes());
        append(builder, "画像摘要", profile.getProfileSummary());
        BigDecimal bmi = calculateBmi(profile.getHeightCm(), profile.getWeightKg());
        if (bmi != null) {
            append(builder, "BMI", bmi);
        }
        return builder.toString();
    }

    public BigDecimal calculateBmi(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null || heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
    }


    private void append(StringBuilder builder, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            builder.append("\n- ").append(label).append("：").append(value);
        }
    }

    public record ProfileRequest(
            String gender,
            Integer age,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal targetWeightKg,
            String activityLevel,
            String dietPreference,
            String healthNotes
    ) {
    }
}
