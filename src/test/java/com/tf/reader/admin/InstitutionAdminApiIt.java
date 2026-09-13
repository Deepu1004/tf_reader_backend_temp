package com.tf.reader.admin;

import tools.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.repository.AdminSessionRepository;
import com.tf.reader.admin.repository.AdminUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the admin institution endpoints end to end against a real database and the real security
 * setup: who can see what, who can write what, and whether suspending an institution actually
 * shows up on the public list.
 *
 * <p>Each request carries a real, signed {@code Authorization: Bearer <jwt>} header — this test
 * makes actual HTTP calls to the embedded server, handled on Tomcat's own worker threads, so
 * putting an {@code Authentication} into {@code SecurityContextHolder} from the test's own thread
 * has no effect on the request the server actually receives. The token has to travel with the
 * request itself.
 *
 * <p>The token comes from the real {@code POST /api/admin/v1/auth/login}, not a hand-signed JWT:
 * the admin decoder requires a live {@code adminSessions} row ({@code ActiveSessionValidator}),
 * which nothing but the real issuance path can produce, plus claims (a session id, {@code
 * scopeInstitutionId} rather than a bare {@code institutionId}) an earlier version of this test
 * did not have signed against the wrong secret in the first place.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Spring Boot 4 no longer auto-registers TestRestTemplate for a RANDOM_PORT test - it now needs
// this annotation explicitly, the same way MockMvc needs @AutoConfigureMockMvc.
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
@Testcontainers
class InstitutionAdminApiIT {

    private static final String PASSWORD = "Correct#Horse#Battery1";

    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("tnf.seed.enabled", () -> "true");
    }

    @Autowired TestRestTemplate http;
    @Autowired AdminUserRepository adminUsers;
    @Autowired AdminSessionRepository adminSessions;
    @Autowired PasswordEncoder passwordEncoder;

    private int nextAdminId;

    @BeforeEach
    void resetAdmins() {
        // The seeded institutions/publishers stay put (that is what this class tests); only the
        // admin accounts and sessions this class mints itself are ours to clean up.
        adminUsers.deleteAll();
        adminSessions.deleteAll();
        nextAdminId = 0;
    }

    /** Creates a real admin, logs in through the real endpoint, and returns the access token. */
    private String tokenFor(AdminRole role, String institutionId) {
        String email = "it-admin-" + (nextAdminId++) + "@example.com";
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setName("Institution Admin API IT admin");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole(role);
        admin.setInstitutionId(institutionId);
        admin.setStatus(AdminStatus.ACTIVE);
        adminUsers.save(admin);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        ResponseEntity<JsonNode> response = http.exchange("/api/admin/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("Expected login to succeed but got " + response.getStatusCode());
        }
        return response.getBody().get("accessToken").asString();
    }

    private static HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private ResponseEntity<JsonNode> get(String path, AdminRole role, String institutionId) {
        return http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(tokenFor(role, institutionId))), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getPublic(String path) {
        return http.getForEntity(path, JsonNode.class);
    }

    // ---------------------------------------------------------------------------------- listing

    @Test
    @DisplayName("the admin list shows every seeded institution including the suspended one")
    void adminListShowsAllThreeIncludingSuspended() {
        JsonNode body = get("/api/admin/v1/institutions", AdminRole.SUPER_ADMIN, null).getBody();

        assertThat(body.get("total").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("the public list still shows only the active institutions")
    void publicListStillShowsTwo() {
        // No sign-in needed for this one — it is open to anyone.
        JsonNode body = getPublic("/api/v1/institutions").getBody();
        assertThat(body.get("total").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("an institution admin's list is filtered to their own institution, not every row")
    void institutionAdminListIsScopedToOneRow() {
        JsonNode body = get("/api/admin/v1/institutions", AdminRole.INSTITUTION_ADMIN, "inst_7f3").getBody();

        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("id").asString()).isEqualTo("inst_7f3");
    }

    // ------------------------------------------------------------------------------- role scoping

    @Test
    @DisplayName("a super admin can reach any institution")
    void superAdminReachesAnyInstitution() {
        assertThat(get("/api/admin/v1/institutions/inst_ucl", AdminRole.SUPER_ADMIN, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an institution admin is refused outside their own institution")
    void institutionAdminIsScopedToItsOwnRow() {
        String accessToken = tokenFor(AdminRole.INSTITUTION_ADMIN, "inst_7f3");

        assertThat(http.exchange("/api/admin/v1/institutions/inst_ucl", HttpMethod.GET,
                        new HttpEntity<>(null, authHeaders(accessToken)), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/api/admin/v1/institutions/inst_7f3", HttpMethod.GET,
                        new HttpEntity<>(null, authHeaders(accessToken)), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a publisher admin is refused on the list, not shown an empty page")
    void publisherAdminIsRejectedOutright() {
        assertThat(get("/api/admin/v1/institutions", AdminRole.PUBLISHER_ADMIN, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("only a super admin can create an institution")
    void onlySuperAdminCanCreate() {
        HttpHeaders headers = authHeaders(tokenFor(AdminRole.INSTITUTION_ADMIN, "inst_7f3"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"cam\",\"name\":\"Cambridge\",\"type\":\"ACADEMIC\",\"country\":\"UK\"}";

        ResponseEntity<JsonNode> response =
                http.exchange("/api/admin/v1/institutions", HttpMethod.POST,
                        new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------------------- status + version

    @Test
    @DisplayName("suspending an institution raises its version and removes it from the public list")
    void suspendingBumpsVersionAndAffectsThePublicList() {
        int before = getPublic("/api/v1/institutions").getBody().get("total").asInt();

        HttpHeaders headers = authHeaders(tokenFor(AdminRole.SUPER_ADMIN, null));
        headers.setContentType(MediaType.APPLICATION_JSON);
        http.exchange("/api/admin/v1/institutions/inst_ucl/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"SUSPENDED\",\"reason\":\"integration test\"}", headers),
                JsonNode.class);

        JsonNode afterAdmin = get("/api/admin/v1/institutions/inst_ucl", AdminRole.SUPER_ADMIN, null).getBody();
        assertThat(afterAdmin.get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(afterAdmin.get("catalogueVersion").asInt()).isGreaterThan(1);

        int after = getPublic("/api/v1/institutions").getBody().get("total").asInt();
        assertThat(after).isEqualTo(before - 1);

        // Put it back — the seed only fills in missing rows, it does not undo edits.
        HttpHeaders restoreHeaders = authHeaders(tokenFor(AdminRole.SUPER_ADMIN, null));
        restoreHeaders.setContentType(MediaType.APPLICATION_JSON);
        http.exchange("/api/admin/v1/institutions/inst_ucl/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"ACTIVE\"}", restoreHeaders), JsonNode.class);
    }
}
