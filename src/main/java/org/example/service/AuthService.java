package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpSession;
import org.example.model.entity.FatLossUser;
import org.example.mapper.FatLossUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Service
public class AuthService {
    public static final String SESSION_USER_ID = "FAT_LOSS_USER_ID";
    private static final String DEFAULT_ROLE = "USER";
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private FatLossUserMapper userMapper;

    public AuthUser register(String username, String password, String nickname) {
        validateCredential(username, password);
        FatLossUser existing = userMapper.selectOne(new LambdaQueryWrapper<FatLossUser>()
                .eq(FatLossUser::getUsername, username));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        FatLossUser user = new FatLossUser();
        user.setUsername(username.trim());
        user.setPasswordHash(hashPassword(password));
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : username.trim());
        user.setRole(DEFAULT_ROLE);
        user.setEnabled(1);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setIsDeleted(0);
        userMapper.insert(user);
        return AuthUser.from(user);
    }

    public AuthUser login(String username, String password, HttpSession session) {
        validateCredential(username, password);
        FatLossUser user = userMapper.selectOne(new LambdaQueryWrapper<FatLossUser>()
                .eq(FatLossUser::getUsername, username.trim()));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        session.setAttribute(SESSION_USER_ID, user.getId());
        return AuthUser.from(user);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public AuthUser currentUser(HttpSession session) {
        FatLossUser user = requireUserEntity(session);
        return AuthUser.from(user);
    }

    public FatLossUser requireUserEntity(HttpSession session) {
        Long userId = getSessionUserId(session);
        if (userId == null) {
            throw new IllegalStateException("请先登录后再对话");
        }
        FatLossUser user = userMapper.selectById(userId);
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            throw new IllegalStateException("登录状态已失效，请重新登录");
        }
        return user;
    }

    public Long requireUserId(HttpSession session) {
        return requireUserEntity(session).getId();
    }

    public boolean isAdmin(HttpSession session) {
        FatLossUser user = requireUserEntity(session);
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private Long getSessionUserId(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private void validateCredential(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (username.trim().length() < 3 || username.trim().length() > 64) {
            throw new IllegalArgumentException("用户名长度需在 3 到 64 位之间");
        }
        if (password.length() < 6 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度需在 6 到 64 位之间");
        }
    }

    private String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    public record AuthUser(Long id, String username, String nickname, String role) {
        public static AuthUser from(FatLossUser user) {
            return new AuthUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        }
    }
}
