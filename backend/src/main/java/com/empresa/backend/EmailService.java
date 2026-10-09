package com.empresa.backend;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Serviço de E-mail Oficial com Spring Mail (JavaMailSender / Gmail SMTP).
 * Utiliza o protocolo SMTP autenticado via TLS para entrega real de mensagens.
 */
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
}
