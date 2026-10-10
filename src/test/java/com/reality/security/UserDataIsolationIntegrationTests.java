package com.reality.security;

import com.jayway.jsonpath.JsonPath;
import com.reality.entity.Activity;
import com.reality.entity.Session;
import com.reality.entity.SessionBreak;
import com.reality.enums.ActivityCategory;
import com.reality.repository.ActivityRepository;
import com.reality.repository.SessionBreakRepository;
import com.reality.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reality-isolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class UserDataIsolationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ActivityRepository activities;
    @Autowired SessionRepository sessions;
    @Autowired SessionBreakRepository breaks;

    private Account alice;
    private Account bob;
    private long aliceActivity;
    private long aliceSession;
    private long bobActivity;
    private long bobSession;
    private long legacyActivity;
    private long legacySession;
    private LocalDate today;

    record Account(String token, long id) {}
    record Endpoint(HttpMethod method, String path, String body) {}

    @BeforeEach
    void createIndependentUsersAndPrivateData() throws Exception {
        today = LocalDate.now();
        alice = register("alice");
        bob = register("bob");
        aliceActivity = createActivity(alice, "Alice private activity");
        bobActivity = createActivity(bob, "Bob private activity");
        aliceSession = startSession(alice, aliceActivity);
        bobSession = startSession(bob, bobActivity);
        call(alice, new Endpoint(HttpMethod.POST, "/api/sessions/" + aliceSession + "/break",
                "{\"description\":\"Alice private break note\"}"))
                .andExpect(status().isCreated());

        // Simulates a database created before accounts were introduced. It must stay hidden.
        Activity legacy = new Activity();
        legacy.setName("Unowned historical activity");
        legacy.setMinimumDuration(1);
        legacy.setCategory(ActivityCategory.OTHER);
        legacy.setActive(true);
        legacy.setStartDate(today.minusDays(1));
        legacy.setScheduledDays(new HashSet<>(Arrays.asList(DayOfWeek.values())));
        legacyActivity = activities.saveAndFlush(legacy).getId();
        Session history = new Session();
        history.setActivity(legacy);
        history.setStartTime(today.atStartOfDay());
        legacySession = sessions.saveAndFlush(history).getId();
        SessionBreak pause = new SessionBreak();
        pause.setSession(history);
        pause.setStartTime(today.atStartOfDay().plusMinutes(1));
        pause.setDescription("Unowned historical note");
        breaks.saveAndFlush(pause);
    }

    @Test
    void allEighteenExistingEndpointsRequireAuthentication() throws Exception {
        List<Endpoint> endpoints = new ArrayList<>(resourceOperations(aliceActivity, aliceSession));
        endpoints.add(new Endpoint(HttpMethod.POST, "/activities", activityBody("Anonymous activity")));
        endpoints.add(new Endpoint(HttpMethod.GET, "/activities", null));
        endpoints.add(new Endpoint(HttpMethod.GET, "/api/sessions", null));
        assertThat(endpoints).hasSize(18);
        for (Endpoint endpoint : endpoints) {
            mvc.perform(build(endpoint)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void foreignResourceIdsReturn404AcrossAllFifteenResourceOperations() throws Exception {
        for (Endpoint endpoint : resourceOperations(aliceActivity, aliceSession)) {
            call(bob, endpoint).andExpect(status().isNotFound())
                    .andExpect(content().string(not(containsString("Alice private"))));
        }
        call(alice, new Endpoint(HttpMethod.GET, "/activities/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice private activity"))
                .andExpect(jsonPath("$.owner").doesNotExist()).andExpect(jsonPath("$.ownerId").doesNotExist());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/" + aliceSession, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endTime").isEmpty());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/" + aliceSession + "/breaks", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Alice private break note"));
    }

    @Test
    void listsAreScopedAndCreationAlwaysUsesAuthenticatedOwner() throws Exception {
        call(alice, new Endpoint(HttpMethod.GET, "/activities", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aliceActivity));
        call(bob, new Endpoint(HttpMethod.GET, "/activities", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bobActivity));
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aliceSession));
        call(bob, new Endpoint(HttpMethod.GET, "/api/sessions", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bobSession));

        long newActivity = createActivity(bob, "Bob second activity");
        assertThat(activities.findById(newActivity).orElseThrow().getOwner().getId()).isEqualTo(bob.id());
        assertThat(activities.findById(aliceActivity).orElseThrow().getOwner().getId()).isEqualTo(alice.id());
        assertThat(activities.findById(legacyActivity).orElseThrow().getOwner()).isNull();
        call(alice, new Endpoint(HttpMethod.GET, "/activities/" + newActivity, null)).andExpect(status().isNotFound());
    }

    @Test
    void unownedLegacyRowsRemainInvisibleEvenAfterAnotherRegistration() throws Exception {
        for (Account account : List.of(alice, bob)) {
            for (Endpoint endpoint : resourceOperations(legacyActivity, legacySession)) {
                call(account, endpoint).andExpect(status().isNotFound());
            }
        }
        Account newcomer = register("newcomer");
        call(newcomer, new Endpoint(HttpMethod.GET, "/activities", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        call(newcomer, new Endpoint(HttpMethod.GET, "/api/sessions", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertThat(activities.findById(legacyActivity).orElseThrow().getOwner()).isNull();
    }

    @Test
    void ownerCanEditTrackPauseResumeStopAndReadActualProgress() throws Exception {
        call(alice, new Endpoint(HttpMethod.PUT, "/activities/" + aliceActivity,
                activityBody("Alice updated activity")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice updated activity"));
        call(alice, new Endpoint(HttpMethod.PUT, "/api/sessions/" + aliceSession + "/resume", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endTime").isNotEmpty());
        call(alice, new Endpoint(HttpMethod.POST, "/api/sessions/" + aliceSession + "/break",
                "{\"description\":\"Second break\"}"))
                .andExpect(status().isCreated());
        call(alice, new Endpoint(HttpMethod.PUT, "/api/sessions/" + aliceSession + "/stop", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endTime").isNotEmpty())
                .andExpect(jsonPath("$.duration").isNumber());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/" + aliceSession + "/breaks", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].endTime").isNotEmpty());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity + "/date/" + today, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activityId").value(aliceActivity));
        call(alice, new Endpoint(HttpMethod.GET, "/api/daily-progress/activity/" + aliceActivity + "/date/" + today, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activityName").value("Alice updated activity"));
        call(alice, new Endpoint(HttpMethod.GET, "/api/streaks/activity/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStreak").isNumber());
        call(alice, new Endpoint(HttpMethod.GET, "/api/reports/activity/" + aliceActivity + "/month/" + YearMonth.from(today), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalSessions").value(1));
        call(bob, new Endpoint(HttpMethod.GET, "/api/sessions", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bobSession));
    }

    @Test
    void softDeleteRetainsOwnedSessionHistoryAndControlsButNotActiveReports() throws Exception {
        call(alice, new Endpoint(HttpMethod.DELETE, "/activities/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(content().string("Activity deleted successfully."));
        call(alice, new Endpoint(HttpMethod.GET, "/activities/" + aliceActivity, null)).andExpect(status().isNotFound());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity + "/date/" + today, null))
                .andExpect(status().isOk());
        call(alice, new Endpoint(HttpMethod.PUT, "/api/sessions/" + aliceSession + "/resume", null)).andExpect(status().isOk());
        call(alice, new Endpoint(HttpMethod.POST, "/api/sessions/" + aliceSession + "/break",
                "{\"description\":\"Break after activity soft deletion\"}"))
                .andExpect(status().isCreated());
        call(alice, new Endpoint(HttpMethod.PUT, "/api/sessions/" + aliceSession + "/stop", null)).andExpect(status().isOk());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/" + aliceSession, null)).andExpect(status().isOk());
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/" + aliceSession + "/breaks", null)).andExpect(status().isOk());
        call(alice, new Endpoint(HttpMethod.POST, "/api/sessions/start", "{\"activityId\":" + aliceActivity + "}"))
                .andExpect(status().isNotFound());
        call(alice, new Endpoint(HttpMethod.GET, "/api/daily-progress/activity/" + aliceActivity + "/date/" + today, null))
                .andExpect(status().isNotFound());
        call(alice, new Endpoint(HttpMethod.GET, "/api/streaks/activity/" + aliceActivity, null)).andExpect(status().isNotFound());
        call(alice, new Endpoint(HttpMethod.GET, "/api/reports/activity/" + aliceActivity + "/month/" + YearMonth.from(today), null))
                .andExpect(status().isNotFound());
        call(bob, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity, null)).andExpect(status().isNotFound());
        call(alice, new Endpoint(HttpMethod.DELETE, "/api/sessions/" + aliceSession, null)).andExpect(status().isNoContent());
        assertThat(sessions.existsById(aliceSession)).isFalse();
        assertThat(breaks.findBySessionIdAndSessionActivityOwnerId(aliceSession, alice.id())).isEmpty();
    }

    @Test
    void ownedDailyAndMonthlyCalculationsPreserveBreakSubtraction() throws Exception {
        LocalDate yesterday = today.minusDays(1);
        Activity activity = activities.findById(aliceActivity).orElseThrow();
        Session finished = new Session();
        finished.setActivity(activity);
        finished.setStartTime(yesterday.atTime(8, 0));
        finished.setEndTime(yesterday.atTime(9, 0));
        finished.setDuration(2700L);
        sessions.saveAndFlush(finished);
        SessionBreak pause = new SessionBreak();
        pause.setSession(finished);
        pause.setStartTime(yesterday.atTime(8, 15));
        pause.setEndTime(yesterday.atTime(8, 30));
        pause.setDuration(900L);
        breaks.saveAndFlush(pause);
        call(alice, new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + aliceActivity + "/date/" + yesterday, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalDuration").value(2700));
        call(alice, new Endpoint(HttpMethod.GET, "/api/daily-progress/activity/" + aliceActivity + "/date/" + yesterday, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalDuration").value(2700))
                .andExpect(jsonPath("$.completed").value(true));
        call(alice, new Endpoint(HttpMethod.GET, "/api/streaks/activity/" + aliceActivity, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStreak").value(1));
        call(alice, new Endpoint(HttpMethod.GET, "/api/reports/activity/" + aliceActivity + "/month/" + YearMonth.from(yesterday), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalDuration").value(2700))
                .andExpect(jsonPath("$.totalSessions").value(1)).andExpect(jsonPath("$.longestStreak").value(1));
    }

    private Account register(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"displayName\":\"" + prefix +
                                "\",\"password\":\"Test-Password-42!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(JsonPath.read(response, "$.token"), ((Number) JsonPath.read(response, "$.user.id")).longValue());
    }

    private long createActivity(Account account, String name) throws Exception {
        String response = call(account, new Endpoint(HttpMethod.POST, "/activities", activityBody(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long startSession(Account account, long activityId) throws Exception {
        String response = call(account, new Endpoint(HttpMethod.POST, "/api/sessions/start", "{\"activityId\":" + activityId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions call(Account account, Endpoint endpoint) throws Exception {
        return mvc.perform(build(endpoint).header("Authorization", "Bearer " + account.token()));
    }

    private MockHttpServletRequestBuilder build(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        return builder;
    }

    private String activityBody(String name) {
        return """
                {"name":"%s","minimumDuration":1,"category":"STUDY",
                "startDate":"%s","scheduledDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]}
                """.formatted(name, today.minusDays(1));
    }

    private List<Endpoint> resourceOperations(long activityId, long sessionId) {
        return List.of(
                new Endpoint(HttpMethod.GET, "/activities/" + activityId, null),
                new Endpoint(HttpMethod.PUT, "/activities/" + activityId, activityBody("Attempted replacement")),
                new Endpoint(HttpMethod.DELETE, "/activities/" + activityId, null),
                new Endpoint(HttpMethod.POST, "/api/sessions/start", "{\"activityId\":" + activityId + "}"),
                new Endpoint(HttpMethod.GET, "/api/sessions/" + sessionId, null),
                new Endpoint(HttpMethod.PUT, "/api/sessions/" + sessionId + "/stop", null),
                new Endpoint(HttpMethod.DELETE, "/api/sessions/" + sessionId, null),
                new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + activityId, null),
                new Endpoint(HttpMethod.GET, "/api/sessions/activity/" + activityId + "/date/" + today, null),
                new Endpoint(HttpMethod.POST, "/api/sessions/" + sessionId + "/break", "{\"description\":\"Foreign note\"}"),
                new Endpoint(HttpMethod.PUT, "/api/sessions/" + sessionId + "/resume", null),
                new Endpoint(HttpMethod.GET, "/api/sessions/" + sessionId + "/breaks", null),
                new Endpoint(HttpMethod.GET, "/api/daily-progress/activity/" + activityId + "/date/" + today, null),
                new Endpoint(HttpMethod.GET, "/api/streaks/activity/" + activityId, null),
                new Endpoint(HttpMethod.GET, "/api/reports/activity/" + activityId + "/month/" + YearMonth.from(today), null)
        );
    }
}
