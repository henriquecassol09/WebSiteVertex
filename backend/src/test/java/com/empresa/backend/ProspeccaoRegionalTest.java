package com.empresa.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Validação da Prospecção Regional de Todas as Cidades")
class ProspeccaoRegionalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deve sanitizar nomes preliminares de empresas para todas as 6 cidades da comarca")
    void deveSanitizarNomesParaTodasAsCidades() throws Exception {
        ProspeccaoService service = new ProspeccaoService(null, null, objectMapper);
        var method = ProspeccaoService.class.getDeclaredMethod("limparNomePreliminar", String.class, String.class);
        method.setAccessible(true);

        String raw1 = "Lojas Merlin em Laranjeiras do Sul, PR - 30.665.084/0002-24";
        assertEquals("Lojas Merlin", method.invoke(service, raw1, "30665084000224"));

        String raw2 = "Oficina Agricola do Miro em Rio Bonito do Iguaçu, PR - 37.717.890/0001-67";
        assertEquals("Oficina Agricola do Miro", method.invoke(service, raw2, "37717890000167"));

        String raw3 = "Auto Posto Virmond em Virmond, PR - 79.586.459/0001-79";
        assertEquals("Auto Posto Virmond", method.invoke(service, raw3, "79586459000179"));

        String raw4 = "Nossa Casa em Cantagalo, PR - 32.682.449/0001-73";
        assertEquals("Nossa Casa", method.invoke(service, raw4, "32682449000173"));

        String raw5 = "Supermercado Central em Nova Laranjeiras, PR - 12.345.678/0001-90";
        assertEquals("Supermercado Central", method.invoke(service, raw5, "12345678000190"));

        String raw6 = "Academia On Fit LTDA - 67524321000166 Porto Barreiro";
        assertEquals("Academia On Fit LTDA", method.invoke(service, raw6, "67524321000166"));
    }

    @Test
    @DisplayName("Deve extrair a cidade correta a partir do JSON de regiao para cada uma das 6 cidades")
    void deveExtrairCidadeCorretaParaCadaUmaDas6Cidades() {
        for (CidadeHelper.CidadeInfo info : CidadeHelper.CIDADES_CITADAS) {
            String json = String.format("""
                    {
                        "cidade": "%s",
                        "estado": "PR",
                        "endereco": "Rua Central, 100",
                        "categoria": "Comércio Local"
                    }
                    """, info.nome());

            Pesquisa p = Pesquisa.builder()
                    .idPesquisa(UUID.randomUUID().toString())
                    .idUsuario("usr-test")
                    .consulta("Empresa Teste " + info.nome())
                    .regiao(json)
                    .status("completed")
                    .build();

            PesquisaResponse resp = PesquisaResponse.from(p);
            assertEquals(info.nome(), resp.cidade());
            assertEquals("PR", resp.estado());
            assertEquals(info.nome() + " - PR", resp.regiao());
        }
    }
}

