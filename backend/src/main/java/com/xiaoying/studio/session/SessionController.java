package com.xiaoying.studio.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final SessionService sessions;
    private final boolean secureCookie;
    public SessionController(SessionService sessions, @Value("${studio.cookie-secure:false}") boolean secureCookie) {
        this.sessions = sessions;
        this.secureCookie = secureCookie;
    }
    public record SwitchUserRequest(@NotBlank String userId) {}

    @GetMapping("/demo/users")
    public List<DemoUser> users() { return sessions.users(); }

    @GetMapping("/session")
    public DemoUser current(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        return sessions.requireUser(token);
    }

    @PostMapping("/session")
    public ResponseEntity<DemoUser> select(@Valid @RequestBody SwitchUserRequest body,
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        var created = sessions.switchUser(body.userId(), token);
        var cookie = ResponseCookie.from(SessionService.COOKIE_NAME, created.token())
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(created.maxAge()).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(created.user());
    }
}
