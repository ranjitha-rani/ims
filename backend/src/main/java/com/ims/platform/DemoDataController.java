package com.ims.platform;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/demo")
@ConditionalOnProperty(name="ims.demo.enabled", havingValue="true")
public class DemoDataController {
    private static final List<String> DEMO_EMAILS=List.of(
        "priya.sharma@example.com","james.wilson@example.com","aisha.khan@example.com");
    private static final List<Object[]> PLANS=List.of(
        new Object[]{"HEALTH-BASIC","Health Shield Basic",new BigDecimal("49.99")},
        new Object[]{"AUTO-PLUS","Auto Protect Plus",new BigDecimal("79.50")},
        new Object[]{"HOME-PREMIUM","HomeGuard Premium",new BigDecimal("129.00")},
        new Object[]{"TRAVEL-FLEX","Travel Flex Cover",new BigDecimal("34.00")});
    private static final List<Object[]> CUSTOMERS=List.of(
        new Object[]{"priya.sharma@example.com","Priya Sharma"},
        new Object[]{"james.wilson@example.com","James Wilson"},
        new Object[]{"aisha.khan@example.com","Aisha Khan"});

    private final PlanRepository plans; private final UserRepository users; private final PolicyRepository policies;
    private final PaymentRepository payments; private final ClaimRepository claims; private final PasswordEncoder passwords;
    private final String customerPassword;
    DemoDataController(PlanRepository plans, UserRepository users, PolicyRepository policies, PaymentRepository payments,
                       ClaimRepository claims, PasswordEncoder passwords,
                       @Value("${ims.demo.customer-password:CustomerDemo12!}") String customerPassword) {
        this.plans=plans; this.users=users; this.policies=policies; this.payments=payments; this.claims=claims;
        this.passwords=passwords; this.customerPassword=customerPassword;
    }

    @PostMapping("/seed") @PreAuthorize("hasRole('ADMIN')") @Transactional
    Map<String,Object> seed() {
        int plansCreated=ensurePlans();
        int customersCreated=ensureCustomers();
        int policiesCreated=ensurePolicies();
        int claimsCreated=ensureClaims();
        return Map.of("plansCreated",plansCreated,"customersCreated",customersCreated,
            "policiesCreated",policiesCreated,"claimsCreated",claimsCreated);
    }

    @PostMapping("/reset") @PreAuthorize("hasRole('ADMIN')") @Transactional
    Map<String,Object> reset() {
        long claimsDeleted=claims.count();
        long paymentsDeleted=payments.count();
        long policiesDeleted=policies.count();
        claims.deleteAll(); payments.deleteAll(); policies.deleteAll();
        int customersDeleted=0;
        for (String email:DEMO_EMAILS) {
            var user=users.findByEmailIgnoreCase(email);
            if (user.isPresent() && user.get().role==Role.CUSTOMER) { users.delete(user.get()); customersDeleted++; }
        }
        int plansDeactivated=0;
        for (Plan p:plans.findAll()) { if (p.active) { p.active=false; plansDeactivated++; } }
        Map<String,Object> seeded=seed();
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("claimsDeleted",claimsDeleted); summary.put("paymentsDeleted",paymentsDeleted);
        summary.put("policiesDeleted",policiesDeleted); summary.put("customersDeleted",customersDeleted);
        summary.put("plansDeactivated",plansDeactivated); summary.putAll(seeded);
        return summary;
    }

    private int ensurePlans() {
        int created=0;
        for (Object[] spec:PLANS) {
            var existing=plans.findByCode((String)spec[0]);
            if (existing.isPresent()) existing.get().active=true;
            else { plans.save(new Plan((String)spec[0],(String)spec[1],(BigDecimal)spec[2])); created++; }
        }
        return created;
    }

    private int ensureCustomers() {
        int created=0;
        for (Object[] spec:CUSTOMERS) {
            if (users.findByEmailIgnoreCase((String)spec[0]).isEmpty()) {
                users.save(new UserAccount((String)spec[0],passwords.encode(customerPassword),(String)spec[1],Role.CUSTOMER));
                created++;
            }
        }
        return created;
    }

    private int ensurePolicies() {
        Map<String,UUID> planIds=new HashMap<>();
        for (Object[] spec:PLANS) plans.findByCode((String)spec[0]).ifPresent(p -> planIds.put((String)spec[0],p.id));
        int created=0;
        UserAccount priya=users.findByEmailIgnoreCase("priya.sharma@example.com").orElseThrow();
        UserAccount james=users.findByEmailIgnoreCase("james.wilson@example.com").orElseThrow();
        UserAccount aisha=users.findByEmailIgnoreCase("aisha.khan@example.com").orElseThrow();
        created+=ensurePolicy(priya,planIds.get("HEALTH-BASIC"));
        created+=ensurePolicy(priya,planIds.get("TRAVEL-FLEX"));
        created+=ensurePolicy(james,planIds.get("AUTO-PLUS"));
        created+=ensurePolicy(aisha,planIds.get("HOME-PREMIUM"));
        created+=ensurePolicy(aisha,planIds.get("HEALTH-BASIC"));
        return created;
    }

    private int ensurePolicy(UserAccount customer, UUID planId) {
        if (planId==null) return 0;
        boolean exists=policies.findByCustomerId(customer.id).stream().anyMatch(p -> p.planId.equals(planId));
        if (exists) return 0;
        Policy policy=policies.save(new Policy(customer.id,planId));
        Plan plan=plans.findById(planId).orElseThrow();
        payments.save(new Payment(policy.id,plan.premium,"IMS-DEMO-"+UUID.randomUUID()));
        return 1;
    }

    private int ensureClaims() {
        int created=0;
        created+=ensureClaim("priya.sharma@example.com","HEALTH-BASIC",
            "Outpatient consultation and diagnostic tests after fever and fatigue.",new BigDecimal("220.00"),ClaimStatus.UNDER_REVIEW);
        created+=ensureClaim("james.wilson@example.com","AUTO-PLUS",
            "Front bumper repair after low-speed parking collision.",new BigDecimal("875.50"),ClaimStatus.SUBMITTED);
        created+=ensureClaim("aisha.khan@example.com","HOME-PREMIUM",
            "Water damage to kitchen cabinets from burst supply line.",new BigDecimal("1450.00"),ClaimStatus.SUBMITTED);
        return created;
    }

    private int ensureClaim(String email, String planCode, String description, BigDecimal amount, ClaimStatus target) {
        UserAccount customer=users.findByEmailIgnoreCase(email).orElseThrow();
        UUID planId=plans.findByCode(planCode).map(p -> p.id).orElseThrow();
        Policy policy=policies.findByCustomerId(customer.id).stream().filter(p -> p.planId.equals(planId)).findFirst().orElseThrow();
        if (!claims.findByCustomerId(customer.id).isEmpty()) return 0;
        Claim claim=claims.save(new Claim(policy.id,customer.id,description,amount));
        if (target!=ClaimStatus.SUBMITTED) claim.applyTransition(target,null);
        return 1;
    }
}
