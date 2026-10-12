package com.narrativenews.service;

import com.narrativenews.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Tiny in-memory sliding-window limiter. Fine for a single instance; swap for Redis if you ever scale out. */
@Component
public class RateLimiter {
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String key, int max, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMillis) q.pollFirst();
            if (q.size() >= max) throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, slow down");
            q.addLast(now);
        }
        if (hits.size() > 50_000) hits.clear();    // crude memory guard
    }
}
