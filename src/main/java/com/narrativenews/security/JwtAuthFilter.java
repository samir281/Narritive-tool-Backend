package com.narrativenews.security;

import com.narrativenews.domain.Enums.AccountStatus;
import com.narrativenews.repo.AdminRepository;
import com.narrativenews.repo.ReaderRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Reads "Authorization: Bearer ..." and, if valid and the account is still ACTIVE, authenticates the request. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AdminRepository admins;
    private final ReaderRepository readers;

    public JwtAuthFilter(JwtService jwt, AdminRepository admins, ReaderRepository readers) {
        this.jwt = jwt;
        this.admins = admins;
        this.readers = readers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parse(header.substring(7)).filter(this::stillActive).ifPresent(p -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    private boolean stillActive(Principal p) {
        if (Principal.ADMIN.equals(p.role()))
            return admins.findById(p.id()).map(a -> a.getStatus() == AccountStatus.ACTIVE).orElse(false);
        return readers.findById(p.id()).map(r -> r.getStatus() == AccountStatus.ACTIVE).orElse(false);
    }
}
