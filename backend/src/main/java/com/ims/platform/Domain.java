package com.ims.platform;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

enum Role { CUSTOMER, ADMIN }
enum ClaimStatus { SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, PAID }

@Entity
@Table(name = "app_user")
class UserAccount {
    @Id UUID id;
    @Column(nullable=false, unique=true) String email;
    @Column(name="password_hash", nullable=false) String passwordHash;
    @Column(name="display_name", nullable=false) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable=false) Role role;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected UserAccount() {}
    UserAccount(String email, String passwordHash, String displayName, Role role) {
        this.id=UUID.randomUUID(); this.email=email.toLowerCase(); this.passwordHash=passwordHash;
        this.displayName=displayName; this.role=role; this.createdAt=Instant.now();
    }
}

@Entity
class Plan {
    @Id UUID id;
    @Column(nullable=false, unique=true) String code;
    @Column(nullable=false) String name;
    @Column(nullable=false) BigDecimal premium;
    @Column(nullable=false) boolean active;
    protected Plan() {}
    Plan(String code, String name, BigDecimal premium) {
        id=UUID.randomUUID(); this.code=code; this.name=name; this.premium=premium; active=true;
    }
}

@Entity
class Policy {
    @Id UUID id;
    @Column(name="policy_number", nullable=false, unique=true) String policyNumber;
    @Column(name="customer_id", nullable=false) UUID customerId;
    @Column(name="plan_id", nullable=false) UUID planId;
    @Column(nullable=false) String status;
    @Column(name="purchased_at", nullable=false) Instant purchasedAt;
    protected Policy() {}
    Policy(UUID customerId, UUID planId) {
        id=UUID.randomUUID(); policyNumber="POL-"+id.toString().substring(0,8).toUpperCase();
        this.customerId=customerId; this.planId=planId; status="ACTIVE"; purchasedAt=Instant.now();
    }
}

@Entity
class Payment {
    @Id UUID id;
    @Column(name="policy_id", nullable=false) UUID policyId;
    @Column(nullable=false) BigDecimal amount;
    @Column(name="provider_reference", nullable=false, unique=true) String providerReference;
    @Column(nullable=false) String status;
    @Column(name="paid_at") Instant paidAt;
    protected Payment() {}
    Payment(UUID policyId, BigDecimal amount, String reference) {
        id=UUID.randomUUID(); this.policyId=policyId; this.amount=amount;
        providerReference=reference; status="RECORDED"; paidAt=Instant.now();
    }
}

@Entity
class Claim {
    @Id UUID id;
    @Column(name="policy_id", nullable=false) UUID policyId;
    @Column(name="customer_id", nullable=false) UUID customerId;
    @Column(nullable=false, length=2000) String description;
    @Column(nullable=false) BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false) ClaimStatus status;
    @Column(name="created_at", nullable=false) Instant createdAt;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
    @Column(name="admin_notes", length=2000) String adminNotes;
    @Version long version;
    protected Claim() {}
    Claim(UUID policyId, UUID customerId, String description, BigDecimal amount) {
        id=UUID.randomUUID(); this.policyId=policyId; this.customerId=customerId;
        this.description=description; this.amount=amount; status=ClaimStatus.SUBMITTED;
        createdAt=updatedAt=Instant.now();
    }
    void transitionTo(ClaimStatus next) {
        boolean valid = switch (status) {
            case SUBMITTED -> next == ClaimStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> next == ClaimStatus.APPROVED || next == ClaimStatus.REJECTED;
            case APPROVED -> next == ClaimStatus.PAID;
            case REJECTED, PAID -> false;
        };
        if (!valid) throw new InvalidStateException("Claim cannot transition from "+status+" to "+next);
        status=next; updatedAt=Instant.now();
    }
    void applyTransition(ClaimStatus next, String notes) {
        transitionTo(next);
        if (notes != null && !notes.isBlank()) adminNotes=notes.trim();
    }
}

@Entity
@Table(name="notification_log")
class NotificationLog {
    @Id UUID id;
    @Column(name="event_id", nullable=false, unique=true) UUID eventId;
    @Column(name="event_type", nullable=false) String eventType;
    @Column(name="recipient_user_id") UUID recipientUserId;
    @Column(nullable=false) String channel;
    @Column(nullable=false) String subject;
    @Column(nullable=false, columnDefinition="text") String body;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected NotificationLog() {}
    NotificationLog(UUID eventId, String eventType, UUID recipientUserId, String channel, String subject, String body) {
        id=UUID.randomUUID(); this.eventId=eventId; this.eventType=eventType;
        this.recipientUserId=recipientUserId; this.channel=channel; this.subject=subject;
        this.body=body; createdAt=Instant.now();
    }
}

@Entity
@Table(name="outbox_event")
class OutboxEvent {
    @Id UUID id;
    @Column(name="aggregate_type", nullable=false) String aggregateType;
    @Column(name="aggregate_id", nullable=false) UUID aggregateId;
    @Column(name="event_type", nullable=false) String eventType;
    @Column(nullable=false, columnDefinition="text") String payload;
    @Column(name="occurred_at", nullable=false) Instant occurredAt;
    @Column(name="published_at") Instant publishedAt;
    protected OutboxEvent() {}
    OutboxEvent(String type, UUID aggregateId, String eventType, String payload) {
        id=UUID.randomUUID(); aggregateType=type; this.aggregateId=aggregateId;
        this.eventType=eventType; this.payload=payload; occurredAt=Instant.now();
    }
}

@Entity
@Table(name="consumed_event")
@IdClass(ConsumedEvent.Id.class)
class ConsumedEvent {
    @jakarta.persistence.Id @Column(name="consumer_name") String consumerName;
    @jakarta.persistence.Id @Column(name="event_id") UUID eventId;
    @Column(name="consumed_at", nullable=false) Instant consumedAt;
    protected ConsumedEvent() {}
    ConsumedEvent(String name, UUID id) { consumerName=name; eventId=id; consumedAt=Instant.now(); }
    static class Id implements java.io.Serializable {
        String consumerName; UUID eventId;
        public Id() {}
        public Id(String n, UUID i) { consumerName=n; eventId=i; }
        public boolean equals(Object o) { return o instanceof Id x && consumerName.equals(x.consumerName) && eventId.equals(x.eventId); }
        public int hashCode() { return java.util.Objects.hash(consumerName,eventId); }
    }
}

@Entity
@Table(name="audit_event")
class AuditEvent {
    @Id UUID id;
    @Column(name="event_id", nullable=false, unique=true) UUID eventId;
    @Column(name="event_type", nullable=false) String eventType;
    @Column(nullable=false, columnDefinition="text") String payload;
    @Column(name="recorded_at", nullable=false) Instant recordedAt;
    protected AuditEvent() {}
    AuditEvent(UUID eventId, String eventType, String payload) {
        id=UUID.randomUUID(); this.eventId=eventId; this.eventType=eventType;
        this.payload=payload; recordedAt=Instant.now();
    }
}

class InvalidStateException extends RuntimeException {
    InvalidStateException(String message) { super(message); }
}
class NotFoundException extends RuntimeException {
    NotFoundException(String message) { super(message); }
}
