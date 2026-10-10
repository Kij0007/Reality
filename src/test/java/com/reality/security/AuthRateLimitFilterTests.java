package com.reality.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitFilterTests {
    private final AtomicLong nanos = new AtomicLong();
    private final SecurityErrorWriter errors = new SecurityErrorWriter(JsonMapper.builder().build());

    @Test void loginAndRegistrationShareOneRollingBudgetAndReturnStructuredRetry() throws Exception {
        AuthRateLimitFilter filter = filter(2, 4);
        AtomicInteger executions = new AtomicInteger();
        attempt(filter, "POST", "/api/auth/login", executions);
        nanos.set(Duration.ofSeconds(30).toNanos());
        attempt(filter, "POST", "/api/auth/register", executions);
        nanos.set(Duration.ofSeconds(40).toNanos());
        MockHttpServletResponse rejected = attempt(filter, "POST", "/api/auth/login", executions);

        assertThat(executions.get()).isEqualTo(2);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("20");
        assertThat(rejected.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(rejected.getContentType()).startsWith("application/json");
        var json = JsonMapper.builder().build().readTree(rejected.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("message").asString()).contains("try again shortly");
        assertThat(json.get("path").asString()).isEqualTo("/api/auth/login");

        // Only the attempt at t=0 expires at t=60; the t=30 attempt is still counted.
        nanos.set(Duration.ofMinutes(1).toNanos());
        assertThat(attempt(filter, "POST", "/api/auth/login", executions).getStatus()).isEqualTo(200);
        assertThat(attempt(filter, "POST", "/api/auth/login", executions).getStatus()).isEqualTo(429);
    }

    @Test void changingSpoofedClientAddressesCannotBypassTheGlobalBudget() throws Exception {
        AuthRateLimitFilter filter = filter(1, 4);
        AtomicInteger executions = new AtomicInteger();
        attempt(filter, "POST", "/api/auth/login", executions);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("192.0.2.99");
        request.addHeader("X-Forwarded-For", "198.51.100.24");
        request.addHeader("Forwarded", "for=203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> executions.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test void ordinaryApiRequestsStaticResourcesLogoutAndHealthDoNotConsumeTheAuthBudget() throws Exception {
        AuthRateLimitFilter filter = filter(1, 1);
        AtomicInteger executions = new AtomicInteger();
        for (String path : new String[]{"/activities", "/api/auth/me", "/index.html", "/api/health", "/api/auth/login"}) {
            assertThat(attempt(filter, "GET", path, executions).getStatus()).isEqualTo(200);
        }
        assertThat(attempt(filter, "POST", "/api/auth/logout", executions).getStatus()).isEqualTo(200);
        assertThat(attempt(filter, "POST", "/api/auth/login", executions).getStatus()).isEqualTo(200);
        assertThat(attempt(filter, "POST", "/api/auth/register", executions).getStatus()).isEqualTo(429);
        assertThat(executions.get()).isEqualTo(7);
    }

    @Test void concurrentPasswordWorkIsRejectedWithoutQueuingAndPermitIsReleased() throws Exception {
        AuthRateLimitFilter filter = filter(10, 1);
        AtomicInteger executions = new AtomicInteger();
        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, (request, response) -> {
            MockHttpServletResponse overlapping = attempt(filter, "POST", "/api/auth/register", executions);
            assertThat(overlapping.getStatus()).isEqualTo(429);
            assertThat(overlapping.getHeader("Retry-After")).isEqualTo("1");
        });
        assertThat(executions.get()).isZero();
        assertThat(attempt(filter, "POST", "/api/auth/login", executions).getStatus()).isEqualTo(200);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test void failedControllerWorkStillReleasesTheConcurrencyPermit() throws Exception {
        AuthRateLimitFilter filter = filter(10, 1);
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"),
                new MockHttpServletResponse(), (request, response) -> { throw new ServletException("test failure"); }))
                .isInstanceOf(ServletException.class);
        assertThat(attempt(filter, "POST", "/api/auth/login", new AtomicInteger()).getStatus()).isEqualTo(200);
    }

    @Test void nonRootServletContextStillProtectsTheActualAuthenticationPaths() throws Exception {
        AuthRateLimitFilter filter = filter(1, 1);
        AtomicInteger executions = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/reality/api/auth/login");
            request.setContextPath("/reality");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> executions.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(attempt == 0 ? 200 : 429);
        }
        assertThat(executions.get()).isEqualTo(1);
    }

    private AuthRateLimitFilter filter(int requests, int concurrent) {
        return new AuthRateLimitFilter(errors, requests, concurrent, Duration.ofMinutes(1), nanos::get);
    }

    private MockHttpServletResponse attempt(AuthRateLimitFilter filter, String method, String path,
            AtomicInteger executions) throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, path), response,
                (request, acceptedResponse) -> executions.incrementAndGet());
        return response;
    }
}
