package com.narrativenews.security;

import com.narrativenews.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(UUID subject, String role) {
        long minutes = Principal.ADMIN.equals(role) ? props.jwt().adminTtlMinutes() : props.jwt().readerTtlMinutes();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject.toString())
                .audience().add(role.toLowerCase()).and()      // aud=admin | aud=reader
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(minutes))))
                .signWith(key)
                .compact();
    }

    public Optional<Principal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String role = c.get("role", String.class);
            if (role == null || !c.getAudience().contains(role.toLowerCase())) return Optional.empty();
            return Optional.of(new Principal(UUID.fromString(c.getSubject()), role));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
