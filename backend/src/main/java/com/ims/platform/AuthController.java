package com.ims.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    record RegisterRequest(@Email @NotBlank String email, @Size(min=12,max=100) String password, @NotBlank @Size(max=120) String displayName) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
    record UserView(UUID id, String email, String displayName, Role role) {
        static UserView from(UserAccount u) { return new UserView(u.id,u.email,u.displayName,u.role); }
    }
    record AuthResponse(UserView user, String accessToken, String refreshToken, long expiresIn) {}

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final java.util.Optional<StringRedisTemplate> redis;
    private final boolean redisEnabled;
    AuthController(UserRepository users, PasswordEncoder passwords, TokenService tokens,
                   @Value("${ims.redis.enabled:true}") boolean redisEnabled,
                   java.util.Optional<StringRedisTemplate> redis) {
        this.users=users; this.passwords=passwords; this.tokens=tokens; this.redisEnabled=redisEnabled; this.redis=redis;
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw new ConflictException("Email is already registered");
        UserAccount user=users.save(new UserAccount(request.email(),passwords.encode(request.password()),request.displayName(),Role.CUSTOMER));
        return response(user,tokens.issue(user));
    }
    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        rateLimit(request.email());
        UserAccount user=users.findByEmailIgnoreCase(request.email()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(request.password(),user.passwordHash)) throw new BadCredentialsException("Invalid credentials");
        return response(user,tokens.issue(user));
    }
    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        var p=tokens.verify(request.refreshToken(),"refresh");
        UserAccount user=users.findById(p.userId()).orElseThrow(() -> new BadCredentialsException("Unknown user"));
        tokens.revoke(request.refreshToken());
        return response(user,tokens.issue(user));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) { tokens.revoke(request.refreshToken()); }
    private AuthResponse response(UserAccount u, TokenService.Pair p) {
        return new AuthResponse(UserView.from(u),p.accessToken(),p.refreshToken(),p.expiresIn());
    }
    private void rateLimit(String key) {
        if (!redisEnabled) return;
        redis.ifPresent(r -> {
            try {
                String k="login-rate:"+key.toLowerCase(); Long count=r.opsForValue().increment(k);
                if (count != null && count == 1) r.expire(k,Duration.ofMinutes(1));
                if (count != null && count > 10) throw new RateLimitException("Too many login attempts");
            } catch (RateLimitException e) { throw e; } catch (RuntimeException ignored) {}
        });
    }
}

class ConflictException extends RuntimeException { ConflictException(String m) { super(m); } }
class RateLimitException extends RuntimeException { RateLimitException(String m) { super(m); } }
