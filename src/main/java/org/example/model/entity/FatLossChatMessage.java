package org.example.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fat_loss_chat_message")
public class FatLossChatMessage {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private Long userId;

    /** 消息角色：user/assistant/system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 重要性评分，仅用于对话消息记录 */
    private BigDecimal importanceScore;

    /** 消息摘要，仅用于对话消息记录 */
    private String summary;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer isDeleted;
}
