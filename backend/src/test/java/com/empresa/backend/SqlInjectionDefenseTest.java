package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlInjectionDefenseTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "' OR '1'='1",
        "' OR 1=1 --",
        "\" OR \"\"=\"",
        "' OR 'a'='a'",
        "admin'--",
        "1; DROP TABLE usuarios; --",
        "1; DELETE FROM empresas",
        "1; TRUNCATE TABLE sessoes",
        "' UNION SELECT null, null, null --",
        "' UNION ALL SELECT id_usuario, senha FROM conta --",
        "'; EXEC xp_cmdshell('dir'); --",
        "1 AND pg_sleep(5)",
        "1 WAITFOR DELAY '0:0:5'",
        "1; ALTER TABLE usuario ADD COLUMN admin boolean",
        "' OR true=true --"
    })
    @DisplayName("Deve detectar e bloquear vetores conhecidos de SQL Injection")
    void deveDetectarSqlInjection(String payloadMalicioso) {
        assertTrue(SqlInjectionDefenseFilter.containsSqlInjection(payloadMalicioso),
                "Deveria ter identificado SQL Injection no payload: " + payloadMalicioso);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "João da Silva",
        "Empresa de Tecnologia & Soluções LTDA",
        "Av. das Palmeiras, 123",
        "Laranjeiras do Sul - PR",
        "contato@empresa.com.br",
        "Proposta Comercial - Desenvolvimento Web",
        "12.345.678/0001-90",
        "42999887766",
        "categoria=Comércio Local",
        "page=0&size=20"
    })
    @DisplayName("Deve permitir valores legítimos de uso normal sem falsos positivos")
    void devePermitirValoresLegitimos(String valorLegitimo) {
        assertFalse(SqlInjectionDefenseFilter.containsSqlInjection(valorLegitimo),
                "Não deveria ter bloqueado valor legítimo: " + valorLegitimo);
    }
}
