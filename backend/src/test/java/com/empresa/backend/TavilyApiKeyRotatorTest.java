package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TavilyApiKeyRotatorTest {

    @Test
    @DisplayName("Deve inicializar com as chaves fornecidas na lista de mock")
    public void testInicializacaoComChaves() {
        TavilyApiKeyRotator rotator = new TavilyApiKeyRotator(List.of("tvly-mock-1", "tvly-mock-2", "tvly-mock-3"));

        assertEquals(3, rotator.getTotalChaves());
        assertEquals("tvly-mock-1", rotator.obterChaveAtual());
        assertTrue(rotator.getChaves().contains("tvly-mock-1"));
        assertTrue(rotator.getChaves().contains("tvly-mock-2"));
        assertTrue(rotator.getChaves().contains("tvly-mock-3"));
    }

    @Test
    @DisplayName("Deve rotacionar para a próxima chave quando a atual atingir o limite")
    public void testRotacaoQuandoEstouraLimite() {
        TavilyApiKeyRotator rotator = new TavilyApiKeyRotator(List.of("chave-1", "chave-2", "chave-3"));

        assertEquals("chave-1", rotator.obterChaveAtual());

        // Simula limite atingido na chave-1
        String proxima = rotator.rotacionarParaProxima("chave-1", "HTTP 429 - Too Many Requests");
        assertEquals("chave-2", proxima);
        assertEquals("chave-2", rotator.obterChaveAtual());

        // Simula limite atingido na chave-2
        proxima = rotator.rotacionarParaProxima("chave-2", "HTTP 432 - Quota Exceeded");
        assertEquals("chave-3", proxima);
        assertEquals("chave-3", rotator.obterChaveAtual());

        // Quando chega ao fim da lista, volta ciclicamente para o início
        proxima = rotator.rotacionarParaProxima("chave-3", "Usage limit exceeded");
        assertEquals("chave-1", proxima);
    }

    @Test
    @DisplayName("Deve identificar corretamente status codes e mensagens de limite/cota do Tavily")
    public void testIdentificacaoLimiteExcedido() {
        TavilyApiKeyRotator rotator = new TavilyApiKeyRotator();

        // Status codes
        assertTrue(rotator.isLimiteOuChaveInvalida(429, "Too Many Requests"));
        assertTrue(rotator.isLimiteOuChaveInvalida(432, "Quota Exceeded"));
        assertTrue(rotator.isLimiteOuChaveInvalida(401, "Unauthorized"));
        assertTrue(rotator.isLimiteOuChaveInvalida(403, "Forbidden"));

        // Mensagens no corpo
        assertTrue(rotator.isLimiteOuChaveInvalida(400, "{\"detail\":{\"error\":\"Usage limit exceeded.\"}}"));
        assertTrue(rotator.isLimiteOuChaveInvalida(400, "{\"detail\":{\"error\":\"Rate limit reached\"}}"));
        assertTrue(rotator.isLimiteOuChaveInvalida(400, "{\"detail\":{\"error\":\"Monthly quota exceeded\"}}"));
        assertTrue(rotator.isLimiteOuChaveInvalida(400, "{\"detail\":{\"error\":\"Insufficient credits\"}}"));

        // Requisição normal 200 OK ou erro não relacionado
        assertFalse(rotator.isLimiteOuChaveInvalida(200, "{\"results\":[]}"));
        assertFalse(rotator.isLimiteOuChaveInvalida(404, "Not Found"));
    }

    @Test
    @DisplayName("Deve mascarar a chave de forma segura para os logs")
    public void testMascaramentoChave() {
        String chave = "tvly-teste1234567890abcdef";
        String mascarada = TavilyApiKeyRotator.mascararChave(chave);

        assertTrue(mascarada.startsWith("tvly-tes"));
        assertTrue(mascarada.endsWith("cdef"));
        assertTrue(mascarada.contains("..."));
        assertFalse(mascarada.contains("1234567890"));
    }

    @Test
    @DisplayName("Deve permitir adicionar chaves dinamicamente e ignorar duplicatas")
    public void testAdicaoDinamica() {
        TavilyApiKeyRotator rotator = new TavilyApiKeyRotator(List.of("chave-a"));
        assertEquals(1, rotator.getTotalChaves());

        rotator.adicionarChave("chave-b");
        assertEquals(2, rotator.getTotalChaves());

        // Adicionar duplicata não deve alterar o tamanho
        rotator.adicionarChave("chave-b");
        assertEquals(2, rotator.getTotalChaves());
    }
}

