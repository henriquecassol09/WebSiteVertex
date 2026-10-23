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

@Service
public class EmailValidatorService {

    private static final Logger log = LoggerFactory.getLogger(EmailValidatorService.class);

    private static final Pattern SINTAXE_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

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

        if (!SINTAXE_EMAIL.matcher(emailNormalizado).matches()) {
            return ResultadoValidacao.falha("Formato de e-mail inválido");
        }

        String[] partes = emailNormalizado.split("@");
        if (partes.length != 2) {
            return ResultadoValidacao.falha("Formato de e-mail inválido");
        }

        String dominio = partes[1].trim();

        if (DOMINIOS_DESCARTAVEIS.contains(dominio)) {
            return ResultadoValidacao.falha("E-mails descartáveis ou temporários não são permitidos");
        }

        boolean temMx = verificarRegistrosMx(dominio);
        if (!temMx) {
            log.warn("[EMAIL-VALIDATOR] Domínio '{}' não possui servidores MX válidos", dominio);
            return ResultadoValidacao.falha("O domínio @" + dominio + " não possui servidores de e-mail válidos para receber mensagens");
        }

        return ResultadoValidacao.sucesso();
    }

    // Validação de registros MX
    private boolean verificarRegistrosMx(String dominio) {
        if (dominio.equals("localhost") || dominio.endsWith(".local") || dominio.endsWith(".test")) {
            return true;
        }

        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns://8.8.8.8 dns://1.1.1.1");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(dominio, new String[]{"MX"});
            Attribute attr = attrs.get("MX");

            if (attr == null || attr.size() == 0) {
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
            return dominio.contains(".");
        }
    }
}
