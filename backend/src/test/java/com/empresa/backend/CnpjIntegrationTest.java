package com.empresa.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CnpjIntegrationTest {

    @Test
    public void testIntegracaoCnpjaEOpenCnpj() {
        ProspeccaoService service = new ProspeccaoService(null, null, new ObjectMapper());

        String cnpj = "60701190000104";
        Map<String, String> dados = service.consultarDadosCadastraisCompletos(cnpj);

        assertNotNull(dados);
        assertTrue(dados.containsKey("razaoSocial"));
        assertTrue(dados.containsKey("telefone"));
    }

    @Test
    public void testDescobertaCnpjViaTavilyEEnriquecimentoComCnpja() {
        ProspeccaoService service = new ProspeccaoService(null, null, new ObjectMapper());

        String nome = "Pizzaria Pertutti";
        String cidade = "Laranjeiras do Sul";
        String estado = "PR";

        String cnpjDescoberto = service.buscarCnpjViaTavily(nome, cidade, estado);

        assertNotNull(cnpjDescoberto, "Tavily deve localizar o CNPJ nos domínios cadastrais abertos");
        assertTrue(ProspeccaoService.validarCnpj(cnpjDescoberto), "CNPJ deve ser matematicamente válido (Módulo 11)");

        Map<String, String> dadosCadastrais = service.consultarDadosCadastraisCompletos(cnpjDescoberto);
        assertNotNull(dadosCadastrais, "CNPJá/OpenCNPJ devem retornar dados cadastrais oficiais");
    }
}
