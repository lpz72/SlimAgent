<template>
  <div v-if="visible" class="profile-panel" style="display: flex">
    <div class="profile-card">
      <div class="profile-header">
        <div>
          <div class="auth-kicker">Profile</div>
          <h2>减脂资料</h2>
        </div>
        <button class="profile-close" type="button" @click="$emit('close')">×</button>
      </div>
      <div class="profile-grid">
        <select v-model="profile.gender" class="auth-input">
          <option value="">性别</option>
          <option value="MALE">男</option>
          <option value="FEMALE">女</option>
          <option value="OTHER">其他</option>
        </select>
        <input v-model.number="profile.age" class="auth-input" type="number" placeholder="年龄" />
        <input v-model.number="profile.heightCm" class="auth-input" type="number" placeholder="身高 cm" />
        <input v-model.number="profile.weightKg" class="auth-input" type="number" placeholder="当前体重 kg" />
        <input v-model.number="profile.targetWeightKg" class="auth-input" type="number" placeholder="目标体重 kg" />
        <select v-model="profile.activityLevel" class="auth-input">
          <option value="">活动水平</option>
          <option value="SEDENTARY">久坐</option>
          <option value="LIGHT">轻度活动</option>
          <option value="MODERATE">中度活动</option>
          <option value="ACTIVE">高活动</option>
          <option value="VERY_ACTIVE">极高活动</option>
        </select>
      </div>
      <textarea v-model="profile.dietPreference" class="auth-input profile-textarea" placeholder="饮食偏好，例如少油、素食、乳糖不耐受"></textarea>
      <textarea v-model="profile.healthNotes" class="auth-input profile-textarea" placeholder="健康备注/禁忌，例如高血压、膝盖不适、过敏信息"></textarea>
      <div class="profile-actions">
        <button class="auth-submit" type="button" @click="$emit('save')">保存资料</button>
        <button class="ghost-btn" type="button" @click="$emit('logout')">退出登录</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { UserProfile } from '@/types'

defineProps<{ visible: boolean; profile: UserProfile }>()
defineEmits<{ close: []; save: []; logout: [] }>()
</script>
