package com.empresa.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador de E-mails 100% Java e Open Source.
 * Valida formato RFC 5322, consulta servidores MX (DNS) reais na internet
 * e bloqueia domínios de e-mails descartáveis/temporários.
 */
@Service
public class EmailValidatorService {

    private static final Logger log = LoggerFactory.getLogger(EmailValidatorService.class);

    private static final Pattern SINTAXE_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // Domínios conhecidos de e-mails descartáveis/temporários
    private static final Set<String> DOMINIOS_DESCARTAVEIS = Set.of(
            "tempmail.com", "10minutemail.com", "guerrillamail.com", "mailinator.com",
            "yopmail.com", "trashmail.com", "dispostable.com", "sharklasers.com",
            "temp-mail.org", "fakeinbox.com", "throwawaymail.com", "getairmail.com"
    );

    public record ResultadoValidacao(boolean valido, String mensagem) {
        public static ResultadoValidacao sucesso() {
            return new ResultadoValidacao(true, null);
        }

        public static ResultadoValidacao falha(String mensagem) {
            return new ResultadoValidacao(false, mensagem);
        }
    }

    public ResultadoValidacao validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return ResultadoValidacao.falha("E-mail não informado");
        }

        String emailNormalizado = email.trim().toLowerCase();

        // 1. Checagem de Sintaxe
        if (!SINTAXE_EMAIL.matcher(emailNormalizado).matches()) {
            return ResultadoValidacao.falha("Formato de e-mail inválido");
        }

        String[] partes = emailNormalizado.split("@");
        if (partes.length != 2) {
            return ResultadoValidacao.falha("Formato de e-mail inválido");
        }

        String dominio = partes[1].trim();

        // 2. Bloqueio de E-mails Descartáveis
        if (DOMINIOS_DESCARTAVEIS.contains(dominio)) {
            return ResultadoValidacao.falha("E-mails descartáveis ou temporários não são permitidos");
        }

        // 3. Checagem de DNS / Registros MX (Mail Exchange)
        boolean temMx = verificarRegistrosMx(dominio);
        if (!temMx) {
            log.warn("[EMAIL-VALIDATOR] Domínio '{}' não possui servidores MX válidos", dominio);
            return ResultadoValidacao.falha("O domínio @" + dominio + " não possui servidores de e-mail válidos para receber mensagens");
        }

        return ResultadoValidacao.sucesso();
    }

    /**
     * Consulta os registros DNS MX para verificar se o domínio aceita e-mails.
     */
    private boolean verificarRegistrosMx(String dominio) {
        // Domínios locais/testes
        if (dominio.equals("localhost") || dominio.endsWith(".local") || dominio.endsWith(".test")) {
            return true;
        }

        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns://8.8.8.8 dns://1.1.1.1");
        env.put("com.sun.jndi.dns.timeout.initial", "2000"); // 2 segundos
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(dominio, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            if (attr == null || attr.size() == 0) {
                // Tenta fallback com registro 'A' (alguns domínios aceitam entrega direta)
                attrs = ctx.getAttributes(dominio, new String[]{"A"});
                attr = attrs.get("A");
                if (attr == null || attr.size() == 0) {
                    return false;
                }
            }

            NamingEnumeration<?> en = attr.getAll();
            return en != null && en.hasMore();
        } catch (NamingException e) {
            log.debug("[EMAIL-VALIDATOR] Falha ao consultar DNS para {}: {}", dominio, e.getMessage());
            // Em caso de erro de rede ou DNS timeout, não bloqueia o usuário se o domínio tiver formato plausível
            return dominio.contains(".");
        }
    }
}
