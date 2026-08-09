package com.ims.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
public class PlanController {
    record PlanRequest(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=120) String name,
                       @NotNull @DecimalMin("0.00") BigDecimal premium) {}
    record PlanView(UUID id, String code, String name, BigDecimal premium, boolean active) {
        static PlanView from(Plan p) { return new PlanView(p.id,p.code,p.name,p.premium,p.active); }
    }
    private final PlanRepository plans;
    PlanController(PlanRepository plans) { this.plans=plans; }
    @GetMapping List<PlanView> list() { return plans.findByActiveTrue().stream().map(PlanView::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    PlanView create(@Valid @RequestBody PlanRequest r) {
        return PlanView.from(plans.save(new Plan(r.code(),r.name(),r.premium())));
    }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    PlanView update(@PathVariable UUID id, @Valid @RequestBody PlanRequest r) {
        Plan p=plans.findById(id).orElseThrow(() -> new NotFoundException("Plan not found"));
        p.code=r.code(); p.name=r.name(); p.premium=r.premium();
        return PlanView.from(p);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Transactional
    void deactivate(@PathVariable UUID id) {
        Plan p=plans.findById(id).orElseThrow(() -> new NotFoundException("Plan not found")); p.active=false;
    }
}
