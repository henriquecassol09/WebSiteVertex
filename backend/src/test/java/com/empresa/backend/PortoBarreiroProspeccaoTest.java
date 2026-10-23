package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Validação da Prospecção da Cidade de Porto Barreiro")
class PortoBarreiroProspeccaoTest {

    @Test
    @DisplayName("Deve extrair a cidade Porto Barreiro quando o JSON de regiao contiver a cidade")
    void deveExtrairCidadePortoBarreiroDeJson() {
        String regiaoJson = """
                {
                    "cidade": "Porto Barreiro",
                    "estado": "PR",
                    "endereco": "Rua das Flores, 335, Centro",
                    "categoria": "Materiais de Construção",
                    "horario": "Seg a Sex 08:00 - 18:00",
                    "telefone": "(42) 3653-1187",
                    "email": "pbmateriais@hotmail.com",
                    "cnpj": "05.059.448/0001-02",
                    "razaoSocial": "PORTO BARREIRO MATERIAIS DE CONSTRUCAO LTDA"
                }
                """;

        Pesquisa p = Pesquisa.builder()
                .idPesquisa(UUID.randomUUID().toString())
                .idUsuario("usr-test-1")
                .consulta("Porto Barreiro Materiais de Construcao LTDA")
                .regiao(regiaoJson)
                .status("completed")
                .build();

        PesquisaResponse response = PesquisaResponse.from(p);

        assertEquals("Porto Barreiro", response.cidade());
        assertEquals("PR", response.estado());
        assertEquals("Porto Barreiro - PR", response.regiao());
        assertEquals("Rua das Flores, 335, Centro", response.endereco());
        assertEquals("(42) 3653-1187", response.telefone());
        assertEquals("pbmateriais@hotmail.com", response.email());
        assertEquals("05.059.448/0001-02", response.cnpj());
        assertEquals("Materiais de Construção", response.categoria());
    }

    @Test
    @DisplayName("Deve identificar Porto Barreiro quando o texto bruto contiver o nome da cidade")
    void deveIdentificarPortoBarreiroDeTextoBruto() {
        Pesquisa p1 = Pesquisa.builder()
                .idPesquisa(UUID.randomUUID().toString())
                .idUsuario("usr-test-1")
                .consulta("Academia On Fit")
                .regiao("Avenida das Flores, Centro (Porto Barreiro - PR)")
                .status("completed")
                .build();

        PesquisaResponse res1 = PesquisaResponse.from(p1);
        assertEquals("Porto Barreiro", res1.cidade());
        assertEquals("Porto Barreiro - PR", res1.regiao());

        Pesquisa p2 = Pesquisa.builder()
                .idPesquisa(UUID.randomUUID().toString())
                .idUsuario("usr-test-1")
                .consulta("Victor Mazon")
                .regiao("Rua das Tipoanas, 981 - Porto Barreiro")
                .status("completed")
                .build();

        PesquisaResponse res2 = PesquisaResponse.from(p2);
        assertEquals("Porto Barreiro", res2.cidade());
        assertEquals("Porto Barreiro - PR", res2.regiao());
    }

    @Test
    @DisplayName("Deve sanitizar nomes preliminares de empresas de Porto Barreiro oriundos dos portais cadastrais")
    void deveSanitizarNomesPreliminaresDePortoBarreiro() {
        ProspeccaoService service = new ProspeccaoService(null, null, new com.fasterxml.jackson.databind.ObjectMapper());

        String raw1 = "51.274.888 Victor Mazon - 51274888000191 Porto Barreiro";
        String limpo1 = invocarLimpeza(service, raw1, "51274888000191");
        assertEquals("Victor Mazon", limpo1);

        String raw2 = "68.860.607 Alexandre Kuster - Porto Barreiro";
        String limpo2 = invocarLimpeza(service, raw2, "68860607000185");
        assertEquals("Alexandre Kuster", limpo2);

        String raw3 = "Academia On Fit LTDA - 67524321000166 Porto Barreiro";
        String limpo3 = invocarLimpeza(service, raw3, "67524321000166");
        assertEquals("Academia On Fit LTDA", limpo3);

        String raw4 = "Tico Materiais de Construcao 07828736000282 Porto Barreiro";
        String limpo4 = invocarLimpeza(service, raw4, "07828736000282");
        assertEquals("Tico Materiais de Construcao", limpo4);
    }

    private String invocarLimpeza(ProspeccaoService service, String raw, String cnpj) {
        try {
            var method = ProspeccaoService.class.getDeclaredMethod("limparNomePreliminar", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(service, raw, cnpj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
