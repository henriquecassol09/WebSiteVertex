package com.empresa.backend;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerenciador e rotacionador inteligente de API keys do Tavily.
 * Quando uma chave atinge o limite de requisições ou quota (HTTP 429, 432, erros de cota),
 * o sistema rotaciona automaticamente para a próxima chave válida configurada.
 */
@Slf4j
@Component
public class TavilyApiKeyRotator {

    @Value("${app.tavily.api-keys:}")
    private String apiKeysConfig;

    @Value("${app.tavily.api-key:}")
    private String singleApiKeyConfig;

    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Instant> falhasPorChave = new ConcurrentHashMap<>();

    public TavilyApiKeyRotator() {
        carregarDoAmbienteOuEnv();
    }

    public TavilyApiKeyRotator(List<String> chavesIniciais) {
        inicializarComChaves(chavesIniciais);
    }

    @PostConstruct
    public void init() {
        Set<String> chavesCarregadas = new LinkedHashSet<>();

        // 1. Carrega chaves da propriedade api-keys (separadas por vírgula ou ponto-e-vírgula)
        if (apiKeysConfig != null && !apiKeysConfig.isBlank()) {
            for (String k : apiKeysConfig.split("[,;\\n]")) {
                String limpa = k.trim();
                if (!limpa.isBlank()) {
                    chavesCarregadas.add(limpa);
                }
            }
        }

        // 2. Carrega chave legada/única se informada
        if (singleApiKeyConfig != null && !singleApiKeyConfig.isBlank()) {
            String limpa = singleApiKeyConfig.trim();
            if (!limpa.isBlank()) {
                chavesCarregadas.add(limpa);
            }
        }

        if (!chavesCarregadas.isEmpty()) {
            inicializarComChaves(new ArrayList<>(chavesCarregadas));
        } else {
            carregarDoAmbienteOuEnv();
        }

        if (keys.isEmpty()) {
            log.warn("[Tavily] Nenhuma chave de API configurada. Configure TAVILY_API_KEYS no .env para ativar as buscas.");
        } else {
            log.info("[Tavily] Sistema de rotação inicializado com {} chave(s). Chave ativa inicial: {}",
                    keys.size(), mascararChave(obterChaveAtual()));
        }
    }

    private void carregarDoAmbienteOuEnv() {
        String envKeys = System.getenv("TAVILY_API_KEYS");
        if (envKeys == null || envKeys.isBlank()) {
            envKeys = System.getProperty("TAVILY_API_KEYS");
        }
        if (envKeys == null || envKeys.isBlank()) {
            // Tenta ler do arquivo .env local se existir
            java.io.File[] possiveisEnvs = new java.io.File[]{
                    new java.io.File(".env"),
                    new java.io.File("backend/.env"),
                    new java.io.File("../.env")
            };
            for (java.io.File envFile : possiveisEnvs) {
                if (envFile.exists() && envFile.isFile()) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("TAVILY_API_KEYS=")) {
                                envKeys = line.substring("TAVILY_API_KEYS=".length()).trim();
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (envKeys != null && !envKeys.isBlank()) {
                        break;
                    }
                }
            }
        }

        if (envKeys != null && !envKeys.isBlank()) {
            List<String> list = new ArrayList<>();
            for (String k : envKeys.split("[,;\\n]")) {
                String limpa = k.trim();
                if (!limpa.isBlank()) list.add(limpa);
            }
            inicializarComChaves(list);
        }
    }

    public synchronized void inicializarComChaves(List<String> novasChaves) {
        keys.clear();
        falhasPorChave.clear();
        if (novasChaves != null) {
            for (String k : novasChaves) {
                if (k != null && !k.trim().isBlank() && !keys.contains(k.trim())) {
                    keys.add(k.trim());
                }
            }
        }
        currentIndex.set(0);
    }

    /**
     * Retorna a chave ativa atual para uso nas buscas.
     */
    public String obterChaveAtual() {
        if (keys.isEmpty()) {
            return null;
        }
        int idx = Math.abs(currentIndex.get() % keys.size());
        return keys.get(idx);
    }

    /**
     * Avança para a próxima chave configurada e registra o motivo da rotação.
     */
    public synchronized String rotacionarParaProxima(String chaveComFalha, String motivo) {
        if (keys.isEmpty()) {
            return null;
        }

        if (chaveComFalha != null) {
            falhasPorChave.put(chaveComFalha, Instant.now());
        }

        int total = keys.size();
        if (total <= 1) {
            log.warn("[Tavily] Apenas 1 chave configurada ({}). Impossível alternar chave. Motivo: {}",
                    mascararChave(chaveComFalha), motivo);
            return obterChaveAtual();
        }

        int anterior = Math.abs(currentIndex.get() % total);
        int novo = (anterior + 1) % total;
        currentIndex.set(novo);

        String novaChave = keys.get(novo);
        log.warn("[Tavily] LIMITE OU FALHA DETECTADA na chave {} (motivo: {}). Alternando para a próxima chave: {} ({}/{})",
                mascararChave(chaveComFalha),
                motivo,
                mascararChave(novaChave),
                novo + 1,
                total);

        return novaChave;
    }

    /**
     * Avalia se uma resposta HTTP da API Tavily indica que o limite de requisições/quota foi atingido
     * ou se a chave foi bloqueada por cota ou não autorizada.
     */
    public boolean isLimiteOuChaveInvalida(int statusCode, String responseBody) {
        // 429: Too Many Requests / Quota Exceeded (Padrão Tavily de limite de buscas)
        if (statusCode == 429) {
            return true;
        }
        // 432: Quota Exceeded / Project Paused
        if (statusCode == 432) {
            return true;
        }
        // 401 ou 403: Chave desativada por cota, não autorizada ou sem permissão
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }

        // Inspeção do corpo da resposta JSON para detecção de termos de limite
        if (responseBody != null) {
            String lower = responseBody.toLowerCase(Locale.ROOT);
            if (lower.contains("usage limit") ||
                lower.contains("limit exceeded") ||
                lower.contains("rate limit") ||
                lower.contains("quota") ||
                lower.contains("credit") ||
                lower.contains("unauthorized") ||
                lower.contains("forbidden") ||
                lower.contains("plan")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adiciona uma nova chave dinamicamente na lista de rotação.
     */
    public synchronized void adicionarChave(String novaChave) {
        if (novaChave != null && !novaChave.trim().isBlank()) {
            String limpa = novaChave.trim();
            if (!keys.contains(limpa)) {
                keys.add(limpa);
                log.info("[Tavily] Nova API key adicionada ao pool: {}. Total de chaves: {}",
                        mascararChave(limpa), keys.size());
            }
        }
    }

    public int getTotalChaves() {
        return keys.size();
    }

    public List<String> getChaves() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Oculta parte da chave para segurança e conformidade em logs.
     */
    public static String mascararChave(String chave) {
        if (chave == null || chave.isBlank()) {
            return "[VAZIA]";
        }
        if (chave.length() <= 12) {
            return chave.substring(0, Math.min(4, chave.length())) + "...";
        }
        return chave.substring(0, 8) + "..." + chave.substring(chave.length() - 4);
    }
}

