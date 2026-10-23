package com.empresa.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Serviço de Defesa e Segurança para Imagens e Fotos de Perfil.
 * Previne ataques de:
 * 1. Upload de Arquivos Maliciosos / Polyglot / WebShells (re-codificação completa de pixels via BufferedImage).
 * 2. Stored XSS (bloqueio de arquivos SVG, HTML e scripts).
 * 3. Payload Bomb e DoS (limite estrito de 5 MB, compressão automática para ~25 KB).
 * 4. SSRF (Server-Side Request Forgery) em caso de URLs.
 */
@Slf4j
@Service
public class FotoUrlSecurityService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_URL_LENGTH = 500;

    private static final Pattern CARACTERES_PERIGOSOS = Pattern.compile("[<>'\"\\r\\n\\t\\s]");
    private static final Pattern EXTENSAO_SVG = Pattern.compile("(?i)\\.(svg|svgz)($|\\?|#)");

    private static final List<String> EXTENSOES_SEGURAS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif"
    );

    private static final List<String> PROVEDORES_CONHECIDOS = Arrays.asList(
            "gravatar.com",
            "githubusercontent.com",
            "googleusercontent.com",
            "imgur.com",
            "cloudinary.com",
            "unsplash.com",
            "images.unsplash.com",
            "cdn.jsdelivr.net",
            "res.cloudinary.com",
            "cloudflare.com",
            "discordapp.com",
            "twimg.com"
    );

    /**
     * Processa o upload de um arquivo de imagem com limite de até 5 MB.
     * Valida magic bytes, elimina metadados maliciosos, recorta em formato quadrado de avatar
     * e comprime em formato leve (~25 KB) para não onerar o Neon Postgres.
     */
    public String processarUploadFotoSegura(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Nenhum arquivo de imagem foi enviado.");
        }

        // 1. Limite estrito de 5 MB
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("A imagem excede o tamanho máximo permitido de 5 MB.");
        }

        // 2. Leitura dos bytes brutos
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Erro ao processar o arquivo enviado.");
        }

        if (bytes.length < 12) {
            throw new BusinessException("Arquivo corrompido ou formato de imagem não reconhecido.");
        }

        // 3. Bloqueio rigoroso de SVG / XML / HTML / Script embutido
        String cabecalhoStr = new String(bytes, 0, Math.min(bytes.length, 120), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (cabecalhoStr.contains("<svg") || cabecalhoStr.contains("<?xml") || cabecalhoStr.contains("<html") || cabecalhoStr.contains("<script")) {
            log.warn("Tentativa de upload de arquivo SVG/HTML disfarçado detectada e bloqueada!");
            throw new BusinessException("Arquivos SVG ou scripts não são permitidos por motivos de segurança.");
        }

        // 4. Verificação de Magic Bytes legítimos
        boolean isJpeg = (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
        boolean isPng = (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47);
        boolean isWebp = (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                          bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');

        if (!isJpeg && !isPng && !isWebp) {
            log.warn("Arquivo rejeitado por magic bytes inválidos: contentType={}", file.getContentType());
            throw new BusinessException("Formato de imagem inválido. Apenas imagens JPEG, PNG ou WebP são aceitas.");
        }

        // 5. Re-codificação e sanitização com BufferedImage (elimina payloads EXIF e webshells)
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                throw new BusinessException("Não foi possível decodificar a imagem. O arquivo pode estar corrompido.");
            }

            int origW = original.getWidth();
            int origH = original.getHeight();

            if (origW < 10 || origH < 10) {
                throw new BusinessException("Dimensões da imagem muito pequenas.");
            }

            // Recorte centralizado para avatar quadrado
            int cropSize = Math.min(origW, origH);
            int cropX = (origW - cropSize) / 2;
            int cropY = (origH - cropSize) / 2;
            BufferedImage cropped = original.getSubimage(cropX, cropY, cropSize, cropSize);

            // Redimensionamento para 256x256 pixels
            int targetDim = 256;
            BufferedImage resized = new BufferedImage(targetDim, targetDim, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, targetDim, targetDim);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(cropped, 0, 0, targetDim, targetDim, null);
            g2d.dispose();

            // Compressão JPEG otimizada (gera apenas ~20 KB a 30 KB)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            byte[] compressedBytes = baos.toByteArray();

            log.info("Upload de foto concluído com sucesso: original={} bytes -> comprimido={} bytes", file.getSize(), compressedBytes.length);

            String base64 = Base64.getEncoder().encodeToString(compressedBytes);
            return "data:image/jpeg;base64," + base64;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Erro ao processar imagem de perfil", e);
            throw new BusinessException("Erro ao processar a imagem enviada. Certifique-se de que é uma imagem válida.");
        }
    }

    /**
     * Valida URLs externas caso o usuário opte por link direto.
     */
    public void validarUrlFotoSegura(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String limpa = url.trim();

        if (limpa.startsWith("data:image/jpeg;base64,") || limpa.startsWith("data:image/png;base64,") || limpa.startsWith("data:image/webp;base64,")) {
            // Data URI gerado pelo sistema
            if (limpa.length() > 250000) {
                throw new BusinessException("Tamanho da imagem excede o limite.");
            }
            return;
        }

        if (limpa.length() > MAX_URL_LENGTH) {
            throw new BusinessException("A URL da foto excede o limite máximo permitido de 500 caracteres.");
        }

        if (CARACTERES_PERIGOSOS.matcher(limpa).find()) {
            throw new BusinessException("A URL contém caracteres inválidos ou potencialmente perigosos.");
        }

        if (!limpa.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new BusinessException("Por segurança, são aceitas apenas URLs com protocolo seguro 'https://'.");
        }

        if (EXTENSAO_SVG.matcher(limpa).find()) {
            throw new BusinessException("Imagens em formato SVG não são permitidas por motivos de segurança.");
        }

        URI uri;
        try {
            uri = URI.create(limpa);
        } catch (Exception e) {
            throw new BusinessException("Formato de URL inválido.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("URL inválida: nome de domínio (host) não identificado.");
        }

        String hostLow = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port != -1 && port != 443 && port != 8443) {
            throw new BusinessException("Porta de conexão não permitida para URL de imagem.");
        }

        if (hostLow.equals("localhost") ||
            hostLow.endsWith(".local") ||
            hostLow.endsWith(".internal") ||
            hostLow.endsWith(".lan") ||
            hostLow.startsWith("127.") ||
            hostLow.startsWith("192.168.") ||
            hostLow.startsWith("10.") ||
            hostLow.startsWith("172.16.") ||
            hostLow.startsWith("169.254.") ||
            hostLow.equals("0.0.0.0") ||
            hostLow.equals("::1")) {
            throw new BusinessException("A URL apontada para rede local ou interna não é permitida.");
        }

        try {
            InetAddress[] enderecos = InetAddress.getAllByName(host);
            for (InetAddress addr : enderecos) {
                if (addr.isLoopbackAddress() ||
                    addr.isAnyLocalAddress() ||
                    addr.isSiteLocalAddress() ||
                    addr.isLinkLocalAddress() ||
                    addr.isMulticastAddress()) {
                    throw new BusinessException("A URL fornecida resolve para um endereço IP privado ou reservado.");
                }

                String ipStr = addr.getHostAddress();
                if (ipStr.equals("169.254.169.254") || ipStr.startsWith("127.") || ipStr.startsWith("10.") || ipStr.startsWith("192.168.")) {
                    throw new BusinessException("Endereço IP não autorizado.");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("Não foi possível resolver o domínio da imagem.");
        }
    }
}
