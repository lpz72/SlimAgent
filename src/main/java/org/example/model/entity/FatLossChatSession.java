package org.example.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fat_loss_chat_session")
public class FatLossChatSession {
    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一会话 ID */
    private String sessionId;

    /** 用户 ID */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer isDeleted;
}
