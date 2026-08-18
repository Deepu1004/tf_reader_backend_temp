package com.tf.reader.loan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.security.CurrentUserAuthenticationToken;
import com.tf.reader.loan.controller.LoanController;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.service.BorrowService;
import com.tf.reader.loan.service.BorrowService.BorrowResult;

/**
 * Controller-slice test for POST /api/v1/loans.
 *
 * <p>@WebMvcTest loads only the web layer. BorrowService and Clock are mocked.
 * Security filters are off; we set the CurrentUser in SecurityContextHolder directly,
 * which is how @AuthenticationPrincipal resolves the principal in stateless apps.
 */
@WebMvcTest(LoanController.class)
@AutoConfigureMockMvc(addFilters = false)
class BorrowEndpointTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean BorrowService borrowService;
	@MockitoBean Clock clock;

	private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");

	@BeforeEach
	void setUser() {
		runAs("user_1", "inst_1");
	}

	@AfterEach
	void clearUser() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void newLoan_returns201WithAllFields() throws Exception {
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
		when(borrowService.borrow("user_1", "inst_1", "item_42"))
				.thenReturn(new BorrowResult(fakeLoan("loan_abc123", "item_42"), true));

		mockMvc.perform(post("/api/v1/loans")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"itemId\": \"item_42\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.loanId").value("loan_abc123"))
				.andExpect(jsonPath("$.itemId").value("item_42"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.licenseModel").value("ELITE"))
				.andExpect(jsonPath("$.canPersist").value(false))
				.andExpect(jsonPath("$.serverTime").value("2026-08-18T09:00:00Z"));
	}

	@Test
	void secondTap_returns200WithSameLoan() throws Exception {
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
		when(borrowService.borrow(any(), any(), eq("item_42")))
				.thenReturn(new BorrowResult(fakeLoan("loan_existing", "item_42"), false));

		mockMvc.perform(post("/api/v1/loans")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"itemId\": \"item_42\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loanId").value("loan_existing"));
	}

	@Test
	void blankItemId_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/loans")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"itemId\": \"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void missingItemId_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/loans")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	// ── helpers ───────────────────────────────────────────────────────

	private static void runAs(String userId, String institutionId) {
		CurrentUser principal = new CurrentUser(userId, UserType.INSTITUTION, institutionId,
				List.of("MEMBER"), List.of("col_1"));
		var auth = new CurrentUserAuthenticationToken(principal, null, List.of());
		var ctx = SecurityContextHolder.createEmptyContext();
		ctx.setAuthentication(auth);
		SecurityContextHolder.setContext(ctx);
	}

	private static Loan fakeLoan(String loanId, String itemId) {
		return Loan.builder()
				.loanId(loanId).userId("user_1").itemId(itemId).institutionId("inst_1")
				.licenseModel(LicenseModel.ELITE).status(LoanStatus.ACTIVE)
				.canPersist(false).borrowedAt(NOW)
				.dueAt(NOW.plusSeconds(86400 * 14))
				.build();
	}
}
