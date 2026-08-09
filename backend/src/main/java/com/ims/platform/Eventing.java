package com.ims.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.FixedBackOff;

record EventEnvelope(UUID id, String aggregateType, UUID aggregateId, String eventType,
                     String payload, Instant occurredAt) {}

@Configuration
@ConditionalOnProperty(name="ims.kafka.enabled",havingValue="true",matchIfMissing=true)
class KafkaConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
        DeadLetterPublishingRecoverer recoverer=new DeadLetterPublishingRecoverer(template,
            (record,error) -> new TopicPartition(record.topic()+".DLT",record.partition()));
        return new DefaultErrorHandler(recoverer,new FixedBackOff(1_000L,3));
    }
}

@Component
@ConditionalOnProperty(name="ims.kafka.enabled",havingValue="true",matchIfMissing=true)
class OutboxPublisher {
    private final OutboxRepository outbox; private final KafkaTemplate<String,String> kafka; private final ObjectMapper json;
    OutboxPublisher(OutboxRepository outbox,KafkaTemplate<String,String> kafka,ObjectMapper json) {
        this.outbox=outbox; this.kafka=kafka; this.json=json;
    }
    @Scheduled(fixedDelayString="${ims.outbox.poll-delay:1000}") @Transactional
    void publish() throws Exception {
        for (OutboxEvent e:outbox.unpublished()) {
            EventEnvelope envelope=new EventEnvelope(e.id,e.aggregateType,e.aggregateId,e.eventType,e.payload,e.occurredAt);
            kafka.send("ims.domain-events",e.aggregateId.toString(),json.writeValueAsString(envelope)).get();
            e.publishedAt=Instant.now();
        }
    }
}

@Component
class OutboxMetrics {
    OutboxMetrics(OutboxRepository outbox,MeterRegistry metrics) {
        Gauge.builder("ims.outbox.unpublished",outbox,repository -> repository.countByPublishedAtIsNull())
            .description("Number of transactional outbox events waiting for publication")
            .register(metrics);
    }
}

@Component
@ConditionalOnProperty(name="ims.kafka.enabled",havingValue="true",matchIfMissing=true)
class DomainEventConsumers {
    private static final Logger log=LoggerFactory.getLogger(DomainEventConsumers.class);
    private final ObjectMapper json; private final ConsumedEventRepository consumed;
    private final AuditEventRepository audits; private final MeterRegistry metrics;
    private final ClaimRepository claims;
    private final NotificationLogRepository notifications;
    DomainEventConsumers(ObjectMapper json,ConsumedEventRepository consumed,AuditEventRepository audits,MeterRegistry metrics,
                         ClaimRepository claims,NotificationLogRepository notifications) {
        this.json=json; this.consumed=consumed; this.audits=audits; this.metrics=metrics;
        this.claims=claims; this.notifications=notifications;
    }
    @KafkaListener(topics="ims.domain-events",groupId="ims-claims-validation")
    @Transactional
    void validateClaim(ConsumerRecord<String,String> record) throws Exception {
        EventEnvelope e=json.readValue(record.value(),EventEnvelope.class);
        if (!e.eventType().startsWith("Claim") || already("claims-validation",e.id())) return;
        if ("ClaimSubmitted".equals(e.eventType())) {
            JsonNode payload=json.readTree(e.payload());
            UUID claimId=UUID.fromString(payload.get("claimId").asText());
            Claim claim=claims.findById(claimId).orElse(null);
            if (claim != null && claim.amount.compareTo(new java.math.BigDecimal("50000")) > 0) {
                log.warn("Claim {} flagged: amount {} exceeds threshold",claimId,claim.amount);
                metrics.counter("ims.claims.validation.flagged").increment();
            } else {
                log.info("Claim {} validation ok",claimId);
            }
        } else {
            log.info("Acknowledged claim event {}",e.eventType());
        }
        mark("claims-validation",e.id()); count("claims-validation",e.eventType());
    }
    @KafkaListener(topics="ims.domain-events",groupId="ims-notifications")
    @Transactional
    void notifyUser(ConsumerRecord<String,String> record) throws Exception {
        EventEnvelope e=json.readValue(record.value(),EventEnvelope.class);
        if (already("notifications",e.id())) return;
        if (!e.eventType().startsWith("Claim") && !"PolicyPurchased".equals(e.eventType())) return;
        JsonNode payload=json.readTree(e.payload());
        UUID recipient=payload.hasNonNull("customerId") ? UUID.fromString(payload.get("customerId").asText()) : null;
        String subject=e.eventType()+" notification";
        notifications.save(new NotificationLog(e.id(),e.eventType(),recipient,"LOG",subject,e.payload()));
        log.info("Logged notification for {} event {} recipient {}",e.eventType(),e.id(),recipient);
        mark("notifications",e.id()); count("notifications",e.eventType());
    }
    @KafkaListener(topics="ims.domain-events",groupId="ims-audit")
    @Transactional
    void audit(ConsumerRecord<String,String> record) throws Exception {
        EventEnvelope e=json.readValue(record.value(),EventEnvelope.class);
        if (already("audit",e.id())) return;
        audits.save(new AuditEvent(e.id(),e.eventType(),e.payload()));
        mark("audit",e.id()); count("audit",e.eventType());
    }
    private boolean already(String name,UUID id) { return consumed.existsById(new ConsumedEvent.Id(name,id)); }
    private void mark(String name,UUID id) { consumed.save(new ConsumedEvent(name,id)); }
    private void count(String consumer,String type) {
        metrics.counter("ims.kafka.events.consumed","consumer",consumer,"type",type).increment();
    }
}
