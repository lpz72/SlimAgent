package org.example.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fat_loss_user_profile")
public class FatLossUserProfile {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 性别：MALE/FEMALE/OTHER */
    private String gender;

    /** 年龄 */
    private Integer age;

    /** 身高，单位 cm */
    private BigDecimal heightCm;

    /** 当前体重，单位 kg */
    private BigDecimal weightKg;

    /** 目标体重，单位 kg */
    private BigDecimal targetWeightKg;

    /** 活动水平：SEDENTARY/LIGHT/MODERATE/ACTIVE/VERY_ACTIVE */
    private String activityLevel;

    /** 饮食偏好 */
    private String dietPreference;

    /** 健康备注或禁忌 */
    private String healthNotes;

    /** 用户画像摘要，来源于结构化信息与用户显式补充 */
    private String profileSummary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer isDeleted;
}
