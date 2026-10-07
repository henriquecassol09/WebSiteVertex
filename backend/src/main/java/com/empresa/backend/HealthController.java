package com.empresa.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de diagnóstico e integridade ("Health Check") funcional.
 * Testa a integridade da aplicação e a conectividade física com o PostgreSQL (Neon Serverless).
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", "UP");
        response.put("timestamp", Instant.now().toString());

        try {
            // Executa ping físico no banco de dados via query padrão leve
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (ping != null && ping == 1) {
                response.put("database", "CONNECTED");
                response.put("databaseType", "PostgreSQL (Neon Serverless)");
                return ResponseEntity.ok(response);
            } else {
                response.put("database", "UNEXPECTED_RESPONSE");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
        } catch (Exception ex) {
            response.put("database", "DOWN");
            response.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}
