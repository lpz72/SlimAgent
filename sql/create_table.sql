-- SuperBizAgent 智能减脂 Agent 扩展表结构
-- 说明：业务 SQL 统一放在本文件，应用代码不内置建表语句。
-- 约定：MySQL 字段名使用 Java 驼峰命名；LLM 抽取的长期记忆不存 MySQL，存入 Milvus 中以 userId 为名的 Collection。

CREATE TABLE IF NOT EXISTS `fat_loss_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `passwordHash` VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDeleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fat_loss_user_username` (`username`),
  KEY `idx_fat_loss_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能减脂用户表';

CREATE TABLE IF NOT EXISTS `fat_loss_user_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `userId` BIGINT NOT NULL COMMENT '用户 ID',
  `gender` VARCHAR(16) DEFAULT NULL COMMENT '性别：MALE/FEMALE/OTHER',
  `age` INT DEFAULT NULL COMMENT '年龄',
  `heightCm` DECIMAL(6,2) DEFAULT NULL COMMENT '身高 cm',
  `weightKg` DECIMAL(6,2) DEFAULT NULL COMMENT '体重 kg',
  `targetWeightKg` DECIMAL(6,2) DEFAULT NULL COMMENT '目标体重 kg',
  `activityLevel` VARCHAR(32) DEFAULT NULL COMMENT '活动水平：SEDENTARY/LIGHT/MODERATE/ACTIVE/VERY_ACTIVE',
  `dietPreference` VARCHAR(255) DEFAULT NULL COMMENT '饮食偏好',
  `healthNotes` TEXT DEFAULT NULL COMMENT '健康备注/禁忌',
  `profileSummary` TEXT DEFAULT NULL COMMENT '用户画像摘要，来源于结构化信息与用户显式补充',
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDeleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fat_loss_profile_user` (`userId`),
  KEY `idx_fat_loss_profile_updated` (`updatedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能减脂用户结构化资料与画像表';

CREATE TABLE IF NOT EXISTS `fat_loss_chat_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `sessionId` VARCHAR(80) NOT NULL COMMENT '全局唯一会话 ID',
  `userId` BIGINT NOT NULL COMMENT '用户 ID',
  `title` VARCHAR(160) DEFAULT NULL COMMENT '会话标题',
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDeleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fat_loss_chat_session` (`sessionId`),
  KEY `idx_fat_loss_chat_session_user_updated` (`userId`, `updatedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能减脂对话会话表';

CREATE TABLE IF NOT EXISTS `fat_loss_chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `sessionId` VARCHAR(80) NOT NULL COMMENT '会话 ID',
  `userId` BIGINT NOT NULL COMMENT '用户 ID',
  `role` VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant/system',
  `content` MEDIUMTEXT NOT NULL COMMENT '消息内容',
  `importanceScore` DECIMAL(4,2) DEFAULT NULL COMMENT '重要性评分，仅用于对话消息记录',
  `summary` TEXT DEFAULT NULL COMMENT '消息摘要，仅用于对话消息记录',
  `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `isDeleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_fat_loss_chat_msg_session_created` (`sessionId`, `createdAt`),
  KEY `idx_fat_loss_chat_msg_user_created` (`userId`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能减脂对话消息表';

-- 初始化管理员账号示例：密码请由应用注册接口或 BCrypt 工具生成后替换，不建议直接使用明文。
-- INSERT INTO `fat_loss_user` (`username`, `passwordHash`, `nickname`, `role`) VALUES ('admin', '$2a$10$replace_with_bcrypt_hash', '管理员', 'ADMIN');
