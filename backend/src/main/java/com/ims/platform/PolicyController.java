package com.ims.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    record PurchaseRequest(@NotNull UUID planId) {}
    record PolicyView(UUID id, String policyNumber, UUID customerId, UUID planId, String status,
                      String customerName, String planName) {
        static PolicyView from(Policy p, UserRepository users, PlanRepository plans) {
            String customerName=users.findById(p.customerId).map(u -> u.displayName).orElse(null);
            String planName=plans.findById(p.planId).map(pl -> pl.name).orElse(null);
            return new PolicyView(p.id,p.policyNumber,p.customerId,p.planId,p.status,customerName,planName);
        }
    }
    record PaymentView(UUID id, UUID policyId, BigDecimal amount, String providerReference, String status) {
        static PaymentView from(Payment p) { return new PaymentView(p.id,p.policyId,p.amount,p.providerReference,p.status); }
    }
    record PurchaseResponse(PolicyView policy, PaymentView payment) {}
    private final PolicyRepository policies; private final PlanRepository plans; private final UserRepository users;
    private final PaymentRepository payments; private final OutboxRepository outbox; private final ObjectMapper json;
    private final boolean outboxEnabled;
    PolicyController(PolicyRepository policies, PlanRepository plans, UserRepository users, PaymentRepository payments,
                     OutboxRepository outbox, ObjectMapper json,@Value("${ims.outbox.enabled:true}") boolean outboxEnabled) {
        this.policies=policies; this.plans=plans; this.users=users; this.payments=payments; this.outbox=outbox;
        this.json=json; this.outboxEnabled=outboxEnabled;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasRole('CUSTOMER')")
    PurchaseResponse purchase(@Valid @RequestBody PurchaseRequest r, @AuthenticationPrincipal ImsPrincipal principal) {
        Plan plan=plans.findById(r.planId()).filter(p -> p.active).orElseThrow(() -> new NotFoundException("Active plan not found"));
        Policy policy=policies.save(new Policy(principal.id(),plan.id));
        Payment payment=payments.save(new Payment(policy.id,plan.premium,"IMS-"+UUID.randomUUID()));
        if (outboxEnabled) {
            outbox.save(new OutboxEvent("Policy",policy.id,"PolicyPurchased",payload(Map.of(
                "policyId",policy.id,"customerId",policy.customerId,"planId",policy.planId))));
        }
        return new PurchaseResponse(PolicyView.from(policy,users,plans),PaymentView.from(payment));
    }
    @GetMapping
    List<PolicyView> list(@AuthenticationPrincipal ImsPrincipal principal) {
        List<Policy> found=principal.role()==Role.ADMIN
            ? policies.findAll()
            : policies.findByCustomerIdOrderByPurchasedAtDesc(principal.id());
        return found.stream().map(p -> PolicyView.from(p,users,plans)).toList();
    }
    @GetMapping("/{id}")
    PolicyView one(@PathVariable UUID id, @AuthenticationPrincipal ImsPrincipal principal) {
        Policy p=policies.findById(id).orElseThrow(() -> new NotFoundException("Policy not found"));
        requireOwnerOrAdmin(p.customerId,principal); return PolicyView.from(p,users,plans);
    }
    @GetMapping("/{id}/payments")
    List<PaymentView> payments(@PathVariable UUID id, @AuthenticationPrincipal ImsPrincipal principal) {
        Policy p=policies.findById(id).orElseThrow(() -> new NotFoundException("Policy not found"));
        requireOwnerOrAdmin(p.customerId,principal);
        return payments.findByPolicyId(id).stream().map(PaymentView::from).toList();
    }
    private void requireOwnerOrAdmin(UUID owner, ImsPrincipal p) {
        if (p.role()!=Role.ADMIN && !owner.equals(p.id())) throw new AccessDeniedException("Not policy owner");
    }
    private String payload(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
