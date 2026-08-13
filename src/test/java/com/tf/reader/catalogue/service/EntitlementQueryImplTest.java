package com.tf.reader.catalogue.service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EntitlementQueryImplTest {

    private final EntitlementQuery query = new EntitlementQueryImpl();

    @Test
    void allowsAccessOnTwoCopiesForFourteenDays() {
        EntitlementDecision decision = query.check(new SubjectRef("u_88", "inst_7f3"), "item_c25");

        assertThat(decision.entitled()).isTrue();
        assertThat(decision.copies()).isEqualTo(2);
        assertThat(decision.loanPeriodDays()).isEqualTo(14);
        assertThat(decision.accessLevel()).isEqualTo(AccessLevel.ENTITLED_CONCURRENT);
        assertThat(decision.entitlementId()).isNotBlank();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void rejectsAMissingItemId() {
        SubjectRef subject = new SubjectRef("u_88", "inst_7f3");

        assertThatIllegalArgumentException().isThrownBy(() -> query.check(subject, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> query.check(subject, null));
    }
}
