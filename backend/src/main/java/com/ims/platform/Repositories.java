package com.ims.platform;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByActiveTrue();
    Optional<Plan> findByCode(String code);
}
interface PolicyRepository extends JpaRepository<Policy, UUID> {
    List<Policy> findByCustomerId(UUID customerId);
}
interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByPolicyId(UUID policyId);
}
interface ClaimRepository extends JpaRepository<Claim, UUID> {
    List<Claim> findByCustomerId(UUID customerId);
}
interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<OutboxEvent> unpublished();
    long countByPublishedAtIsNull();
}
interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, ConsumedEvent.Id> {}
interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {}
