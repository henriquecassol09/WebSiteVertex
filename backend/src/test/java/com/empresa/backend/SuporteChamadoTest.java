package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SuporteChamadoTest {

    @Test
    @DisplayName("Garante que o destinatário de suporte configurado é henriquecassol09@gmail.com")
    void deveConfirmarDestinatarioSuporteCorreto() {
        assertEquals("henriquecassol09@gmail.com", EmailService.DESTINATARIO_SUPORTE);
    }

    @Test
    @DisplayName("Deve processar chamado de suporte e encaminhar para EmailService")
    void deveProcessarChamadoSuporteComSucesso() {
        AtomicReference<String> capturadoEmail = new AtomicReference<>();
        AtomicReference<String> capturadoNome = new AtomicReference<>();
        AtomicReference<String> capturadoOrigem = new AtomicReference<>();
        AtomicReference<String> capturadoDescricao = new AtomicReference<>();
        AtomicReference<String> capturadoErroSistema = new AtomicReference<>();

        EmailService fakeEmailService = new EmailService() {
            @Override
            public void enviarChamadoSuporte(String emailUsuario, String nomeUsuario, String origem, String descricaoErro, String mensagemErroSistema, String ipOrigem, String userAgent) {
                capturadoEmail.set(emailUsuario);
                capturadoNome.set(nomeUsuario);
                capturadoOrigem.set(origem);
                capturadoDescricao.set(descricaoErro);
                capturadoErroSistema.set(mensagemErroSistema);
            }
        };

        SuporteService service = new SuporteService(fakeEmailService);

        ChamadoSuporteRequest req = new ChamadoSuporteRequest(
                "cliente@empresa.com",
                "Carlos Eduardo",
                "Esqueceu a Senha",
                "Não recebi o código de 6 dígitos no meu e-mail após 10 minutos.",
                "Tempo limite esgotado"
        );

        MensagemResponse response = service.enviarChamado(req, null);

        assertNotNull(response);
        assertNotNull(response.mensagem());
        assertEquals("cliente@empresa.com", capturadoEmail.get());
        assertEquals("Carlos Eduardo", capturadoNome.get());
        assertEquals("Esqueceu a Senha", capturadoOrigem.get());
        assertEquals("Não recebi o código de 6 dígitos no meu e-mail após 10 minutos.", capturadoDescricao.get());
        assertEquals("Tempo limite esgotado", capturadoErroSistema.get());
    }
}
