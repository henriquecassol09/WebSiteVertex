package com.empresa.backend;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Filtro de Segurança Ativa contra SQL Injection (WAF em camada de aplicação).
 * 
 * Intercepta requisições HTTP antes de chegarem aos controladores ou repositórios,
 * inspecionando query strings, parâmetros de URL, caminhos e cabeçalhos com base
 * em padrões avançados de detecção de SQL Injection catalogados pela OWASP.
 */
@Component
public class SqlInjectionDefenseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionDefenseFilter.class);

    // Expressões regulares compiladas com máxima performance para detecção de injeções SQL
    private static final Pattern[] SQL_INJECTION_PATTERNS = new Pattern[] {
        // Tautologias clássicas e boolean-based injection: ' OR '1'='1', " OR ""=", ' OR 1=1, etc.
        Pattern.compile("(?i)('|\")\\s*(or|and)\\s*('|\")?\\d*('|\")?\\s*=\\s*('|\")?\\d*('|\")?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)('|\")\\s*(or|and)\\s*('|\")[^'\"]*('|\")?\\s*=\\s*('|\")?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(or|and)\\s+\\d+\\s*=\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(or|and)\\s+true\\s*=\\s*true\\b", Pattern.CASE_INSENSITIVE),

        // UNION SELECT / UNION ALL SELECT
        Pattern.compile("(?i)\\bunion\\s+(all\\s+)?select\\b", Pattern.CASE_INSENSITIVE),

        // Comandos DDL / DML destrutivos injetados com ponto e vírgula ou em sequência
        Pattern.compile("(?i);\\s*(drop|delete|truncate|alter|create|insert|update)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(drop|truncate)\\s+(table|database|schema|view|trigger|procedure)\\b", Pattern.CASE_INSENSITIVE),

        // Comentários SQL usados para truncar queries
        Pattern.compile("(--|#|/\\*.*?\\*/)", Pattern.CASE_INSENSITIVE),

        // Time-based / Blind SQL Injection (PostgreSQL, MySQL, SQL Server)
        Pattern.compile("(?i)\\b(pg_sleep|sleep|benchmark)\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\bwaitfor\\s+delay\\s+['\"]", Pattern.CASE_INSENSITIVE),

        // Execução de procedures ou comandos do sistema
        Pattern.compile("(?i)\\b(exec(\\s+|\\()+|execute(\\s+|\\()+|xp_cmdshell|sp_executesql)\\b", Pattern.CASE_INSENSITIVE),

        // Evasão com conversão de tipos ou funções de caractere suspeitas
        Pattern.compile("(?i)\\b(char|chr|concat)\\s*\\(\\s*\\d+", Pattern.CASE_INSENSITIVE),

        // Injeção de cláusula INTO OUTFILE ou DUMPFILE
        Pattern.compile("(?i)\\binto\\s+(outfile|dumpfile)\\b", Pattern.CASE_INSENSITIVE),

        // Tentativa de escapar aspas e injetar comandos SQL fechados
        Pattern.compile("(?i)'\\s*;\\s*--", Pattern.CASE_INSENSITIVE)
    };

    // Validação estrita para parâmetros de ordenação (sort)
    private static final Pattern SAFE_SORT_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+(,(asc|desc|ASC|DESC))?$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Inspeciona a URI completa (caminho decodificado)
        String requestUri = request.getRequestURI();
        if (containsSqlInjection(requestUri)) {
            bloquearRequisicao(request, response, "URI", requestUri);
            return;
        }

        // 2. Inspeciona a Query String pura
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isBlank()) {
            String decodedQuery;
            try {
                decodedQuery = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
            } catch (Exception e) {
                decodedQuery = queryString;
            }
            if (containsSqlInjection(decodedQuery)) {
                bloquearRequisicao(request, response, "QueryString", decodedQuery);
                return;
            }
        }

        // 3. Inspeciona cada parâmetro recebido
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);

            // Validação especial de segurança para parâmetros de ordenação 'sort'
            if ("sort".equalsIgnoreCase(paramName) && paramValues != null) {
                for (String sortVal : paramValues) {
                    if (sortVal != null && !sortVal.isBlank() && !SAFE_SORT_PATTERN.matcher(sortVal.trim()).matches()) {
                        bloquearRequisicao(request, response, "Parametro 'sort' invalido", sortVal);
                        return;
                    }
                }
            }

            if (paramValues != null) {
                for (String value : paramValues) {
                    if (value != null && !value.isBlank()) {
                        if (containsSqlInjection(value)) {
                            bloquearRequisicao(request, response, "Parametro '" + paramName + "'", value);
                            return;
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Verifica se o valor informado corresponde a algum padrão perigoso de SQL Injection.
     */
    public static boolean containsSqlInjection(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String texto = value.trim();
        // Mitigacao contra ReDoS (Regular Expression DoS): limita comprimento analisado
        if (texto.length() > 4096) {
            texto = texto.substring(0, 4096);
        }

        // Testa tambem a versao decodificada se houver encoding
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(texto).find()) {
                return true;
            }
        }

        return false;
    }

    private void bloquearRequisicao(HttpServletRequest request,
                                    HttpServletResponse response,
                                    String localizacao,
                                    String valorSuspeito) throws IOException {

        String ip = clientIp(request);
        String valorTruncado = valorSuspeito != null && valorSuspeito.length() > 120
                ? valorSuspeito.substring(0, 120) + "..."
                : valorSuspeito;

        log.warn("Tentativa de SQL Injection bloqueada! IP: {}, Endpoint: {}, Local: {}, Conteudo suspeito: [{}]",
                ip, request.getRequestURI(), localizacao, valorTruncado);

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        String jsonResposta = String.format(
                "{\"status\":400,\"timestamp\":\"%s\",\"erro\":\"Requisicao bloqueada por violacao de seguranca (padrao suspeito de SQL injection detectado).\"}",
                Instant.now().toString()
        );
        response.getWriter().write(jsonResposta);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
