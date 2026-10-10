package com.reality.security;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds expensive password requests on a single small hosting instance. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final SecurityErrorWriter errors;
    private final int requestLimit;
    private final long windowNanos;
    private final LongSupplier monotonicTime;
    private final Semaphore inFlight;
    private final Deque<Long> acceptedRequests = new ArrayDeque<>();

    public AuthRateLimitFilter(SecurityErrorWriter errors) {
        this(errors, 60, 4, Duration.ofMinutes(1), System::nanoTime);
    }

    AuthRateLimitFilter(SecurityErrorWriter errors, int requestLimit, int concurrentLimit,
            Duration window, LongSupplier monotonicTime) {
        if (requestLimit < 1 || concurrentLimit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Authentication limits must be positive.");
        }
        this.errors = errors;
        this.requestLimit = requestLimit;
        this.windowNanos = window.toNanos();
        this.monotonicTime = monotonicTime;
        this.inFlight = new Semaphore(concurrentLimit);
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !HttpMethod.POST.matches(request.getMethod()) ||
                !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // Do not queue password work or rely on unverified proxy/client IP headers.
        if (!inFlight.tryAcquire()) {
            reject(request, response, 1);
            return;
        }
        try {
            long retrySeconds = reserveRequest();
            if (retrySeconds > 0) {
                reject(request, response, retrySeconds);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            inFlight.release();
        }
    }

    private synchronized long reserveRequest() {
        long now = monotonicTime.getAsLong();
        while (!acceptedRequests.isEmpty() && now - acceptedRequests.peekFirst() >= windowNanos) {
            acceptedRequests.removeFirst();
        }
        if (acceptedRequests.size() >= requestLimit) {
            long remaining = windowNanos - (now - acceptedRequests.peekFirst());
            return Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L);
        }
        // At most requestLimit timestamps are retained, irrespective of supplied usernames.
        acceptedRequests.addLast(now);
        return 0;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retrySeconds)
            throws IOException {
        response.setHeader("Retry-After", Long.toString(retrySeconds));
        errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                "Too many sign-in or registration attempts. Please try again shortly.");
    }
}
