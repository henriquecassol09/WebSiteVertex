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

        System.out.println("=== RESULTADO DA CONSULTA EM TEMPO REAL VIA API (CNPJá + OpenCNPJ) ===");
        System.out.println("Razão Social: " + dados.get("razaoSocial"));
        System.out.println("Telefone: " + dados.get("telefone"));
        System.out.println("E-mail: " + dados.get("email"));
        System.out.println("Endereço: " + dados.get("endereco"));
        System.out.println("Cidade/UF: " + dados.get("cidade") + " - " + dados.get("estado"));

        assertNotNull(dados);
        assertTrue(dados.containsKey("razaoSocial"));
        assertTrue(dados.containsKey("telefone"));
    }

    @Test
    public void testDescobertaCnpjViaTavilyEEnriquecimentoComCnpja() {
        ProspeccaoService service = new ProspeccaoService(null, null, new ObjectMapper());

        // Teste de busca 100% dinâmica via Tavily API
        String nome = "Pizzaria Pertutti";
        String cidade = "Laranjeiras do Sul";
        String estado = "PR";

        System.out.println("=== TESTANDO BUSCA DINÂMICA VIA TAVILY SEARCH API ===");
        String cnpjDescoberto = service.buscarCnpjViaTavily(nome, cidade, estado);
        System.out.println("CNPJ Descoberto via Tavily: " + cnpjDescoberto);

        assertNotNull(cnpjDescoberto, "Tavily deve localizar o CNPJ nos domínios cadastrais abertos");
        assertTrue(ProspeccaoService.validarCnpj(cnpjDescoberto), "CNPJ deve ser matematicamente válido (Módulo 11)");

        // Pipeline completo: envia o CNPJ descoberto para o CNPJá / OpenCNPJ em tempo de execução
        Map<String, String> dadosCadastrais = service.consultarDadosCadastraisCompletos(cnpjDescoberto);
        assertNotNull(dadosCadastrais, "CNPJá/OpenCNPJ devem retornar dados cadastrais oficiais");

        System.out.println("=== DADOS RETORNADOS PELO CNPJá / OPENCNPJ PARA A EMPRESA DESCOBERTA ===");
        System.out.println("Razão Social Oficial: " + dadosCadastrais.get("razaoSocial"));
        System.out.println("Telefone Oficial: " + dadosCadastrais.get("telefone"));
        System.out.println("E-mail Oficial: " + dadosCadastrais.get("email"));
        System.out.println("Endereço: " + dadosCadastrais.get("endereco"));
    }
}
