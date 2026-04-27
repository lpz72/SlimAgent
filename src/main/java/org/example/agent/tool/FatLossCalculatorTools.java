package org.example.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FatLossCalculatorTools {
    @Tool(description = "Calculate BMI and classify weight status for fat-loss planning. Input height in centimeters and weight in kilograms.")
    public String calculateBmi(
            @ToolParam(description = "Height in centimeters") BigDecimal heightCm,
            @ToolParam(description = "Weight in kilograms") BigDecimal weightKg) {
        if (heightCm == null || weightKg == null || heightCm.compareTo(BigDecimal.ZERO) <= 0 || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            return "身高和体重必须为正数。";
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal bmi = weightKg.divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
        return "BMI=" + bmi + "，分类=" + classifyBmi(bmi) + "。";
    }

    @Tool(description = "Estimate daily energy needs and recommended fat-loss calories using Mifflin-St Jeor equation.")
    public String calculateNutritionTarget(
            @ToolParam(description = "Gender, MALE or FEMALE") String gender,
            @ToolParam(description = "Age in years") Integer age,
            @ToolParam(description = "Height in centimeters") BigDecimal heightCm,
            @ToolParam(description = "Weight in kilograms") BigDecimal weightKg,
            @ToolParam(description = "Activity level: SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE") String activityLevel) {
        if (age == null || heightCm == null || weightKg == null || age <= 0) {
            return "年龄、身高和体重必须完整且有效。";
        }
        BigDecimal bmr = BigDecimal.valueOf(10).multiply(weightKg)
                .add(BigDecimal.valueOf(6.25).multiply(heightCm))
                .subtract(BigDecimal.valueOf(5L * age));
        if ("FEMALE".equalsIgnoreCase(gender)) {
            bmr = bmr.subtract(BigDecimal.valueOf(161));
        } else {
            bmr = bmr.add(BigDecimal.valueOf(5));
        }
        BigDecimal tdee = bmr.multiply(activityFactor(activityLevel));
        BigDecimal fatLossCalories = tdee.subtract(BigDecimal.valueOf(400)).max(BigDecimal.valueOf(1200));
        BigDecimal proteinMin = weightKg.multiply(BigDecimal.valueOf(1.6)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal proteinMax = weightKg.multiply(BigDecimal.valueOf(2.0)).setScale(0, RoundingMode.HALF_UP);
        return "估算基础代谢BMR=" + bmr.setScale(0, RoundingMode.HALF_UP) + "kcal，维持热量TDEE="
                + tdee.setScale(0, RoundingMode.HALF_UP) + "kcal，减脂建议热量约="
                + fatLossCalories.setScale(0, RoundingMode.HALF_UP) + "kcal/天，蛋白质建议="
                + proteinMin + "-" + proteinMax + "g/天。";
    }

    @Tool(description = "Estimate calories burned by exercise using MET formula.")
    public String calculateExerciseCalories(
            @ToolParam(description = "Body weight in kilograms") BigDecimal weightKg,
            @ToolParam(description = "Exercise MET value, e.g. walking 3.5, jogging 7, cycling 6") BigDecimal met,
            @ToolParam(description = "Duration in minutes") Integer minutes) {
        if (weightKg == null || met == null || minutes == null || weightKg.compareTo(BigDecimal.ZERO) <= 0 || met.compareTo(BigDecimal.ZERO) <= 0 || minutes <= 0) {
            return "体重、MET 和运动分钟数必须为正数。";
        }
        BigDecimal calories = met.multiply(BigDecimal.valueOf(3.5)).multiply(weightKg)
                .divide(BigDecimal.valueOf(200), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(minutes));
        return "估算运动消耗约 " + calories.setScale(0, RoundingMode.HALF_UP) + " kcal。";
    }

    private BigDecimal activityFactor(String activityLevel) {
        if (activityLevel == null) {
            return BigDecimal.valueOf(1.2);
        }
        return switch (activityLevel.toUpperCase()) {
            case "LIGHT" -> BigDecimal.valueOf(1.375);
            case "MODERATE" -> BigDecimal.valueOf(1.55);
            case "ACTIVE" -> BigDecimal.valueOf(1.725);
            case "VERY_ACTIVE" -> BigDecimal.valueOf(1.9);
            default -> BigDecimal.valueOf(1.2);
        };
    }

    private String classifyBmi(BigDecimal bmi) {
        if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0) {
            return "偏低";
        }
        if (bmi.compareTo(BigDecimal.valueOf(24)) < 0) {
            return "正常";
        }
        if (bmi.compareTo(BigDecimal.valueOf(28)) < 0) {
            return "超重";
        }
        return "肥胖";
    }
}
