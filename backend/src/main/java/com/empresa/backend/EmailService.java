package com.empresa.backend;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String remetente;

    @Value("${spring.mail.host:}")
    private String host;

    public void enviarCodigoVerificacao(String destinatario, String nome, String codigo) {
        String assunto = "VERTEX - Código de verificação de e-mail";
        String html = construirHtmlEmail(
                nome,
                "Confirme seu endereço de e-mail",
                "Recebemos seu cadastro na plataforma VERTEX. Para validar seu e-mail e ativar seu acesso, utilize o código de segurança abaixo:",
                codigo,
                "Este código expira em 15 minutos."
        );

        enviarEmail(destinatario, assunto, html, codigo, "ATIVAÇÃO DE CONTA");
    }

    public void enviarCodigoRecuperacaoSenha(String destinatario, String nome, String codigo) {
        String assunto = "VERTEX - Código para redefinição de senha";
        String html = construirHtmlEmail(
                nome,
                "Redefinição de Senha de Acesso",
                "Recebemos uma solicitação para redefinir a senha da sua conta no VERTEX. Utilize o código de verificação abaixo:",
                codigo,
                "Este código expira em 15 minutos. Se não foi você, ignore este e-mail."
        );

        enviarEmail(destinatario, assunto, html, codigo, "RECUPERAÇÃO DE SENHA");
    }

    public void enviarChamadoSuporte(String emailUsuario, String nomeUsuario, String origem, String descricaoErro, String mensagemErroSistema, String ipOrigem, String userAgent) {
        String assunto = "VERTEX - Chamado de Suporte [" + (origem != null ? origem : "Geral") + "] de " + emailUsuario;
        String html = construirHtmlSuporte(emailUsuario, nomeUsuario, origem, descricaoErro, mensagemErroSistema, ipOrigem, userAgent);

        log.info("================================================================================");
        log.info("[SPRING-MAIL] >>> NOVO CHAMADO DE SUPORTE TÉCNICO <<<");
        log.info("[SPRING-MAIL] Destinatário do Suporte: {}", DESTINATARIO_SUPORTE);
        log.info("[SPRING-MAIL] Usuário Solicitante: {} ({})", nomeUsuario != null ? nomeUsuario : "Não informado", emailUsuario);
        log.info("[SPRING-MAIL] Origem: {}", origem);
        log.info("[SPRING-MAIL] Descrição do Erro: {}", descricaoErro);
        if (mensagemErroSistema != null && !mensagemErroSistema.isBlank()) {
            log.info("[SPRING-MAIL] Mensagem de Erro da Tela: {}", mensagemErroSistema);
        }
        log.info("================================================================================");

        if (mailSender == null || host == null || host.isBlank() || host.equals("localhost-disabled")) {
            log.warn("[SPRING-MAIL] Servidor de e-mail não configurado. Chamado mantido no log do servidor.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String from = (remetente != null && !remetente.isBlank()) ? remetente : "no-reply@vertex.local";
            helper.setFrom(from, "VERTEX Suporte Técnico");
            helper.setTo(DESTINATARIO_SUPORTE);
            if (emailUsuario != null && !emailUsuario.isBlank()) {
                helper.setReplyTo(emailUsuario, (nomeUsuario != null && !nomeUsuario.isBlank()) ? nomeUsuario : emailUsuario);
            }
            helper.setSubject(assunto);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("[SPRING-MAIL] Chamado de suporte enviado com sucesso para: {}", DESTINATARIO_SUPORTE);
        } catch (Exception ex) {
            log.error("[SPRING-MAIL] Falha ao enviar chamado de suporte para {}: {}", DESTINATARIO_SUPORTE, ex.getMessage(), ex);
            throw new BusinessException("Não foi possível enviar o chamado de suporte por e-mail: " + ex.getMessage());
        }
    }

    public static final String DESTINATARIO_SUPORTE = "henriquecassol09@gmail.com";

    private void enviarEmail(String destinatario, String assunto, String htmlConteudo, String codigo, String tipo) {
        log.info("================================================================================");
        log.info("[SPRING-MAIL] >>> {} <<<", tipo);
        log.info("[SPRING-MAIL] Destinatário: {}", destinatario);
        log.info("[SPRING-MAIL] CÓDIGO DE 6 DÍGITOS: >>> {} <<<", codigo);
        log.info("================================================================================");

        if (mailSender == null || host == null || host.isBlank() || host.equals("localhost-disabled")) {
            log.warn("[SPRING-MAIL] Servidor de e-mail não configurado. Código mantido no log.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String from = (remetente != null && !remetente.isBlank()) ? remetente : "no-reply@vertex.local";
            helper.setFrom(from, "VERTEX Platform");
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(htmlConteudo, true);

            mailSender.send(message);
            log.info("[SPRING-MAIL] E-mail enviado com sucesso via Spring Mail para: {}", destinatario);
        } catch (Exception ex) {
            log.error("[SPRING-MAIL] Erro ao enviar e-mail para {}: {}", destinatario, ex.getMessage(), ex);
            throw new BusinessException("Não foi possível enviar o e-mail com o código: " + ex.getMessage());
        }
    }

    private String construirHtmlEmail(String nome, String titulo, String explicacao, String codigo, String rodapeAviso) {
        String saudacao = (nome != null && !nome.isBlank()) ? "Olá, <strong>" + nome + "</strong>!" : "Olá!";

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s</title>
              <style>
                body { margin: 0; padding: 0; background-color: #0f172a; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f8fafc; }
                .wrapper { width: 100%%; max-width: 560px; margin: 30px auto; background-color: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                .header { background: linear-gradient(135deg, #090d16, #1e293b); padding: 28px 24px 20px; text-align: center; border-bottom: 2px solid #ff5500; }
                .brand { font-size: 24px; font-weight: 800; letter-spacing: 2px; color: #ff5500; text-transform: uppercase; margin: 0; }
                .content { padding: 32px 28px; }
                .title { font-size: 18px; font-weight: 700; color: #ffffff; margin-top: 0; margin-bottom: 12px; }
                .text { font-size: 14px; line-height: 1.6; color: #cbd5e1; margin-bottom: 24px; }
                .code-box { background-color: #090d16; border: 1px dashed #ff5500; border-radius: 8px; padding: 18px; text-align: center; margin: 24px 0; }
                .code-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px; color: #94a3b8; margin-bottom: 8px; }
                .code { font-family: 'Courier New', Courier, monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #ff5500; margin: 0; }
                .footer { padding: 20px 28px; background-color: #090d16; border-top: 1px solid #334155; font-size: 12px; line-height: 1.5; color: #64748b; text-align: center; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1 class="brand">VERTEX</h1>
                </div>
                <div class="content">
                  <h2 class="title">%s</h2>
                  <p class="text">%s</p>
                  <p class="text">%s</p>
                  
                  <div class="code-box">
                    <div class="code-label">Código de Verificação</div>
                    <div class="code">%s</div>
                  </div>
                  
                  <p class="text" style="font-size: 12px; color: #94a3b8; margin-bottom: 0;">%s</p>
                </div>
                <div class="footer">
                  VERTEX Prospect & Intel • Sistema de Autenticação Aberto
                </div>
              </div>
            </body>
            </html>
            """.formatted(titulo, titulo, saudacao, explicacao, codigo, rodapeAviso);
    }

    private String construirHtmlSuporte(
            String emailUsuario,
            String nomeUsuario,
            String origem,
            String descricaoErro,
            String mensagemErroSistema,
            String ipOrigem,
            String userAgent
    ) {
        String dataHora = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss '(Horário de Brasília)'"));

        String emailEscapado = escaparHtml(emailUsuario);
        String nomeEscapado = nomeUsuario != null && !nomeUsuario.isBlank() ? escaparHtml(nomeUsuario) : "Não informado";
        String origemEscapada = escaparHtml(origem != null ? origem : "Não informada");
        String erroEscapado = escaparHtml(descricaoErro).replace("\n", "<br/>");
        String erroSistemaEscapado = (mensagemErroSistema != null && !mensagemErroSistema.isBlank())
                ? escaparHtml(mensagemErroSistema)
                : null;
        String ipEscapado = escaparHtml(ipOrigem != null ? ipOrigem : "Desconhecido");
        String uaEscapado = escaparHtml(userAgent != null ? userAgent : "Desconhecido");

        String blocoErroSistema = "";
        if (erroSistemaEscapado != null) {
            blocoErroSistema = """
                <div style="background-color: #450a0a; border: 1px solid #dc2626; border-radius: 8px; padding: 14px; margin-bottom: 20px;">
                  <strong style="color: #f87171; font-size: 12px; text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 6px;">Mensagem de Erro Apresentada na Tela:</strong>
                  <span style="color: #fecaca; font-family: 'Courier New', monospace; font-size: 13px;">%s</span>
                </div>
                """.formatted(erroSistemaEscapado);
        }

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Chamado de Suporte - VERTEX</title>
              <style>
                body { margin: 0; padding: 0; background-color: #0b0f19; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f8fafc; }
                .wrapper { width: 100%%; max-width: 620px; margin: 24px auto; background-color: #151d2f; border-radius: 12px; border: 1px solid #2d3b55; overflow: hidden; box-shadow: 0 12px 30px rgba(0,0,0,0.6); }
                .header { background: linear-gradient(135deg, #090d16, #1f293d); padding: 24px 28px; text-align: left; border-bottom: 3px solid #ff5500; display: flex; align-items: center; justify-content: space-between; }
                .brand { font-size: 22px; font-weight: 800; letter-spacing: 2px; color: #ff5500; text-transform: uppercase; margin: 0; }
                .badge { display: inline-block; background-color: #ea580c; color: #ffffff; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; padding: 4px 10px; border-radius: 9999px; }
                .content { padding: 28px; }
                .title { font-size: 19px; font-weight: 700; color: #ffffff; margin-top: 0; margin-bottom: 8px; }
                .subtitle { font-size: 13px; color: #94a3b8; margin-bottom: 24px; }
                .card { background-color: #0b0f19; border: 1px solid #233148; border-radius: 8px; padding: 18px; margin-bottom: 20px; }
                .field { margin-bottom: 10px; font-size: 13px; }
                .field-label { color: #94a3b8; font-weight: 600; display: inline-block; min-width: 130px; }
                .field-value { color: #f1f5f9; font-weight: 500; }
                .error-box { background-color: #1a1625; border-left: 4px solid #ff5500; border-radius: 4px; padding: 16px; margin: 20px 0; }
                .error-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #fb923c; margin-bottom: 8px; }
                .error-desc { font-size: 14px; line-height: 1.6; color: #ffffff; word-break: break-word; }
                .tech-info { font-size: 11px; color: #64748b; line-height: 1.6; border-top: 1px solid #233148; padding-top: 16px; margin-top: 24px; }
                .footer { padding: 16px 28px; background-color: #090d16; border-top: 1px solid #233148; font-size: 12px; color: #64748b; text-align: center; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <span class="brand">VERTEX</span>
                  <span class="badge">Suporte Técnico</span>
                </div>
                <div class="content">
                  <h1 class="title">Novo Chamado de Suporte Aberto</h1>
                  <p class="subtitle">Um usuário solicitou suporte referente a problemas no site.</p>

                  <div class="card">
                    <div class="field">
                      <span class="field-label">Aba / Contexto:</span>
                      <strong class="field-value" style="color: #ff7a33;">%s</strong>
                    </div>
                    <div class="field">
                      <span class="field-label">E-mail do Usuário:</span>
                      <a href="mailto:%s" class="field-value" style="color: #38bdf8; text-decoration: underline;">%s</a>
                    </div>
                    <div class="field">
                      <span class="field-label">Nome:</span>
                      <span class="field-value">%s</span>
                    </div>
                    <div class="field" style="margin-bottom: 0;">
                      <span class="field-label">Data e Hora:</span>
                      <span class="field-value">%s</span>
                    </div>
                  </div>

                  %s

                  <div class="error-box">
                    <div class="error-title">Relato do Erro no Site:</div>
                    <div class="error-desc">%s</div>
                  </div>

                  <div class="tech-info">
                    <strong>Dados de Diagnóstico Técnico:</strong><br/>
                    • IP de Origem: <code>%s</code><br/>
                    • Navegador / User-Agent: <code>%s</code>
                  </div>
                </div>
                <div class="footer">
                  VERTEX Prospect & Intel • Chamados encaminhados para henriquecassol09@gmail.com
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                origemEscapada,
                emailEscapado, emailEscapado,
                nomeEscapado,
                dataHora,
                blocoErroSistema,
                erroEscapado,
                ipEscapado,
                uaEscapado
        );
    }

    private String escaparHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

