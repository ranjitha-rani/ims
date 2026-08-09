package com.ims.platform;

import java.sql.Connection;
import java.time.Instant;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class StatusController {
    record StatusResponse(String status, String api, String database, String redis, String kafka, String timestamp) {}

    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final ProducerFactory<String,String> kafkaProducerFactory;
    private final boolean redisEnabled;
    private final boolean kafkaEnabled;

    StatusController(DataSource dataSource,
                     @Autowired(required=false) StringRedisTemplate redis,
                     @Autowired(required=false) ProducerFactory<String,String> kafkaProducerFactory,
                     @Value("${ims.redis.enabled:true}") boolean redisEnabled,
                     @Value("${ims.kafka.enabled:true}") boolean kafkaEnabled) {
        this.dataSource=dataSource; this.redis=redis; this.kafkaProducerFactory=kafkaProducerFactory;
        this.redisEnabled=redisEnabled; this.kafkaEnabled=kafkaEnabled;
    }

    @GetMapping("/status")
    StatusResponse status() {
        String database=pingDatabase();
        String redisStatus=checkRedis();
        String kafkaStatus=checkKafka();
        boolean degraded="DOWN".equals(database) || "DOWN".equals(redisStatus) || "DOWN".equals(kafkaStatus);
        return new StatusResponse(degraded ? "DEGRADED" : "UP","UP",database,redisStatus,kafkaStatus,Instant.now().toString());
    }

    private String pingDatabase() {
        try (Connection c=dataSource.getConnection()) { return c.isValid(2) ? "UP" : "DOWN"; }
        catch (Exception e) { return "DOWN"; }
    }

    private String checkRedis() {
        if (!redisEnabled) return "DISABLED";
        if (redis==null) return "DOWN";
        try { redis.hasKey("ims:status:ping"); return "UP"; }
        catch (Exception e) { return "DOWN"; }
    }

    private String checkKafka() {
        if (!kafkaEnabled) return "DISABLED";
        if (kafkaProducerFactory==null) return "DOWN";
        try (Producer<String,String> p=kafkaProducerFactory.createProducer()) { return "UP"; }
        catch (Exception e) { return "DOWN"; }
    }
}
