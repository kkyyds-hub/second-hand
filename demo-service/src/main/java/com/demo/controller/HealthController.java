package com.demo.controller;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** Minimal unauthenticated liveness endpoint for container orchestration. */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;

    public HealthController(JdbcTemplate jdbcTemplate,
                            StringRedisTemplate redisTemplate,
                            MongoTemplate mongoTemplate,
                            RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping(value = "/healthz", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> healthz() {
        return Map.of("status", "UP");
    }

    @GetMapping(value = "/readyz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> readyz() {
        String failedComponent = checkReadiness();
        if (failedComponent == null) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN", "component", failedComponent));
    }

    private String checkReadiness() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException ex) {
            return "mysql";
        }

        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            if (!"PONG".equalsIgnoreCase(connection.ping())) {
                return "redis";
            }
        } catch (RuntimeException ex) {
            return "redis";
        }

        try {
            MongoDatabase database = mongoTemplate.getDb();
            database.runCommand(new Document("ping", 1));
        } catch (RuntimeException ex) {
            return "mongodb";
        }

        try {
            rabbitTemplate.execute(channel -> {
                if (!channel.isOpen()) {
                    throw new IllegalStateException("RabbitMQ channel is closed");
                }
                return null;
            });
        } catch (RuntimeException ex) {
            return "rabbitmq";
        }
        return null;
    }
}
