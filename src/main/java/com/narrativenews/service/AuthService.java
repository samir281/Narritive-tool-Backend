package com.narrativenews.service;

import com.narrativenews.domain.Admin;
import com.narrativenews.domain.Enums.AccountStatus;
import com.narrativenews.domain.Reader;
import com.narrativenews.repo.AdminRepository;
import com.narrativenews.repo.ReaderRepository;
import com.narrativenews.security.JwtService;
import com.narrativenews.security.Principal;
import com.narrativenews.web.Dtos.AuthResponse;
import com.narrativenews.web.Dtos.UserView;
import com.narrativenews.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final long MINUTE = 60_000;
    private final AdminRepository admins;
    private final ReaderRepository readers;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter limiter;

    public AuthService(AdminRepository admins, ReaderRepository readers, PasswordEncoder encoder, JwtService jwt, RateLimiter limiter) {
        this.admins = admins; this.readers = readers; this.encoder = encoder; this.jwt = jwt; this.limiter = limiter;
    }

    private static DomainException badLogin() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Wrong email or password");
    }

    public AuthResponse adminLogin(String email, String password, String ip) {
        limiter.check("admin-login:" + ip, 10, 5 * MINUTE);
        Admin a = admins.findByEmailIgnoreCase(email.trim()).orElseThrow(AuthService::badLogin);
        if (!encoder.matches(password, a.getPasswordHash())) throw badLogin();
        if (a.getStatus() != AccountStatus.ACTIVE) throw badLogin();
        return new AuthResponse(jwt.issue(a.getId(), Principal.ADMIN),
                new UserView(a.getId(), a.getName(), a.getEmail(), Principal.ADMIN));
    }

    public AuthResponse readerRegister(String displayName, String email, String password, String ip) {
        limiter.check("reader-register:" + ip, 5, 10 * MINUTE);
        String e = email.trim().toLowerCase();
        if (readers.existsByEmailIgnoreCase(e)) throw DomainException.conflict("EMAIL_TAKEN", "That email is already registered. Try signing in.");
        Reader r = new Reader();
        r.setDisplayName(displayName.trim());
        r.setEmail(e);
        r.setPasswordHash(encoder.encode(password));
        r = readers.save(r);
        return readerToken(r);
    }

    public AuthResponse readerLogin(String email, String password, String ip) {
        limiter.check("reader-login:" + ip, 10, 5 * MINUTE);
        Reader r = readers.findByEmailIgnoreCase(email.trim()).orElseThrow(AuthService::badLogin);
        if (!encoder.matches(password, r.getPasswordHash())) throw badLogin();
        if (r.getStatus() != AccountStatus.ACTIVE) throw new DomainException(HttpStatus.FORBIDDEN, "READER_BLOCKED", "This account is disabled");
        return readerToken(r);
    }

    private AuthResponse readerToken(Reader r) {
        return new AuthResponse(jwt.issue(r.getId(), Principal.READER),
                new UserView(r.getId(), r.getDisplayName(), r.getEmail(), Principal.READER));
    }
}
