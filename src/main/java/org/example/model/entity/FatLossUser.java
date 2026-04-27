package org.example.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fat_loss_user")
public class FatLossUser {
    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 用户昵称 */
    private String nickname;

    /** 用户角色：USER/ADMIN */
    private String role;

    /** 是否启用：1-启用，0-禁用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer isDeleted;
}
