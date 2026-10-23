package com.empresa.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AccessControlDefenseTest {

    private AccessControlDefense defense;

    @BeforeEach
    void setUp() {
        defense = new AccessControlDefense();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve permitir tentativas de login normais abaixo do limite")
    void devePermitirTentativasAbaixoDoLimite() {
        assertDoesNotThrow(() -> defense.verificarBloqueioLogin("usuario@teste.com", "192.168.1.1"));
        defense.registrarFalhaLogin("usuario@teste.com", "192.168.1.1");
        assertDoesNotThrow(() -> defense.verificarBloqueioLogin("usuario@teste.com", "192.168.1.1"));
    }

    @Test
    @DisplayName("Deve bloquear conta por 15 minutos apos 5 falhas consecutivas de login")
    void deveBloquearContaAposCincoFalhas() {
        String email = "vitima@empresa.com";
        String ip = "10.0.0.50";

        for (int i = 0; i < 5; i++) {
            defense.registrarFalhaLogin(email, ip);
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> defense.verificarBloqueioLogin(email, ip));

        assertTrue(ex.getMessage().contains("Conta temporariamente bloqueada"));
    }

    @Test
    @DisplayName("Login com sucesso deve resetar o historico de falhas da conta")
    void loginComSucessoDeveResetarFalhas() {
        String email = "usuario@empresa.com";
        String ip = "10.0.0.51";

        for (int i = 0; i < 4; i++) {
            defense.registrarFalhaLogin(email, ip);
        }

        defense.registrarSucessoLogin(email, ip);

        assertDoesNotThrow(() -> defense.verificarBloqueioLogin(email, ip));
    }

    @Test
    @DisplayName("Deve invalidar e destruir codigo OTP apos 5 tentativas incorretas")
    void deveDestruirCodigoOtpAposCincoTentativasIncorretas() {
        String identificador = "CADASTRO:novo@empresa.com";
        AtomicBoolean codigoDestruido = new AtomicBoolean(false);

        for (int i = 0; i < 4; i++) {
            defense.registrarFalhaOtp(identificador, () -> codigoDestruido.set(true));
            assertFalse(codigoDestruido.get());
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> defense.registrarFalhaOtp(identificador, () -> codigoDestruido.set(true)));

        assertTrue(codigoDestruido.get());
        assertTrue(ex.getMessage().contains("Limite de tentativas incorretas excedido"));
    }

    @Test
    @DisplayName("Deve revogar token JWT imediatamente e identifica-lo na blacklist ativa")
    void deveRevogarTokenJwt() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tokenDeTeste";
        Instant expiraEm = Instant.now().plusSeconds(900);

        assertFalse(defense.isTokenRevogado(token));

        defense.revogarTokenJwt(token, expiraEm);

        assertTrue(defense.isTokenRevogado(token));
    }

    @Test
    @DisplayName("AutenticacaoUtil deve lancar AutenticacaoException se o contexto de seguranca for nulo ou anonimo")
    void autenticacaoUtilDeveRejeitarSessaoInvalida() {
        SecurityContextHolder.clearContext();

        assertThrows(AutenticacaoException.class, AutenticacaoUtil::idUsuarioAtual);

        var authAnonimo = new UsernamePasswordAuthenticationToken("anonymousUser", null);
        SecurityContextHolder.getContext().setAuthentication(authAnonimo);

        assertThrows(AutenticacaoException.class, AutenticacaoUtil::idUsuarioAtual);
    }
}
