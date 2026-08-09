package com.ims.platform;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimStateMachineTest {
    @Test
    void followsHappyPathToPaid() {
        Claim claim=new Claim(UUID.randomUUID(),UUID.randomUUID(),"Storm damage",new BigDecimal("500.00"));
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        claim.transitionTo(ClaimStatus.APPROVED);
        claim.transitionTo(ClaimStatus.PAID);
        assertThat(claim.status).isEqualTo(ClaimStatus.PAID);
    }
    @Test
    void supportsRejectionAndMakesTerminalStatesFinal() {
        Claim claim=new Claim(UUID.randomUUID(),UUID.randomUUID(),"Damage",BigDecimal.TEN);
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        claim.transitionTo(ClaimStatus.REJECTED);
        assertThatThrownBy(() -> claim.transitionTo(ClaimStatus.APPROVED))
            .isInstanceOf(InvalidStateException.class);
    }
    @Test
    void rejectsSkippedApproval() {
        Claim claim=new Claim(UUID.randomUUID(),UUID.randomUUID(),"Damage",BigDecimal.TEN);
        assertThatThrownBy(() -> claim.transitionTo(ClaimStatus.PAID))
            .isInstanceOf(InvalidStateException.class)
            .hasMessageContaining("SUBMITTED");
    }
    @Test
    void applyTransitionSetsAdminNotes() {
        Claim claim=new Claim(UUID.randomUUID(),UUID.randomUUID(),"Damage",BigDecimal.TEN);
        claim.applyTransition(ClaimStatus.UNDER_REVIEW,"Reviewing documentation");
        assertThat(claim.status).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.adminNotes).isEqualTo("Reviewing documentation");
    }
    @Test
    void applyTransitionSkipsBlankNotes() {
        Claim claim=new Claim(UUID.randomUUID(),UUID.randomUUID(),"Damage",BigDecimal.TEN);
        claim.applyTransition(ClaimStatus.UNDER_REVIEW,"  ");
        assertThat(claim.adminNotes).isNull();
    }
}
