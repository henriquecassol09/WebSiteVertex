package com.empresa.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DosDefenseTest {

    private DosDefenseFilter dosDefenseFilter;

    @BeforeEach
    void setUp() {
        dosDefenseFilter = new DosDefenseFilter();
        // Parametros para teste rapido
        ReflectionTestUtils.setField(dosDefenseFilter, "authCapacity", 3);
        ReflectionTestUtils.setField(dosDefenseFilter, "authRefillMinutes", 1);
        ReflectionTestUtils.setField(dosDefenseFilter, "prospeccaoCapacity", 2);
        ReflectionTestUtils.setField(dosDefenseFilter, "prospeccaoRefillMinutes", 1);
        ReflectionTestUtils.setField(dosDefenseFilter, "apiCapacity", 5);
        ReflectionTestUtils.setField(dosDefenseFilter, "apiRefillMinutes", 1);
        ReflectionTestUtils.setField(dosDefenseFilter, "maxConcurrentRequests", 2);
        ReflectionTestUtils.setField(dosDefenseFilter, "jailMinutes", 3);
    }

    @Test
    @DisplayName("Deve permitir requisicoes normais dentro da cota e retornar headers de RateLimit")
    void devePermitirRequisicoesNormais() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/empresas");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        dosDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeader("X-RateLimit-Limit"));
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Deve bloquear Payload Bomb com HTTP 413 se Content-Length exceder 2MB")
    void deveBloquearPayloadBomb() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.setRemoteAddr("192.168.1.101");
        request.addHeader("Content-Length", String.valueOf(10 * 1024 * 1024L)); // 10MB
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        dosDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("limite maximo permitido de 2MB"));
    }

    @Test
    @DisplayName("Deve acionar 429 Too Many Requests quando estourar o limite de autenticacao")
    void deveAcionarRateLimitAuth() throws ServletException, IOException {
        String ip = "192.168.1.102";

        // Consome as 3 fichas do bucket de auth
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            dosDefenseFilter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }

        // 4a requisicao deve ser barrada com 429
        MockHttpServletRequest requestBloqueada = new MockHttpServletRequest("POST", "/api/auth/login");
        requestBloqueada.setRemoteAddr(ip);
        MockHttpServletResponse responseBloqueada = new MockHttpServletResponse();
        dosDefenseFilter.doFilterInternal(requestBloqueada, responseBloqueada, new MockFilterChain());

        assertEquals(429, responseBloqueada.getStatus());
        assertTrue(responseBloqueada.getContentAsString().contains("Muitas requisicoes"));
        assertNotNull(responseBloqueada.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Deve aplicar cota ultra-estrita para endpoint pesado de prospeccao")
    void deveAplicarCotaProspeccao() throws ServletException, IOException {
        String ip = "192.168.1.103";

        // Cota de prospeccao = 2
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pesquisas/prospectar-regiao");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            dosDefenseFilter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }

        // 3a requisicao de prospeccao no mesmo minuto deve ser barrada
        MockHttpServletRequest requestBloqueada = new MockHttpServletRequest("POST", "/api/pesquisas/prospectar-regiao");
        requestBloqueada.setRemoteAddr(ip);
        MockHttpServletResponse responseBloqueada = new MockHttpServletResponse();
        dosDefenseFilter.doFilterInternal(requestBloqueada, responseBloqueada, new MockFilterChain());

        assertEquals(429, responseBloqueada.getStatus());
    }

    @Test
    @DisplayName("Deve colocar IP em quarentena (Jail) apos 5 violacoes consecutivas")
    void deveColocarEmQuarentenaAposViolacoesConsecutivas() throws ServletException, IOException {
        String ip = "192.168.1.104";

        // Esgota o bucket (3 tokens)
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(ip);
            dosDefenseFilter.doFilterInternal(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // Gera 5 violacoes de 429 para ativar o Jail
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            dosDefenseFilter.doFilterInternal(req, res, new MockFilterChain());
            assertEquals(429, res.getStatus());
        }

        // Agora, mesmo uma requisicao para rota geral comum deve ser descartada imediatamente pelo Jail
        MockHttpServletRequest reqJail = new MockHttpServletRequest("GET", "/api/empresas");
        reqJail.setRemoteAddr(ip);
        MockHttpServletResponse resJail = new MockHttpServletResponse();
        dosDefenseFilter.doFilterInternal(reqJail, resJail, new MockFilterChain());

        assertEquals(429, resJail.getStatus());
        assertTrue(resJail.getContentAsString().contains("quarentena temporaria"));
    }

    @Test
    @DisplayName("Nao deve aplicar rate limit em rotas de health check e actuator")
    void naoDeveLimitarHealthEActuator() throws ServletException, IOException {
        MockHttpServletRequest reqHealth = new MockHttpServletRequest("GET", "/api/health");
        reqHealth.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resHealth = new MockHttpServletResponse();

        dosDefenseFilter.doFilterInternal(reqHealth, resHealth, new MockFilterChain());
        assertEquals(200, resHealth.getStatus());

        MockHttpServletRequest reqActuator = new MockHttpServletRequest("GET", "/actuator/health");
        reqActuator.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resActuator = new MockHttpServletResponse();

        dosDefenseFilter.doFilterInternal(reqActuator, resActuator, new MockFilterChain());
        assertEquals(200, resActuator.getStatus());
    }
}
