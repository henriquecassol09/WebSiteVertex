package com.empresa.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AccessControlDefense {

    // Bloqueio de login por tentativas
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AtomicInteger> tentativasLoginPorChave = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> contasBloqueadas = new ConcurrentHashMap<>();

    public void verificarBloqueioLogin(String email, String ip) {
        String chaveEmail = "email:" + email.toLowerCase().trim();
        Instant expiraEmail = contasBloqueadas.get(chaveEmail);
        if (expiraEmail != null) {
            if (Instant.now().isBefore(expiraEmail)) {
                long minutosRestantes = Math.max(1, Duration.between(Instant.now(), expiraEmail).toMinutes());
                log.warn("Tentativa de login rejeitada para conta bloqueada: {} (IP: {})", email, ip);
                throw new BusinessException(String.format(
                        "Conta temporariamente bloqueada por excesso de tentativas incorretas. Tente novamente em %d minutos ou utilize 'Esqueci minha senha'.",
                        minutosRestantes));
            } else {
                contasBloqueadas.remove(chaveEmail);
                tentativasLoginPorChave.remove(chaveEmail);
            }
        }

        String chaveIp = "ip:" + ip;
        Instant expiraIp = contasBloqueadas.get(chaveIp);
        if (expiraIp != null) {
            if (Instant.now().isBefore(expiraIp)) {
                long minutosRestantes = Math.max(1, Duration.between(Instant.now(), expiraIp).toMinutes());
                log.warn("Tentativa de login rejeitada para IP bloqueado: {}", ip);
                throw new BusinessException(String.format(
                        "Muitas tentativas de login falhas a partir deste IP. Bloqueio temporário ativo por mais %d minutos.",
                        minutosRestantes));
            } else {
                contasBloqueadas.remove(chaveIp);
                tentativasLoginPorChave.remove(chaveIp);
            }
        }
    }

    public void registrarFalhaLogin(String email, String ip) {
        String chaveEmail = "email:" + email.toLowerCase().trim();
        int falhasEmail = tentativasLoginPorChave.computeIfAbsent(chaveEmail, k -> new AtomicInteger(0)).incrementAndGet();

        String chaveIp = "ip:" + ip;
        int falhasIp = tentativasLoginPorChave.computeIfAbsent(chaveIp, k -> new AtomicInteger(0)).incrementAndGet();

        log.warn("Falha de autenticacao registrada para email '{}' (tentativa {}) e IP '{}' (tentativa {})",
                email, falhasEmail, ip, falhasIp);

        if (falhasEmail >= MAX_LOGIN_ATTEMPTS) {
            log.error("Conta '{}' bloqueada por {} minutos devido a {} falhas consecutivas.",
                    email, LOCKOUT_DURATION.toMinutes(), falhasEmail);
            contasBloqueadas.put(chaveEmail, Instant.now().plus(LOCKOUT_DURATION));
        }

        if (falhasIp >= (MAX_LOGIN_ATTEMPTS * 2)) {
            log.error("IP '{}' bloqueado por {} minutos devido a excesso de falhas de autenticacao.",
                    ip, LOCKOUT_DURATION.toMinutes());
            contasBloqueadas.put(chaveIp, Instant.now().plus(LOCKOUT_DURATION));
        }
    }

    public void registrarSucessoLogin(String email, String ip) {
        tentativasLoginPorChave.remove("email:" + email.toLowerCase().trim());
        contasBloqueadas.remove("email:" + email.toLowerCase().trim());
    }

    // Proteção OTP
    private static final int MAX_OTP_ATTEMPTS = 5;
    private final ConcurrentHashMap<String, AtomicInteger> tentativasOtpPorIdentificador = new ConcurrentHashMap<>();

    public void registrarFalhaOtp(String identificador, Runnable acaoDestruirCodigo) {
        int falhas = tentativasOtpPorIdentificador.computeIfAbsent(identificador, k -> new AtomicInteger(0)).incrementAndGet();
        log.warn("Tentativa incorreta de codigo OTP para '{}' (tentativa {}/{})", identificador, falhas, MAX_OTP_ATTEMPTS);

        if (falhas >= MAX_OTP_ATTEMPTS) {
            tentativasOtpPorIdentificador.remove(identificador);
            acaoDestruirCodigo.run();
            log.error("Limite de tentativas de codigo excedido para '{}'. Codigo invalidado por seguranca.", identificador);
            throw new BusinessException("Limite de tentativas incorretas excedido. O codigo foi invalidado por seguranca. Solicite um novo envio.");
        }
    }

    public void registrarSucessoOtp(String identificador) {
        tentativasOtpPorIdentificador.remove(identificador);
    }

    // Blacklist JWT
    private final ConcurrentHashMap<String, Instant> tokenBlacklist = new ConcurrentHashMap<>();

    public void revogarTokenJwt(String token, Instant expiraEm) {
        if (token != null && !token.isBlank() && expiraEm != null && expiraEm.isAfter(Instant.now())) {
            tokenBlacklist.put(token, expiraEm);
            log.info("Token JWT revogado e inserido na blacklist ativa.");
        }
    }

    public boolean isTokenRevogado(String token) {
        if (token == null || token.isBlank()) return false;
        Instant expiraEm = tokenBlacklist.get(token);
        if (expiraEm == null) return false;
        if (Instant.now().isAfter(expiraEm)) {
            tokenBlacklist.remove(token);
            return false;
        }
        return true;
    }

    // Rate limiting para atualização de foto de perfil (Defesa anti-DoS e anti-spam)
    private final ConcurrentHashMap<String, AtomicInteger> updatesFotoPorUsuario = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> janelasFotoPorUsuario = new ConcurrentHashMap<>();

    public void verificarLimiteAtualizacaoFoto(String idUsuario) {
        if (idUsuario == null) return;
        Instant agora = Instant.now();
        Instant janela = janelasFotoPorUsuario.compute(idUsuario, (k, v) -> {
            if (v == null || agora.isAfter(v)) {
                updatesFotoPorUsuario.put(idUsuario, new AtomicInteger(0));
                return agora.plus(Duration.ofMinutes(5));
            }
            return v;
        });

        int tentativas = updatesFotoPorUsuario.computeIfAbsent(idUsuario, k -> new AtomicInteger(0)).incrementAndGet();
        if (tentativas > 10) {
            log.warn("Excesso de atualizacoes de foto para usuario {}. Bloqueio temporario.", idUsuario);
            throw new BusinessException("Muitas atualizações de foto recentes. Por favor, aguarde alguns minutos.");
        }
    }

    @Scheduled(fixedRate = 600000)
    public void limparCadastrosExpirados() {
        Instant agora = Instant.now();
        contasBloqueadas.entrySet().removeIf(e -> agora.isAfter(e.getValue()));
        tokenBlacklist.entrySet().removeIf(e -> agora.isAfter(e.getValue()));
        janelasFotoPorUsuario.entrySet().removeIf(e -> agora.isAfter(e.getValue()));
    }
}
