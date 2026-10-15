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

class CsrfDefenseTest {

    private CsrfDefenseFilter csrfDefenseFilter;

    @BeforeEach
    void setUp() {
        csrfDefenseFilter = new CsrfDefenseFilter();
        ReflectionTestUtils.setField(csrfDefenseFilter, "allowedOrigins",
                "http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173");
    }

    @Test
    @DisplayName("Metodos seguros (GET, HEAD, OPTIONS) nao devem ser bloqueados por CSRF")
    void metodosSegurosNaoDevemSerBloqueados() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/empresas");
        request.addHeader("Origin", "http://site-estranho.com"); // mesmo com origin externa, GET e seguro
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("POST com Origin autorizada deve ser permitido")
    void postComOriginAutorizadaDeveSerPermitido() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("POST com Origin nao autorizada (cross-site attack) deve ser barrado com 403 Forbidden")
    void postComOriginNaoAutorizadaDeveSerBarrado() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.addHeader("Origin", "http://evil-attacker.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CSRF Protection"));
    }

    @Test
    @DisplayName("PUT/DELETE com Referer externo nao autorizado deve ser barrado com 403 Forbidden")
    void mutacaoComRefererNaoAutorizadoDeveSerBarrada() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/empresas/123");
        request.addHeader("Referer", "http://malicious-website.org/phishing-page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CSRF Protection"));
    }

    @Test
    @DisplayName("POST com Referer da aplicacao autorizada deve ser permitido")
    void postComRefererAutorizadoDeveSerPermitido() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.addHeader("Referer", "http://localhost:5173/painel");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Submissao de formulario simples sem Origin e sem headers anti-CSRF deve ser barrada com 403")
    void formSimplesSemCabecalhosAntiCsrfDeveSerBarrado() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("anti-CSRF"));
    }

    @Test
    @DisplayName("Requisicao com cabecalho seguro (X-Requested-With ou Authorization) deve ser permitida")
    void requisicaoComCabecalhoCustomizadoDeveSerPermitida() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/empresas");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        csrfDefenseFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }
}
