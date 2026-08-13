package com.tf.reader.admin.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.repository.AdminSessionRepository;
import com.tf.reader.admin.repository.AdminUserRepository;
import com.tf.reader.common.security.JwtProperties;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for the admin auth integration tests: a real HTTP stack, the real security filter
 * chains and a real MongoDB.
 *
 * <p>Both collections are cleared before each test so no test depends on another's leftovers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class AbstractAdminAuthIntegrationTest {

	protected static final String LOGIN_PATH = "/api/admin/v1/auth/login";
	protected static final String REFRESH_PATH = "/api/admin/v1/auth/refresh";
	protected static final String LOGOUT_PATH = "/api/admin/v1/auth/logout";
	protected static final String ME_PATH = "/api/admin/v1/auth/me";

	protected static final String PASSWORD = "Correct#Horse#Battery1";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected AdminUserRepository adminUserRepository;

	@Autowired
	protected AdminSessionRepository adminSessionRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtProperties jwtProperties;

	protected TestTokenFactory tokens;

	@BeforeEach
	void resetStateAndTokenFactory() {
		this.adminUserRepository.deleteAll();
		this.adminSessionRepository.deleteAll();
		this.tokens = new TestTokenFactory(this.jwtEncoder, this.jwtProperties.issuer());
	}

	protected AdminUser saveAdmin(String email, AdminRole role, AdminStatus status) {
		return saveAdmin(email, role, status, null, null);
	}

	protected AdminUser saveAdmin(String email, AdminRole role, AdminStatus status, String publisherId,
			String institutionId) {

		AdminUser adminUser = new AdminUser();
		adminUser.setEmail(email);
		adminUser.setName("Test Admin");
		adminUser.setPasswordHash(this.passwordEncoder.encode(PASSWORD));
		adminUser.setRole(role);
		adminUser.setPublisherId(publisherId);
		adminUser.setInstitutionId(institutionId);
		adminUser.setStatus(status);
		return this.adminUserRepository.save(adminUser);
	}

	protected MvcResult performLogin(String email, String password) throws Exception {
		return this.mockMvc.perform(json(post(LOGIN_PATH), """
				{"email": "%s", "password": "%s"}""".formatted(email, password))).andReturn();
	}

	/** Logs in and returns the issued tokens. Fails loudly if the login itself did not succeed. */
	protected AdminLoginResponse loginSuccessfully(String email) throws Exception {
		MvcResult result = performLogin(email, PASSWORD);
		if (result.getResponse().getStatus() != 200) {
			throw new IllegalStateException(
					"Expected login to succeed but got HTTP " + result.getResponse().getStatus());
		}
		return readLogin(result);
	}

	/** Reads a login response, which carries the token pair plus the signed-in admin. */
	protected AdminLoginResponse readLogin(MvcResult result) throws Exception {
		return this.objectMapper.readValue(result.getResponse().getContentAsString(),
				AdminLoginResponse.class);
	}

	/** Reads a refresh response, which is the token pair alone. */
	protected TokenPair readTokens(MvcResult result) throws Exception {
		return this.objectMapper.readValue(result.getResponse().getContentAsString(), TokenPair.class);
	}

	protected MvcResult callRefresh(String refreshToken) throws Exception {
		return this.mockMvc.perform(json(post(REFRESH_PATH), """
				{"refreshToken": "%s"}""".formatted(refreshToken))).andReturn();
	}

	protected MvcResult callMe(String accessToken) throws Exception {
		return this.mockMvc.perform(get(ME_PATH).header("Authorization", "Bearer " + accessToken)).andReturn();
	}

	/** Pushes a session's absolute expiry into the past, the one thing a client cannot do itself. */
	protected void expireSession(String sessionId) {
		AdminSession session = this.adminSessionRepository.findById(sessionId).orElseThrow();
		session.setExpiresAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(1)));
		this.adminSessionRepository.save(session);
	}

	/** Indistinguishable from a real refresh token, except that it was never issued. */
	protected static String randomOpaqueLookingToken() {
		byte[] tokenBytes = new byte[32];
		new java.security.SecureRandom().nextBytes(tokenBytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
	}

	/** The single session under test. Fails if a test accidentally opened more than one. */
	protected AdminSession onlySession() {
		java.util.List<AdminSession> sessions = this.adminSessionRepository.findAll();
		if (sessions.size() != 1) {
			throw new IllegalStateException("Expected exactly one session but found " + sessions.size());
		}
		return sessions.get(0);
	}

	/** Logout carries the refresh token in the body, not a bearer token. */
	protected MvcResult callLogout(String refreshToken) throws Exception {
		return this.mockMvc.perform(json(post(LOGOUT_PATH), """
				{"refreshToken": "%s"}""".formatted(refreshToken))).andReturn();
	}

	protected MvcResult callLogoutWithRawBody(String body) throws Exception {
		return this.mockMvc.perform(json(post(LOGOUT_PATH), body)).andReturn();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
