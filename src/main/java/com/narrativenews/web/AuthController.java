package com.narrativenews.web;

import com.narrativenews.service.AuthService;
import com.narrativenews.web.Dtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/admin/auth/login")
    public AuthResponse adminLogin(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.adminLogin(r.email(), r.password(), req.getRemoteAddr());
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
        return auth.readerRegister(r.displayName(), r.email(), r.password(), req.getRemoteAddr());
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.readerLogin(r.email(), r.password(), req.getRemoteAddr());
    }
}
