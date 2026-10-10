package com.reality.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.reality.entity.AuthSession;
import com.reality.repository.AuthSessionRepository;
import com.reality.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired AuthSessionRepository sessions;
    private static final String PASSWORD = "test-only-long-password";

    @Test void registrationNormalizesIdentityHashesPasswordsAndStoresOnlyTokenHash() throws Exception {
        Instant before = Instant.now();
        JsonNode response = register("  Case_User  ", "  Test Person  ", PASSWORD);
        String token = response.get("token").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(response.get("user").get("username").asString()).isEqualTo("case_user");
        assertThat(response.get("user").get("displayName").asString()).isEqualTo("Test Person");
        assertThat(response.get("user").has("passwordHash")).isFalse();
        assertThat(response.get("user").has("password")).isFalse();
        var account = users.findByUsername("case_user").orElseThrow();
        assertThat(account.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        AuthSession saved = sessions.findByTokenHash(TokenHashes.sha256(token)).orElseThrow();
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(token);
        Instant expiry = Instant.parse(response.get("expiresAt").asString());
        assertThat(Duration.between(before, expiry).toDays()).isEqualTo(30);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(account.getId()))
            .andExpect(jsonPath("$.username").value("case_user"));
    }

    @Test void duplicateNamesAreCaseInsensitiveAndValidationNeverPersistsInvalidAccounts() throws Exception {
        register("unique_user", "Test Person", PASSWORD);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new com.reality.dto.auth.RegisterRequest("UNIQUE_USER", "Another", PASSWORD))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        for (var request : List.of(
                new com.reality.dto.auth.RegisterRequest("bad user", "Person", PASSWORD),
                new com.reality.dto.auth.RegisterRequest("new_user", " ", PASSWORD),
                new com.reality.dto.auth.RegisterRequest("new_user", "Person", "short"),
                new com.reality.dto.auth.RegisterRequest("new_user", "Person", "é".repeat(37)))) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        assertThat(users.findByUsername("new_user")).isEmpty();
    }

    @Test void incorrectAndUnknownCredentialsShareTheSameErrorAndOverlengthPasswordsDoNotMatchPrefixes() throws Exception {
        register("credential_user", "Person", "a".repeat(72));
        String wrong = loginFailure("credential_user", "wrong-test-password");
        assertThat(loginFailure("missing_user", "wrong-test-password")).isEqualTo(wrong);
        assertThat(loginFailure("credential_user", "a".repeat(73))).isEqualTo(wrong);
        assertThat(wrong).isEqualTo("Invalid username or password.");
    }

    @Test void logoutRevokesOnlyThePresentedSessionAndDoesNotCreateAuthCookies() throws Exception {
        String first = register("logout_user", "Person", PASSWORD).get("token").asString();
        String second = login("logout_user", PASSWORD).get("token").asString();
        var result = mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first))
            .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
        assertThat(sessions.findByTokenHash(TokenHashes.sha256(first)).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void expiredTokensAreRejectedAndInvalidatedOnce() throws Exception {
        String token = register("expired_user", "Person", PASSWORD).get("token").asString();
        AuthSession saved = sessions.findByTokenHash(TokenHashes.sha256(token)).orElseThrow();
        saved.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.saveAndFlush(saved);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        Instant revoked = saved.getRevokedAt();
        assertThat(revoked).isNotNull();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThat(saved.getRevokedAt()).isEqualTo(revoked);
    }

    @Test void allExistingOperationsRequireAuthenticationAndStaticResourcesStayPublic() throws Exception {
        List<MockHttpServletRequestBuilder> operations = List.of(
            get("/activities"), get("/activities/1"), post("/activities"), put("/activities/1"), delete("/activities/1"),
            get("/api/sessions"), get("/api/sessions/1"), get("/api/sessions/activity/1"),
            get("/api/sessions/activity/1/date/2026-10-01"), post("/api/sessions/start"), put("/api/sessions/1/stop"), delete("/api/sessions/1"),
            get("/api/sessions/1/breaks"), post("/api/sessions/1/break"), put("/api/sessions/1/resume"),
            get("/api/daily-progress/activity/1/date/2026-10-01"), get("/api/streaks/activity/1"),
            get("/api/reports/activity/1/month/2026-10"));
        for (var operation : operations) mvc.perform(operation)
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Sign in to use Reality."));
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
    }

    private JsonNode register(String username, String displayName, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new com.reality.dto.auth.RegisterRequest(username, displayName, password))))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
    private JsonNode login(String username, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new com.reality.dto.auth.LoginRequest(username, password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String loginFailure(String username, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new com.reality.dto.auth.LoginRequest(username, password))))
            .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString())
            .get("message").asString();
    }
}
