package com.ims.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, JwtFilter jwt, ObjectMapper json,
                                       CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.csrf(c -> c.disable())
            .cors(c -> c.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/plans/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request,response,error) -> writeProblem(response,json,401,"Unauthorized"))
                .accessDeniedHandler((request,response,error) -> writeProblem(response,json,403,"Forbidden")))
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${ims.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration=new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**",configuration);
        return source;
    }
    private static void writeProblem(HttpServletResponse response,ObjectMapper json,int status,String title) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(),Map.of(
            "type","https://ims.example/problems/"+title.toLowerCase(),
            "title",title,"status",status,"correlationId",Objects.requireNonNullElse(MDC.get("correlationId"),"")));
    }
}

@Component
class TokenService {
    record Principal(UUID userId, String email, Role role, String type, String jti) {}
    record Pair(String accessToken, String refreshToken, long expiresIn) {}
    private final byte[] secret;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Optional<StringRedisTemplate> redis;
    private final boolean redisEnabled;

    TokenService(@Value("${ims.jwt.secret}") String secret,
                 @Value("${ims.jwt.access-ttl}") Duration accessTtl,
                 @Value("${ims.jwt.refresh-ttl}") Duration refreshTtl,
                 @Value("${ims.redis.enabled:true}") boolean redisEnabled,
                 Optional<StringRedisTemplate> redis) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        this.secret=secret.getBytes(StandardCharsets.UTF_8); this.accessTtl=accessTtl; this.refreshTtl=refreshTtl;
        this.redisEnabled=redisEnabled; this.redis=redis;
    }
    Pair issue(UserAccount user) {
        return new Pair(token(user,"access",accessTtl), token(user,"refresh",refreshTtl), accessTtl.toSeconds());
    }
    private String token(UserAccount user, String type, Duration ttl) {
        try {
            Instant now=Instant.now();
            JWTClaimsSet claims=new JWTClaimsSet.Builder().subject(user.id.toString()).issuer("ims-platform")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plus(ttl))).jwtID(UUID.randomUUID().toString())
                .claim("email",user.email).claim("role",user.role.name()).claim("type",type).build();
            SignedJWT jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) { throw new IllegalStateException("Unable to sign token",e); }
    }
    Principal verify(String raw, String expectedType) {
        try {
            SignedJWT jwt=SignedJWT.parse(raw);
            if (!jwt.verify(new MACVerifier(secret))) throw new JOSEException("invalid signature");
            JWTClaimsSet c=jwt.getJWTClaimsSet();
            if (!"ims-platform".equals(c.getIssuer())
                || c.getExpirationTime() == null
                || c.getExpirationTime().before(new Date())
                || !expectedType.equals(c.getStringClaim("type"))) {
                throw new JOSEException("invalid token");
            }
            if ("refresh".equals(expectedType) && isRevoked(c.getJWTID())) throw new JOSEException("revoked token");
            return new Principal(UUID.fromString(c.getSubject()),c.getStringClaim("email"),
                Role.valueOf(c.getStringClaim("role")),c.getStringClaim("type"),c.getJWTID());
        } catch (Exception e) { throw new org.springframework.security.authentication.BadCredentialsException("Invalid token",e); }
    }
    void revoke(String raw) {
        try {
            SignedJWT jwt=SignedJWT.parse(raw); JWTClaimsSet c=jwt.getJWTClaimsSet();
            long seconds=Math.max(1,Duration.between(Instant.now(),c.getExpirationTime().toInstant()).toSeconds());
            if (redisEnabled) redis.ifPresent(r -> { try { r.opsForValue().set("revoked:"+c.getJWTID(),"1",Duration.ofSeconds(seconds)); } catch (RuntimeException ignored) {} });
        } catch (Exception e) { throw new org.springframework.security.authentication.BadCredentialsException("Invalid token",e); }
    }
    private boolean isRevoked(String jti) {
        if (!redisEnabled) return false;
        return redis.map(r -> { try { return Boolean.TRUE.equals(r.hasKey("revoked:"+jti)); } catch (RuntimeException e) { return false; } }).orElse(false);
    }
}

@Component
class JwtFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    JwtFilter(TokenService tokens) { this.tokens=tokens; }
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String auth=req.getHeader("Authorization");
        if (auth!=null && auth.startsWith("Bearer ")) {
            try {
                var p=tokens.verify(auth.substring(7),"access");
                var principal=new ImsPrincipal(p.userId(),p.email(),p.role());
                var token=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+p.role())));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(token);
            } catch (org.springframework.security.core.AuthenticationException ignored) {}
        }
        chain.doFilter(req,res);
    }
}

record ImsPrincipal(UUID id, String email, Role role) {}
