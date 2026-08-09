package com.ims.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
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
@RequestMapping("/api/claims")
public class ClaimController {
    record SubmitRequest(@NotNull UUID policyId, @NotBlank @Size(max=2000) String description,
                         @NotNull @DecimalMin("0.01") BigDecimal amount) {}
    record TransitionRequest(@NotNull ClaimStatus status, @Size(max=2000) String notes) {}
    record ClaimView(UUID id, UUID policyId, UUID customerId, String description, BigDecimal amount, ClaimStatus status,
                     String adminNotes, String customerName, String planName) {
        static ClaimView from(Claim c, UserRepository users, PlanRepository plans, PolicyRepository policies) {
            String customerName=users.findById(c.customerId).map(u -> u.displayName).orElse(null);
            String planName=policies.findById(c.policyId).flatMap(p -> plans.findById(p.planId).map(pl -> pl.name)).orElse(null);
            return new ClaimView(c.id,c.policyId,c.customerId,c.description,c.amount,c.status,c.adminNotes,customerName,planName);
        }
    }
    private final ClaimRepository claims; private final PolicyRepository policies; private final UserRepository users;
    private final PlanRepository plans; private final OutboxRepository outbox;
    private final ObjectMapper json; private final MeterRegistry metrics;
    private final boolean outboxEnabled;
    ClaimController(ClaimRepository claims, PolicyRepository policies, UserRepository users, PlanRepository plans,
                    OutboxRepository outbox, ObjectMapper json, MeterRegistry metrics,
                    @Value("${ims.outbox.enabled:true}") boolean outboxEnabled) {
        this.claims=claims; this.policies=policies; this.users=users; this.plans=plans; this.outbox=outbox;
        this.json=json; this.metrics=metrics; this.outboxEnabled=outboxEnabled;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional @PreAuthorize("hasRole('CUSTOMER')")
    ClaimView submit(@Valid @RequestBody SubmitRequest r, @AuthenticationPrincipal ImsPrincipal principal) {
        Policy policy=policies.findById(r.policyId()).orElseThrow(() -> new NotFoundException("Policy not found"));
        if (principal.role()!=Role.ADMIN && !policy.customerId.equals(principal.id())) throw new AccessDeniedException("Not policy owner");
        if (!"ACTIVE".equals(policy.status)) throw new InvalidStateException("Policy is not active");
        Claim claim=claims.save(new Claim(policy.id,policy.customerId,r.description(),r.amount()));
        event(claim,"ClaimSubmitted",null);
        metrics.counter("ims.claims.submitted").increment();
        return ClaimView.from(claim,users,plans,policies);
    }
    @GetMapping
    List<ClaimView> list(@AuthenticationPrincipal ImsPrincipal principal) {
        List<Claim> found=principal.role()==Role.ADMIN
            ? claims.findAll()
            : claims.findByCustomerIdOrderByCreatedAtDesc(principal.id());
        return found.stream().map(c -> ClaimView.from(c,users,plans,policies)).toList();
    }
    @GetMapping("/{id}")
    ClaimView one(@PathVariable UUID id, @AuthenticationPrincipal ImsPrincipal principal) {
        Claim c=claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        requireOwnerOrAdmin(c,principal); return ClaimView.from(c,users,plans,policies);
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasRole('ADMIN')") @Transactional
    ClaimView transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest r) {
        Claim c=claims.findById(id).orElseThrow(() -> new NotFoundException("Claim not found"));
        ClaimStatus previous=c.status; c.applyTransition(r.status(),r.notes()); event(c,"ClaimStatusChanged",r.notes());
        metrics.counter("ims.claims.transitions","from",previous.name(),"to",c.status.name()).increment();
        return ClaimView.from(c,users,plans,policies);
    }
    private void requireOwnerOrAdmin(Claim c, ImsPrincipal p) {
        if (p.role()!=Role.ADMIN && !c.customerId.equals(p.id())) throw new AccessDeniedException("Not claim owner");
    }
    private void event(Claim c, String type, String notes) {
        if (!outboxEnabled) return;
        try {
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("claimId",c.id); payload.put("policyId",c.policyId);
            payload.put("customerId",c.customerId); payload.put("status",c.status.name());
            if (notes != null && !notes.isBlank()) payload.put("notes",notes);
            outbox.save(new OutboxEvent("Claim",c.id,type,json.writeValueAsString(payload)));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
