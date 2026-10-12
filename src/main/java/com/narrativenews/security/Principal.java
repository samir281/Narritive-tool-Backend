package com.narrativenews.security;

import java.util.UUID;

public record Principal(UUID id, String role) {
    public static final String ADMIN = "ADMIN";
    public static final String READER = "READER";
}
