package com.xiaoying.studio.session;

import com.xiaoying.studio.web.ApiProblem;
import com.xiaoying.studio.web.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    public static final String COOKIE_NAME = "studio_session";
    private final SessionMapper mapper;
    private final boolean demoMode;
    private final long ttlDays;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionMapper mapper, @Value("${studio.demo-mode:true}") boolean demoMode,
                          @Value("${studio.session-ttl-days:7}") long ttlDays) {
        if (ttlDays < 1 || ttlDays > 30) throw new IllegalArgumentException("Session TTL must be 1..30 days");
        this.mapper = mapper;
        this.demoMode = demoMode;
        this.ttlDays = ttlDays;
    }

    public record CreatedSession(DemoUser user, String token, Duration maxAge) {}

    public List<DemoUser> users() { requireDemoMode(); return mapper.users(); }

    @Transactional
    public CreatedSession switchUser(String userId, String oldToken) {
        requireDemoMode();
        DemoUser user = mapper.user(userId);
        if (user == null) throw new ResourceNotFoundException("演示用户不存在");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        mapper.insert(UUID.randomUUID().toString(), hash(token), user.id(),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(ttlDays));
        if (validToken(oldToken)) mapper.delete(hash(oldToken));
        return new CreatedSession(user, token, Duration.ofDays(ttlDays));
    }

    public DemoUser requireUser(String token) {
        DemoUser user = validToken(token)
                ? mapper.current(hash(token), LocalDateTime.now(ZoneOffset.UTC)) : null;
        if (user == null) throw new ApiProblem(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "请先选择演示身份，或重新确认已过期的会话");
        return user;
    }

    public DemoUser requireExpectedUser(String token, String expectedUserId) {
        DemoUser user = requireUser(token);
        if (!user.id().equals(expectedUserId)) {
            throw new ApiProblem(HttpStatus.CONFLICT, "SESSION_CHANGED", "当前身份已变化，请刷新身份后重新操作");
        }
        return user;
    }

    private void requireDemoMode() {
        if (!demoMode) throw new ResourceNotFoundException("演示身份入口已关闭");
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
