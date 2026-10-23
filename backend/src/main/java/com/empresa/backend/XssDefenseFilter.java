package com.empresa.backend;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.regex.Pattern;

@Component
public class XssDefenseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XssDefenseFilter.class);

    // Padrões de detecção de XSS
    private static final Pattern[] XSS_PATTERNS = new Pattern[] {
        Pattern.compile("(?i)<\\s*script[\\s\\S]*?>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)javascript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)vbscript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)data\\s*:\\s*text\\/(html|javascript)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\bon(load|error|click|dblclick|mouseover|mouseout|mouseenter|mouseleave|mousedown|mouseup|keydown|keypress|keyup|focus|blur|change|submit|reset|select|input|invalid|pointerdown|pointerup|animationstart|animationend|toggle|wheel)\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)<\\s*(iframe|object|embed|applet|meta|link|base|form)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)<\\s*(svg|img|body|input|button|details|audio|video|source|marquee|textarea|select)\\b[^>]*?\\bon\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(document\\.cookie|document\\.location|window\\.location)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(alert|prompt|confirm|eval|setTimeout|setInterval|Function)\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)expression\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)behavior\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\bsrcdoc\\s*=", Pattern.CASE_INSENSITIVE)
    };

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        if (containsXss(requestUri)) {
            bloquearRequisicao(request, response, "URI", requestUri);
            return;
        }

        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isBlank()) {
            String decodedQuery = normalizarEntrada(queryString);
            if (containsXss(decodedQuery)) {
                bloquearRequisicao(request, response, "QueryString", decodedQuery);
                return;
            }
        }

        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            if (containsXss(paramName)) {
                bloquearRequisicao(request, response, "ParameterName:" + paramName, paramName);
                return;
            }
            String[] paramValues = request.getParameterValues(paramName);
            if (paramValues != null) {
                for (String paramValue : paramValues) {
                    if (containsXss(paramValue)) {
                        bloquearRequisicao(request, response, "ParameterValue:" + paramName, paramValue);
                        return;
                    }
                }
            }
        }

        String contentType = request.getContentType();
        String method = request.getMethod();
        boolean hasBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);

        if (hasBody && contentType != null && contentType.toLowerCase().contains("application/json")) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            String bodyContent = wrappedRequest.getBodyAsString();

            if (containsXss(bodyContent)) {
                bloquearRequisicao(request, response, "RequestBody", bodyContent);
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public static boolean containsXss(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String valorInspecao = value.length() > 8192 ? value.substring(0, 8192) : value;
        String normalizado = normalizarEntrada(valorInspecao);

        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(normalizado).find()) {
                return true;
            }
        }

        return false;
    }

    private static String normalizarEntrada(String input) {
        if (input == null) return "";
        String texto = input;

        for (int i = 0; i < 2; i++) {
            try {
                String decodificado = URLDecoder.decode(texto, StandardCharsets.UTF_8);
                if (decodificado.equals(texto)) break;
                texto = decodificado;
            } catch (Exception ignored) {
                break;
            }
        }

        texto = texto.replace("\\u003c", "<").replace("\\u003C", "<")
                     .replace("\\u003e", ">").replace("\\u003E", ">");

        texto = texto.replace("&lt;", "<").replace("&LT;", "<")
                     .replace("&gt;", ">").replace("&GT;", ">")
                     .replace("&#60;", "<").replace("&#62;", ">")
                     .replace("&#x3c;", "<").replace("&#x3C;", "<")
                     .replace("&#x3e;", ">").replace("&#x3E;", ">");

        return texto;
    }

    private void bloquearRequisicao(HttpServletRequest request,
                                    HttpServletResponse response,
                                    String localizacao,
                                    String valorSuspeito) throws IOException {

        String ip = clientIp(request);
        String valorTruncado = valorSuspeito != null && valorSuspeito.length() > 120
                ? valorSuspeito.substring(0, 120) + "..."
                : valorSuspeito;

        log.warn("Tentativa de Cross-Site Scripting (XSS) bloqueada! IP: {}, Endpoint: {}, Local: {}, Conteudo suspeito: [{}]",
                ip, request.getRequestURI(), localizacao, valorTruncado);

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        String jsonResposta = String.format(
                "{\"status\":400,\"timestamp\":\"%s\",\"erro\":\"Requisicao bloqueada por violacao de seguranca (padrao suspeito de Cross-Site Scripting - XSS detectado).\"}",
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

    // Cache do corpo da requisição para leitura múltipla
    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        public String getBodyAsString() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {}

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
