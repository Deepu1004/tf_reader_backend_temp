package com.tf.reader.admin;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import tools.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the admin institution endpoints end to end against a real database and the real security
 * setup: who can see what, who can write what, and whether suspending an institution actually
 * shows up on the public list.
 *
 * <p>Each request carries a real, signed {@code Authorization: Bearer <jwt>} header — this test
 * makes actual HTTP calls to the embedded server, handled on Tomcat's own worker threads, so
 * putting an {@code Authentication} into {@code SecurityContextHolder} from the test's own thread
 * (an earlier version of this test did that) has no effect on the request the server actually
 * receives. The token has to travel with the request itself. {@code tnf.jwt.secret} is overridden
 * to a known test value so a token minted here validates against this run's own decoder; the exact
 * claim names/algorithm need re-checking against the real {@code JwtService} once it exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class InstitutionAdminApiIT {

    private static final String TEST_JWT_SECRET = "test-only-secret-must-be-at-least-32-bytes-long!!";

    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("tnf.seed.enabled", () -> "true");
        registry.add("tnf.jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Autowired TestRestTemplate http;

    /** Mints a real, signed token for the given role, scoped to the given institution. */
    private static String tokenFor(String institutionId, String role) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject("u_test")
                    .audience("tf-admin")
                    .claim("role", role)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000));
            if (institutionId != null) {
                claims.claim("institutionId", institutionId);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(TEST_JWT_SECRET));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not mint a test token", e);
        }
    }

    private static HttpHeaders authHeaders(String institutionId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(institutionId, role));
        return headers;
    }

    private ResponseEntity<JsonNode> get(String path, String institutionId, String role) {
        return http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(institutionId, role)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getPublic(String path) {
        return http.getForEntity(path, JsonNode.class);
    }

    // ---------------------------------------------------------------------------------- listing

    @Test
    @DisplayName("the admin list shows every seeded institution including the suspended one")
    void adminListShowsAllThreeIncludingSuspended() {
        JsonNode body = get("/api/admin/v1/institutions", "inst_7f3", "SUPER_ADMIN").getBody();

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
        JsonNode body = get("/api/admin/v1/institutions", "inst_7f3", "INSTITUTION_ADMIN").getBody();

        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("id").asString()).isEqualTo("inst_7f3");
    }

    // ------------------------------------------------------------------------------- role scoping

    @Test
    @DisplayName("a super admin can reach any institution")
    void superAdminReachesAnyInstitution() {
        assertThat(get("/api/admin/v1/institutions/inst_ucl", "inst_7f3", "SUPER_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an institution admin is refused outside their own institution")
    void institutionAdminIsScopedToItsOwnRow() {
        assertThat(get("/api/admin/v1/institutions/inst_ucl", "inst_7f3", "INSTITUTION_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/admin/v1/institutions/inst_7f3", "inst_7f3", "INSTITUTION_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a publisher admin is refused on the list, not shown an empty page")
    void publisherAdminIsRejectedOutright() {
        assertThat(get("/api/admin/v1/institutions", null, "PUBLISHER_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("only a super admin can create an institution")
    void onlySuperAdminCanCreate() {
        HttpHeaders headers = authHeaders("inst_7f3", "INSTITUTION_ADMIN");
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

        HttpHeaders headers = authHeaders("inst_7f3", "SUPER_ADMIN");
        headers.setContentType(MediaType.APPLICATION_JSON);
        http.exchange("/api/admin/v1/institutions/inst_ucl/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"SUSPENDED\",\"reason\":\"integration test\"}", headers),
                JsonNode.class);

        JsonNode afterAdmin = get("/api/admin/v1/institutions/inst_ucl", "inst_7f3", "SUPER_ADMIN").getBody();
        assertThat(afterAdmin.get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(afterAdmin.get("catalogueVersion").asInt()).isGreaterThan(1);

        int after = getPublic("/api/v1/institutions").getBody().get("total").asInt();
        assertThat(after).isEqualTo(before - 1);

        // Put it back — the seed only fills in missing rows, it does not undo edits.
        http.exchange("/api/admin/v1/institutions/inst_ucl/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"ACTIVE\"}", headers), JsonNode.class);
    }
}