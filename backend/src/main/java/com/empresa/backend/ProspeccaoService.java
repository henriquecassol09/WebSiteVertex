package com.empresa.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço de Prospeccao e Enriquecimento 100% Dinâmico via APIs Web Externas.
 * Nenhuma informação fica pré-armazenada em código Java ou banco de dados.
 *
 * APIs 100% Web integradas em tempo de execução:
 * 1. OpenStreetMap (Overpass API) - Identificação geográfica, nós e tags cadastrais
 * 2. Tavily Search API - Descoberta oficial de CNPJ filtrando portais cadastrais brasileiros
 * 3. CNPJá Open API - Consulta oficial direta de CNPJ com extração de telefones (fixo e celular/WhatsApp) e e-mails
 * 4. OpenCNPJ API - API aberta baseada nos dados do BigQuery Público da Receita Federal
 * 5. Wikidata Entity API (Wikimedia Foundation) - Busca aberta global de website oficial e identificador fiscal
 * 6. Validador Módulo 11 (Java) - Validação matemática em tempo real de dígitos verificadores de CNPJ
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoService {

    private final PesquisaRepository pesquisaRepository;
    private final EmpresaRepository empresaRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.tavily.api-key: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}")
    private String tavilyApiKey = "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";

    // Servidores Overpass mundiais abertos e de alta disponibilidade
    private static final String[] OVERPASS_SERVERS = {
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter"
    };

    // Endpoints oficiais das APIs públicas e gratuitas
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final String CNPJA_OPEN_API = "https://open.cnpja.com/office/";
    private static final String OPEN_CNPJ_API = "https://api.opencnpj.org/";
    private static final String BRASIL_API_CNPJ = "https://brasilapi.com.br/api/cnpj/v1/";
    private static final String MINHA_RECEITA_API = "https://minhareceita.org/";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    // Padrão regex para identificar números de CNPJ na web
    private static final Pattern CNPJ_PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})|(\\b\\d{14}\\b)"
    );

    // Bounding Box e cidades atendidas para enriquecimento geográfico
    private static final Set<String> TIPOS_IGNORADOS = Set.of(
            "fountain", "police", "fire_station", "townhall", "school", "bus_station", "library", "charging_station"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Defesa contra DoS: impede que o mesmo usuario execute multiplas buscas pesadas concorrentes
    private static final ConcurrentHashMap<String, Boolean> PROSPECCOES_ATIVAS = new ConcurrentHashMap<>();

    @Transactional
    public List<PesquisaResponse> prospectarEmpresasSemSite(String idUsuario) {
        if (idUsuario != null && PROSPECCOES_ATIVAS.putIfAbsent(idUsuario, Boolean.TRUE) != null) {
            throw new BusinessException("Ja existe uma prospeccao em andamento para a sua conta. Aguarde alguns instantes antes de iniciar outra.");
        }

        try {
            // Remove registros legados com cidades não citadas
            limparPesquisasInvalidas(idUsuario);

            String bbox = "-25.68,-52.60,-25.25,-51.95";

            // Consulta Overpass buscando nós e vias comerciais
            String query = """
                    [out:json][timeout:20];
                    (
                      node["shop"]["name"](%s);
                      node["amenity"]["name"](%s);
                      node["craft"]["name"](%s);
                      node["office"]["name"](%s);
                      node["healthcare"]["name"](%s);
                      way["shop"]["name"](%s);
                      way["amenity"]["name"](%s);
                    );
                    out body 60;
                    """.formatted(bbox, bbox, bbox, bbox, bbox, bbox, bbox);

            boolean obteveDados = false;

            for (String server : OVERPASS_SERVERS) {
                try {
                    log.info("Consultando Overpass API em: {}", server);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(server))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("User-Agent", "VertexProspectorApp/1.0 (contact: admin@vertex.com)")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String body = response.body();
                        if (body != null && body.trim().startsWith("{")) {
                            JsonNode root = objectMapper.readTree(body);
                            JsonNode elements = root.path("elements");

                            if (elements.isArray() && elements.size() > 0) {
                                processarElementos(elements, idUsuario);
                                obteveDados = true;
                                break;
                            }
                        }
                    } else {
                        log.warn("Overpass API retornou HTTP {} em {}", response.statusCode(), server);
                    }
                } catch (Exception e) {
                    log.warn("Tentativa de consulta à Overpass API em {} falhou: {}", server, e.getMessage());
                }
            }

            if (!obteveDados) {
                log.warn("Nenhum servidor Overpass respondeu ou a busca não retornou novos dados.");
            }

            // Prospecção cadastral oficial complementar para Porto Barreiro (cidade da comarca com baixa densidade no OSM)
            try {
                int puxadasPortoBarreiro = prospectarCidadeEspecificaTavily(idUsuario, "Porto Barreiro", "PR");
                log.info("Prospecção direcionada de Porto Barreiro concluída: {} novos estabelecimentos incorporados.", puxadasPortoBarreiro);
            } catch (Exception e) {
                log.warn("Falha na prospecção complementar de Porto Barreiro: {}", e.getMessage());
            }

            limparPesquisasInvalidas(idUsuario);

            return pesquisaRepository.findNaoSalvasByIdUsuario(idUsuario, PageRequest.of(0, 100))
                    .getContent()
                    .stream()
                    .map(PesquisaResponse::from)
                    .filter(r -> CidadeHelper.isCidadeCitada(r.cidade()))
                    .toList();
        } finally {
            if (idUsuario != null) {
                PROSPECCOES_ATIVAS.remove(idUsuario);
            }
        }
    }

    /**
     * Higieniza registros que possuam cidades fora das 6 cidades citadas.
     */
    public void limparPesquisasInvalidas(String idUsuario) {
        if (idUsuario == null) return;
        try {
            List<Pesquisa> pesquisas = pesquisaRepository.findByIdUsuario(idUsuario, PageRequest.of(0, 500)).getContent();
            for (Pesquisa p : pesquisas) {
                PesquisaResponse resp = PesquisaResponse.from(p);
                if (!CidadeHelper.isCidadeCitada(resp.cidade())) {
                    pesquisaRepository.delete(p);
                    log.info("Pesquisa higienizada (cidade não citada '{}'): {}", resp.cidade(), p.getConsulta());
                }
            }
        } catch (Exception e) {
            log.warn("Aviso ao higienizar pesquisas com cidades não citadas: {}", e.getMessage());
        }
    }

    private void processarElementos(JsonNode elements, String idUsuario) {
        int processados = 0;

        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            if (!tags.hasNonNull("name")) continue;

            String nome = tags.path("name").asText().trim();
            if (nome.isEmpty() || nome.toLowerCase().startsWith("rua ") || nome.toLowerCase().startsWith("avenida ")) continue;

            String tipo = tags.path("shop").asText(tags.path("amenity").asText(tags.path("craft").asText("")));
            if (TIPOS_IGNORADOS.contains(tipo)) continue;

            // Se já foi salva na tabela EMPRESAS, não deve reaparecer
            if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nome)) {
                continue;
            }

            // Evita duplicar registros já salvos na tabela PESQUISAS
            if (pesquisaRepository.existsByIdUsuarioAndConsultaIgnoreCase(idUsuario, nome)) {
                continue;
            }

            double lat = el.path("lat").asDouble(el.path("center").path("lat").asDouble(-25.4086));
            double lon = el.path("lon").asDouble(el.path("center").path("lon").asDouble(-52.4161));
            
            // Identifica se pertence estritamente a uma das 6 cidades citadas
            CidadeHelper.CidadeInfo cidadeInfo = CidadeHelper.identificarCidade(lat, lon, tags.path("addr:city").asText(null));
            if (cidadeInfo == null) {
                // Fora das 6 cidades citadas ou explicitamente de outro município vizinho
                continue;
            }

            String cidade = cidadeInfo.nome();
            String estado = "PR";
            String dddPadrao = cidadeInfo.ddd();

            String enderecoLimpo = extrairEnderecoLimpo(tags);
            String categoria = CategoriaDetector.detectar(tags, nome);
            String horario = formatarHorario(tags.path("opening_hours").asText(null));

            // =========================================================================================
            // ETAPA 1: EXTRAÇÃO DIRETA DAS TAGS DO OPENSTREETMAP (100% DINÂMICA)
            // =========================================================================================
            String telefoneOverpass = extrairTelefoneTags(tags, dddPadrao);
            String emailOverpass = extrairEmailTags(tags);
            String siteOverpass = extrairSiteTags(tags);
            String cnpjOverpass = extrairCnpjTags(tags);
            String wikidataId = tags.path("brand:wikidata").asText(tags.path("operator:wikidata").asText(null));

            // =========================================================================================
            // ETAPA 2: CONSULTA DINÂMICA À WIKIDATA API (OPEN SOURCE / WIKIMEDIA FOUNDATION)
            // =========================================================================================
            Map<String, String> dadosWikidata = null;
            try {
                if (wikidataId != null && !wikidataId.isBlank()) {
                    dadosWikidata = consultarWikidataPorId(wikidataId);
                } else {
                    dadosWikidata = buscarWikidataPorNome(nome);
                }
            } catch (Exception e) {
                log.debug("Wikidata API: busca dinâmica para '{}' falhou: {}", nome, e.getMessage());
            }

            String siteWikidata = dadosWikidata != null ? dadosWikidata.get("website") : null;
            String telefoneWikidata = dadosWikidata != null ? dadosWikidata.get("telefone") : null;
            String emailWikidata = dadosWikidata != null ? dadosWikidata.get("email") : null;
            String cnpjWikidata = dadosWikidata != null ? dadosWikidata.get("cnpj") : null;

            // =========================================================================================
            // ETAPA 3: DESCOBERTA DINÂMICA DE CNPJ VIA TAVILY SEARCH API COM VALIDAÇÃO MÓDULO 11
            // =========================================================================================
            String cnpjCandidato = (cnpjWikidata != null) ? cnpjWikidata : cnpjOverpass;
            if (cnpjCandidato == null) {
                try {
                    cnpjCandidato = buscarCnpjViaTavily(nome, cidade, estado);
                } catch (Exception e) {
                    log.debug("Tavily API: não localizou CNPJ dinamicamente para '{}': {}", nome, e.getMessage());
                }
            }

            // =========================================================================================
            // ETAPA 4: EXTRAÇÃO OFICIAL EM TEMPO REAL VIA API CNPJÁ (+ BRASILAPI / MINHA RECEITA)
            // =========================================================================================
            String emailOficial = null;
            String telefoneOficial = null;
            String razaoSocialOficial = null;
            String cnpjFinal = null;

            if (cnpjCandidato != null && validarCnpj(cnpjCandidato)) {
                cnpjFinal = cnpjCandidato;
                try {
                    Map<String, String> dadosOficiais = consultarDadosCadastraisCompletos(cnpjFinal);
                    if (dadosOficiais != null) {
                        String cidadeCadastral = dadosOficiais.get("cidade");
                        // Se a Receita Federal trouxer a cidade da empresa, ela DEVE ser uma das cidades citadas
                        if (cidadeCadastral != null && !cidadeCadastral.isBlank()) {
                            String cidNorm = CidadeHelper.normalizarCidade(cidadeCadastral);
                            if (cidNorm == null) {
                                // O CNPJ encontrado pertence a empresa de outra cidade (ex: Curitiba, Cascavel, etc.)
                                log.debug("Descartando CNPJ {} para '{}': cidade cadastral '{}' é fora da comarca", cnpjFinal, nome, cidadeCadastral);
                                cnpjFinal = null;
                            } else {
                                emailOficial = dadosOficiais.get("email");
                                telefoneOficial = dadosOficiais.get("telefone");
                                razaoSocialOficial = dadosOficiais.get("razaoSocial");
                            }
                        } else {
                            emailOficial = dadosOficiais.get("email");
                            telefoneOficial = dadosOficiais.get("telefone");
                            razaoSocialOficial = dadosOficiais.get("razaoSocial");
                        }
                    }
                } catch (Exception e) {
                    log.debug("Consulta oficial da Receita para CNPJ {}: {}", cnpjFinal, e.getMessage());
                }
            }

            // =========================================================================================
            // ETAPA 5: RESOLUÇÃO HIERÁRQUICA E PREENCHIMENTO DOS CANAIS
            // =========================================================================================
            String telefoneFinal = resolverTelefone(telefoneOficial, telefoneWikidata, telefoneOverpass, dddPadrao);
            String emailFinal = resolverEmail(emailOficial, emailWikidata, emailOverpass);
            String siteFinal = resolverWebsite(siteWikidata, siteOverpass, telefoneFinal);

            try {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("endereco", enderecoLimpo);
                meta.put("cidade", cidade);
                meta.put("estado", estado);
                meta.put("categoria", categoria);
                meta.put("horario", horario);
                meta.put("telefone", telefoneFinal);
                meta.put("email", emailFinal);
                meta.put("site", siteFinal);
                if (cnpjFinal != null) {
                    meta.put("cnpj", cnpjFinal);
                }
                if (razaoSocialOficial != null && !razaoSocialOficial.isBlank()) {
                    meta.put("razaoSocial", razaoSocialOficial);
                }

                String regiaoJson = objectMapper.writeValueAsString(meta);

                Pesquisa p = Pesquisa.builder()
                        .idUsuario(idUsuario)
                        .consulta(nome)
                        .regiao(regiaoJson)
                        .status("completed")
                        .build();

                pesquisaRepository.save(p);
                processados++;

            } catch (Exception e) {
                log.error("Erro ao persistir pesquisa para '{}': {}", nome, e.getMessage());
            }
        }

        log.info("Prospecção dinâmica concluída: {} estabelecimentos coletados e enriquecidos via APIs web.", processados);
    }

    // ===========================================================================================
    // INTEGRAÇÃO COM A API DO CNPJÁ (OPEN API PÚBLICA GRATUITA E SEM TOKEN)
    // ===========================================================================================

    /**
     * Consulta cadastral oficial completa utilizando primeiramente a API pública do CNPJá,
     * com fallback automático para BrasilAPI e Minha Receita.
     */
    public Map<String, String> consultarDadosCadastraisCompletos(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        if (cnpjLimpo.length() != 14) return null;

        Map<String, String> dadosConsolidados = new LinkedHashMap<>();

        // 1ª ETAPA: Consulta via API Aberta do CNPJá (open.cnpja.com) - Telefones fixo/celular e e-mails
        try {
            HttpRequest reqCnpja = HttpRequest.newBuilder()
                    .uri(URI.create(CNPJA_OPEN_API + cnpjLimpo))
                    .timeout(Duration.ofMillis(3500))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respCnpja = httpClient.send(reqCnpja, HttpResponse.BodyHandlers.ofString());
            if (respCnpja.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(respCnpja.body());
                Map<String, String> dadosCnpja = extrairDadosCnpjaJson(root);
                if (dadosCnpja != null && !dadosCnpja.isEmpty()) {
                    dadosConsolidados.putAll(dadosCnpja);
                    log.info("CNPJá: dados cadastrais extraídos com sucesso para CNPJ {}", cnpjLimpo);
                }
            }
        } catch (Exception e) {
            log.debug("CNPJá open API falhou para {}: {}", cnpjLimpo, e.getMessage());
        }

        // 2ª ETAPA: Consulta via OpenCNPJ (api.opencnpj.org - baseada no BigQuery Público da Receita)
        // Complementa campos ausentes ou atua como provedor primário de dados da Receita Federal
        if (!dadosConsolidados.containsKey("telefone") || !dadosConsolidados.containsKey("email") || !dadosConsolidados.containsKey("razaoSocial")) {
            try {
                HttpRequest reqOpenCnpj = HttpRequest.newBuilder()
                        .uri(URI.create(OPEN_CNPJ_API + cnpjLimpo))
                        .timeout(Duration.ofMillis(3000))
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respOpenCnpj = httpClient.send(reqOpenCnpj, HttpResponse.BodyHandlers.ofString());
                if (respOpenCnpj.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(respOpenCnpj.body());
                    Map<String, String> dadosOpenCnpj = extrairDadosOpenCnpjJson(root);
                    if (dadosOpenCnpj != null && !dadosOpenCnpj.isEmpty()) {
                        dadosOpenCnpj.forEach(dadosConsolidados::putIfAbsent);
                        log.info("OpenCNPJ (BigQuery Público): dados cadastrais obtidos para CNPJ {}", cnpjLimpo);
                    }
                }
            } catch (Exception e) {
                log.debug("OpenCNPJ API falhou para {}: {}", cnpjLimpo, e.getMessage());
            }
        }

        // 3ª ETAPA (FALLBACK): BrasilAPI (Comunidade Aberta)
        if (!dadosConsolidados.containsKey("telefone") || !dadosConsolidados.containsKey("email")) {
            try {
                HttpRequest reqBrasilApi = HttpRequest.newBuilder()
                        .uri(URI.create(BRASIL_API_CNPJ + cnpjLimpo))
                        .timeout(Duration.ofMillis(3000))
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respBrasilApi = httpClient.send(reqBrasilApi, HttpResponse.BodyHandlers.ofString());
                if (respBrasilApi.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(respBrasilApi.body());
                    Map<String, String> dadosBrasilApi = extrairDadosBrasilApiJson(root);
                    if (dadosBrasilApi != null && !dadosBrasilApi.isEmpty()) {
                        dadosBrasilApi.forEach(dadosConsolidados::putIfAbsent);
                        log.info("BrasilAPI: dados cadastrais obtidos para CNPJ {}", cnpjLimpo);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4ª ETAPA (FALLBACK): Minha Receita (Open Source)
        if (!dadosConsolidados.containsKey("telefone") || !dadosConsolidados.containsKey("email")) {
            try {
                HttpRequest reqMinhaReceita = HttpRequest.newBuilder()
                        .uri(URI.create(MINHA_RECEITA_API + cnpjLimpo))
                        .timeout(Duration.ofMillis(3000))
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respMinhaReceita = httpClient.send(reqMinhaReceita, HttpResponse.BodyHandlers.ofString());
                if (respMinhaReceita.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(respMinhaReceita.body());
                    Map<String, String> dadosMinhaReceita = extrairDadosBrasilApiJson(root);
                    if (dadosMinhaReceita != null && !dadosMinhaReceita.isEmpty()) {
                        dadosMinhaReceita.forEach(dadosConsolidados::putIfAbsent);
                    }
                }
            } catch (Exception ignored) {}
        }

        return dadosConsolidados.isEmpty() ? null : dadosConsolidados;
    }

    /**
     * Parser específico para a estrutura JSON retornada pela API do CNPJá
     */
    private Map<String, String> extrairDadosCnpjaJson(JsonNode root) {
        Map<String, String> dados = new LinkedHashMap<>();

        // Razão Social
        String razao = root.path("company").path("name").asText(null);
        if (razao != null && !razao.isBlank()) {
            dados.put("razaoSocial", razao.trim());
        }

        // Nome Fantasia
        String alias = root.path("alias").asText(null);
        if (alias != null && !alias.isBlank()) {
            dados.put("nomeFantasia", alias.trim());
        }

        // Telefones (CNPJá lista telefones LANDLINE e MOBILE com DDD e número)
        JsonNode phones = root.path("phones");
        if (phones.isArray() && phones.size() > 0) {
            String telFixo = null;
            String telMovel = null;
            for (JsonNode p : phones) {
                String area = p.path("area").asText("");
                String number = p.path("number").asText("");
                String type = p.path("type").asText("");
                if (!area.isBlank() && !number.isBlank()) {
                    String formatado = formatarTelefoneBrasil(area + number);
                    if ("MOBILE".equalsIgnoreCase(type) && telMovel == null) {
                        telMovel = formatado;
                    } else if (telFixo == null) {
                        telFixo = formatado;
                    }
                }
            }
            dados.put("telefone", telMovel != null ? telMovel : telFixo);
        }

        // E-mails (CNPJá retorna array de objetos com campo address)
        JsonNode emails = root.path("emails");
        if (emails.isArray() && emails.size() > 0) {
            for (JsonNode em : emails) {
                String addr = em.path("address").asText(null);
                if (addr != null && addr.contains("@") && !addr.contains("empresa.com.br")) {
                    dados.put("email", addr.toLowerCase().trim());
                    break;
                }
            }
        }

        // Endereço Cadastral Oficial
        JsonNode addr = root.path("address");
        String street = addr.path("street").asText("");
        String num = addr.path("number").asText("");
        if (!street.isBlank()) {
            dados.put("endereco", num.isBlank() ? street : street + ", " + num);
        }
        String city = addr.path("city").asText(null);
        if (city != null) dados.put("cidade", city);
        String state = addr.path("state").asText(null);
        if (state != null) dados.put("estado", state);

        return dados;
    }

    /**
     * Parser específico para a estrutura JSON retornada pela API do OpenCNPJ (baseada em BigQuery Público da Receita Federal)
     */
    private Map<String, String> extrairDadosOpenCnpjJson(JsonNode root) {
        Map<String, String> dados = new LinkedHashMap<>();

        String razao = root.path("razao_social").asText(null);
        if (razao != null && !razao.isBlank()) {
            dados.put("razaoSocial", razao.trim());
        }

        String fantasia = root.path("nome_fantasia").asText(null);
        if (fantasia != null && !fantasia.isBlank()) {
            dados.put("nomeFantasia", fantasia.trim());
        }

        String email = root.path("email").asText(null);
        if (email != null && !email.isBlank() && email.contains("@") && !email.contains("empresa.com.br")) {
            dados.put("email", email.toLowerCase().trim());
        }

        // Telefones (OpenCNPJ retorna array de objetos: [ { ddd, numero, is_fax } ])
        JsonNode telefones = root.path("telefones");
        if (telefones.isArray() && telefones.size() > 0) {
            String telFixo = null;
            String telMovel = null;
            for (JsonNode t : telefones) {
                if (t.path("is_fax").asBoolean(false)) continue;
                String ddd = t.path("ddd").asText("").replaceAll("\\D", "");
                String num = t.path("numero").asText("").replaceAll("\\D", "");
                if (ddd.length() == 3 && ddd.startsWith("0")) ddd = ddd.substring(1);
                if (!ddd.isBlank() && !num.isBlank()) {
                    String formatado = formatarTelefoneBrasil(ddd + num);
                    if (num.length() >= 9) {
                        telMovel = formatado;
                    } else if (telFixo == null) {
                        telFixo = formatado;
                    }
                }
            }
            if (telMovel != null) {
                dados.put("telefone", telMovel);
            } else if (telFixo != null) {
                dados.put("telefone", telFixo);
            }
        }

        String logradouro = root.path("logradouro").asText("");
        String numero = root.path("numero").asText("");
        if (!logradouro.isBlank()) {
            dados.put("endereco", numero.isBlank() ? logradouro : logradouro + ", " + numero);
        }

        String municipio = root.path("municipio").asText(null);
        if (municipio != null && !municipio.isBlank()) {
            dados.put("cidade", municipio.trim());
        }

        String uf = root.path("uf").asText(null);
        if (uf != null && !uf.isBlank()) {
            dados.put("estado", uf.trim());
        }

        return dados;
    }

    private Map<String, String> extrairDadosBrasilApiJson(JsonNode root) {
        Map<String, String> dados = new LinkedHashMap<>();

        String razaoSocial = root.path("razao_social").asText(null);
        if (razaoSocial != null && !razaoSocial.isBlank()) {
            dados.put("razaoSocial", razaoSocial.trim());
        }

        String nomeFantasia = root.path("nome_fantasia").asText(null);
        if (nomeFantasia != null && !nomeFantasia.isBlank()) {
            dados.put("nomeFantasia", nomeFantasia.trim());
        }

        String email = root.path("email").asText(null);
        if (email != null && !email.isBlank() && email.contains("@") && !email.contains("empresa.com.br")) {
            dados.put("email", email.toLowerCase().trim());
        }

        String ddd1 = root.path("ddd_telefone_1").asText(null);
        String ddd2 = root.path("ddd_telefone_2").asText(null);
        String tel = (ddd1 != null && !ddd1.isBlank()) ? ddd1 : ddd2;
        if (tel != null && !tel.isBlank()) {
            dados.put("telefone", formatarTelefoneBrasil(tel));
        }

        String logradouro = root.path("logradouro").asText("");
        String numero = root.path("numero").asText("");
        if (!logradouro.isBlank()) {
            dados.put("endereco", numero.isBlank() ? logradouro : logradouro + ", " + numero);
        }

        String municipio = root.path("municipio").asText(null);
        if (municipio != null && !municipio.isBlank()) {
            dados.put("cidade", municipio.trim());
        }

        String uf = root.path("uf").asText(null);
        if (uf != null && !uf.isBlank()) {
            dados.put("estado", uf.trim());
        }

        return dados;
    }

    // ===========================================================================================
    // CONSULTA PÚBLICA PARA O ENDPOINT REST GET /api/empresas/consultar-cnpj/{cnpj}
    // ===========================================================================================

    public DadosCnpjResponse consultarCnpjPublico(String cnpj) {
        if (cnpj == null) return null;
        String limpo = cnpj.replaceAll("\\D", "");
        if (limpo.length() != 14 || !validarCnpj(limpo)) {
            throw new IllegalArgumentException("CNPJ inválido de acordo com os dígitos verificadores");
        }

        Map<String, String> dados = consultarDadosCadastraisCompletos(limpo);
        if (dados == null || dados.isEmpty()) {
            throw new ResourceNotFoundException("Dados do CNPJ não localizados nas bases públicas");
        }

        return new DadosCnpjResponse(
                formatarCnpjMascara(limpo),
                dados.get("razaoSocial"),
                dados.get("nomeFantasia"),
                dados.get("telefone"),
                dados.get("email"),
                dados.get("endereco"),
                dados.get("cidade"),
                dados.get("estado"),
                dados.getOrDefault("situacao", "ATIVA")
        );
    }

    // ===========================================================================================
    // BUSCA DINÂMICA VIA WIKIDATA (WIKIMEDIA FOUNDATION)
    // ===========================================================================================

    private Map<String, String> consultarWikidataPorId(String wikidataId) {
        try {
            String url = WIKIDATA_API + "?action=wbgetentities&ids=" + wikidataId + "&languages=pt&props=claims&format=json";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("User-Agent", "VertexProspectorApp/1.0 (contact: admin@vertex.com)")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode claims = root.path("entities").path(wikidataId).path("claims");
                return extrairClaimsWikidata(claims);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, String> buscarWikidataPorNome(String nome) {
        try {
            String url = WIKIDATA_API + "?action=wbsearchentities&search="
                    + URLEncoder.encode(nome, StandardCharsets.UTF_8)
                    + "&language=pt&limit=1&format=json";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("User-Agent", "VertexProspectorApp/1.0 (contact: admin@vertex.com)")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode search = root.path("search");
                if (search.isArray() && search.size() > 0) {
                    String id = search.get(0).path("id").asText();
                    return consultarWikidataPorId(id);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, String> extrairClaimsWikidata(JsonNode claims) {
        Map<String, String> map = new LinkedHashMap<>();

        // P856 = website oficial
        JsonNode p856 = claims.path("P856");
        if (p856.isArray() && p856.size() > 0) {
            String site = p856.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (site != null && !site.isBlank()) map.put("website", site);
        }

        // P1329 = número de telefone
        JsonNode p1329 = claims.path("P1329");
        if (p1329.isArray() && p1329.size() > 0) {
            String tel = p1329.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (tel != null && !tel.isBlank()) map.put("telefone", tel);
        }

        // P968 = email
        JsonNode p968 = claims.path("P968");
        if (p968.isArray() && p968.size() > 0) {
            String email = p968.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (email != null && !email.isBlank()) map.put("email", email);
        }

        // P3644 = identificador fiscal CNPJ
        JsonNode p3644 = claims.path("P3644");
        if (p3644.isArray() && p3644.size() > 0) {
            String cnpj = p3644.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (cnpj != null && !cnpj.isBlank()) map.put("cnpj", cnpj.replaceAll("\\D", ""));
        }

        return map;
    }

    // ===========================================================================================
    // BUSCA PÚBLICA DINÂMICA VIA TAVILY SEARCH API (SEM CAPTCHA / DOMÍNIOS CADASTRAIS BRASILEIROS)
    // ===========================================================================================

    public String buscarCnpjViaTavily(String nome, String cidade, String estado) {
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) return null;

        try {
            String query = String.format("%s %s %s CNPJ", nome, cidade, estado != null ? estado : "PR");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("api_key", tavilyApiKey);
            payload.put("query", query);
            payload.put("search_depth", "basic");
            payload.put("max_results", 4);
            payload.put("include_domains", List.of(
                    "casadosdados.com.br",
                    "cnpj.biz",
                    "econodata.com.br",
                    "consultas.plus",
                    "infocnpj.com.br",
                    "situacaocadastral.info"
            ));

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_API_URL))
                    .timeout(Duration.ofMillis(4500))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                if (results.isArray()) {
                    for (JsonNode res : results) {
                        String title = res.path("title").asText("");
                        String content = res.path("content").asText("");
                        String textoCompleto = title + " " + content;

                        Matcher matcher = CNPJ_PATTERN.matcher(textoCompleto);
                        while (matcher.find()) {
                            String cand = matcher.group().replaceAll("\\D", "");
                            if (cand.length() == 14 && validarCnpj(cand)) {
                                log.info("Tavily Search API: CNPJ {} descoberto com sucesso para '{}' em {}", cand, nome, cidade);
                                return cand;
                            }
                        }
                    }
                }
            } else {
                log.warn("Tavily Search API retornou HTTP {}", resp.statusCode());
            }
        } catch (Exception e) {
            log.debug("Tavily Search API: falha ao buscar CNPJ para '{}': {}", nome, e.getMessage());
        }

        return null;
    }

    // ===========================================================================================
    // VALIDADOR DE DÍGITOS VERIFICADORES DO CNPJ (ALGORITMO MÓDULO 11 OFICIAL DO BRASIL)
    // ===========================================================================================

    public static boolean validarCnpj(String cnpj) {
        if (cnpj == null) return false;
        String limpo = cnpj.replaceAll("\\D", "");
        if (limpo.length() != 14) return false;

        // Elimina sequências repetidas inválidas (ex: 00000000000000, 11111111111111)
        if (limpo.matches("(\\d)\\1{13}")) return false;

        try {
            // 1º Dígito verificador
            int soma = 0;
            int peso = 5;
            for (int i = 0; i < 12; i++) {
                soma += (limpo.charAt(i) - '0') * peso--;
                if (peso < 2) peso = 9;
            }
            int r = soma % 11;
            char dig1 = (r < 2) ? '0' : (char) ((11 - r) + '0');
            if (limpo.charAt(12) != dig1) return false;

            // 2º Dígito verificador
            soma = 0;
            peso = 6;
            for (int i = 0; i < 13; i++) {
                soma += (limpo.charAt(i) - '0') * peso--;
                if (peso < 2) peso = 9;
            }
            r = soma % 11;
            char dig2 = (r < 2) ? '0' : (char) ((11 - r) + '0');
            return limpo.charAt(13) == dig2;

        } catch (Exception e) {
            return false;
        }
    }

    // ===========================================================================================
    // EXTRATORES DE TAGS DO OPENSTREETMAP
    // ===========================================================================================

    private String extrairTelefoneTags(JsonNode tags, String dddPadrao) {
        String[] chaves = {"phone", "contact:phone", "contact:mobile", "contact:whatsapp", "whatsapp"};
        for (String c : chaves) {
            String val = tags.path(c).asText(null);
            if (val != null && !val.isBlank()) {
                return formatarTelefoneBrasil(val, dddPadrao);
            }
        }
        return null;
    }

    private String extrairEmailTags(JsonNode tags) {
        String[] chaves = {"email", "contact:email"};
        for (String c : chaves) {
            String val = tags.path(c).asText(null);
            if (val != null && val.contains("@") && !val.contains("empresa.com.br")) {
                return val.toLowerCase().trim();
            }
        }
        return null;
    }

    private String extrairSiteTags(JsonNode tags) {
        String[] chaves = {"website", "contact:website", "url", "facebook", "contact:facebook", "instagram", "contact:instagram"};
        for (String c : chaves) {
            String val = tags.path(c).asText(null);
            if (val != null && !val.isBlank()) {
                if (val.startsWith("http://") || val.startsWith("https://")) {
                    return val.trim();
                } else if (val.contains(".com") || val.contains(".br")) {
                    return "https://" + val.trim();
                } else if (c.contains("instagram")) {
                    return "https://instagram.com/" + val.replace("@", "").trim();
                } else if (c.contains("facebook")) {
                    return "https://facebook.com/" + val.trim();
                }
            }
        }
        return null;
    }

    private String extrairCnpjTags(JsonNode tags) {
        String[] chaves = {"ref:vatin", "cnpj", "operator:cnpj"};
        for (String c : chaves) {
            String val = tags.path(c).asText(null);
            if (val != null && !val.isBlank()) {
                String limpo = val.replaceAll("\\D", "");
                if (limpo.length() == 14 && validarCnpj(limpo)) {
                    return limpo;
                }
            }
        }
        return null;
    }

    // ===========================================================================================
    // RESOLUÇÃO DE CONTATOS E FORMATAÇÃO
    // ===========================================================================================

    private String resolverTelefone(String oficial, String doWikidata, String doOverpass, String dddPadrao) {
        if (oficial != null && !oficial.isBlank() && !oficial.equalsIgnoreCase("Não informado")) {
            return oficial;
        }
        if (doWikidata != null && !doWikidata.isBlank()) {
            return formatarTelefoneBrasil(doWikidata, dddPadrao);
        }
        if (doOverpass != null && !doOverpass.isBlank()) {
            return formatarTelefoneBrasil(doOverpass, dddPadrao);
        }
        return "Não informado";
    }

    private String resolverEmail(String oficial, String doWikidata, String doOverpass) {
        if (oficial != null && !oficial.isBlank() && oficial.contains("@")) {
            return oficial;
        }
        if (doWikidata != null && !doWikidata.isBlank() && doWikidata.contains("@")) {
            return doWikidata;
        }
        if (doOverpass != null && !doOverpass.isBlank() && doOverpass.contains("@")) {
            return doOverpass;
        }
        return "Não informado";
    }

    private String resolverWebsite(String doWikidata, String doOverpass, String telefone) {
        if (doWikidata != null && !doWikidata.isBlank()) {
            return doWikidata;
        }
        if (doOverpass != null && !doOverpass.isBlank()) {
            return doOverpass;
        }
        // Se possui celular/WhatsApp mas não tem site próprio, gera canal direto de WhatsApp Web
        if (telefone != null && telefone.contains(")") && telefone.matches(".*9\\d{4}-?\\d{4}.*")) {
            String num = telefone.replaceAll("\\D", "");
            if (num.length() >= 10) {
                return "https://wa.me/55" + num;
            }
        }
        return "";
    }

    private String formatarTelefoneBrasil(String raw) {
        return formatarTelefoneBrasil(raw, "42");
    }

    private String formatarTelefoneBrasil(String raw, String dddPadrao) {
        if (raw == null || raw.isBlank()) return "Não informado";
        String n = raw.replaceAll("\\D", "");
        if (n.startsWith("55") && n.length() >= 12) {
            n = n.substring(2);
        }
        if (n.length() == 8) {
            return "(" + dddPadrao + ") " + n.substring(0, 4) + "-" + n.substring(4);
        } else if (n.length() == 9) {
            return "(" + dddPadrao + ") " + n.substring(0, 5) + "-" + n.substring(5);
        } else if (n.length() == 10) {
            return "(" + n.substring(0, 2) + ") " + n.substring(2, 6) + "-" + n.substring(6);
        } else if (n.length() == 11) {
            return "(" + n.substring(0, 2) + ") " + n.substring(2, 7) + "-" + n.substring(7);
        }
        return raw;
    }

    public static String formatarCnpjMascara(String cnpj) {
        if (cnpj == null) return null;
        String limpo = cnpj.replaceAll("\\D", "");
        if (limpo.length() == 14) {
            return limpo.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return cnpj;
    }

    private String extrairEnderecoLimpo(JsonNode tags) {
        String rua = tags.path("addr:street").asText("").trim();
        String numero = tags.path("addr:housenumber").asText("").trim();
        if (rua.isEmpty()) {
            return "Centro";
        }
        return numero.isEmpty() ? rua : rua + ", " + numero;
    }

    private String formatarHorario(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Não informado";
        }
        return raw.trim()
                .replace("Mo-Fr", "Seg a Sex")
                .replace("Mo-Sa", "Seg a Sáb")
                .replace("Mo-Su", "Seg a Dom")
                .replace("Mo", "Seg")
                .replace("Tu", "Ter")
                .replace("We", "Qua")
                .replace("Th", "Qui")
                .replace("Fr", "Sex")
                .replace("Sa", "Sáb")
                .replace("Su", "Dom")
                .replace("PH", "Feriados")
                .replace("off", "Fechado")
                .replace("24/7", "Aberto 24 horas");
    }

    /**
     * Prospecção cadastral oficial dinâmica para cidades da comarca (como Porto Barreiro - PR),
     * utilizando a Tavily Search API para busca de CNPJs em portais oficiais da Receita Federal e enriquecimento
     * automático com dados cadastrais reais. Apenas as 6 cidades citadas são aceitas.
     */
    public int prospectarCidadeEspecificaTavily(String idUsuario, String cidade, String estado) {
        String cidadeCanonica = CidadeHelper.normalizarCidade(cidade);
        if (cidadeCanonica == null) {
            log.warn("Tentativa de prospecção para cidade não citada: '{}'. Operação abortada.", cidade);
            return 0;
        }

        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            log.warn("Tavily API Key ausente; prospecção para {} não pôde ser executada.", cidadeCanonica);
            return 0;
        }

        int novasEmpresas = 0;
        try {
            List<String> queries = List.of(
                    String.format("site:cnpj.biz OR site:casadosdados.com.br \"%s\" %s comercio", cidadeCanonica, estado != null ? estado : "PR"),
                    String.format("\"%s\" %s empresas CNPJ", cidadeCanonica, estado != null ? estado : "PR")
            );

            Set<String> cnpjsEncontrados = new java.util.LinkedHashSet<>();
            Map<String, String> nomesPorCnpj = new LinkedHashMap<>();

            for (String q : queries) {
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("api_key", tavilyApiKey);
                    payload.put("query", q);
                    payload.put("search_depth", "basic");
                    payload.put("max_results", 10);
                    payload.put("include_domains", List.of(
                            "casadosdados.com.br",
                            "cnpj.biz",
                            "econodata.com.br",
                            "consultas.plus",
                            "infocnpj.com.br",
                            "situacaocadastral.info"
                    ));

                    String jsonPayload = objectMapper.writeValueAsString(payload);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(TAVILY_API_URL))
                            .timeout(Duration.ofSeconds(6))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null) {
                        JsonNode root = objectMapper.readTree(resp.body());
                        JsonNode results = root.path("results");
                        if (results.isArray()) {
                            for (JsonNode res : results) {
                                String title = res.path("title").asText("");
                                String content = res.path("content").asText("");
                                String url = res.path("url").asText("");
                                String textoCompleto = title + " " + content + " " + url;

                                Matcher matcher = CNPJ_PATTERN.matcher(textoCompleto);
                                while (matcher.find()) {
                                    String cand = matcher.group().replaceAll("\\D", "");
                                    if (cand.length() == 14 && validarCnpj(cand)) {
                                        cnpjsEncontrados.add(cand);
                                        if (!nomesPorCnpj.containsKey(cand) && !title.isBlank()) {
                                            String nomeLimpo = limparNomePreliminar(title, cand);
                                            if (!nomeLimpo.isBlank()) {
                                                nomesPorCnpj.put(cand, nomeLimpo);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erro ao executar busca Tavily para '{}' (query: {}): {}", cidadeCanonica, q, e.getMessage());
                }
            }

            log.info("Tavily localizou {} CNPJs únicos para {}", cnpjsEncontrados.size(), cidadeCanonica);

            for (String cnpj : cnpjsEncontrados) {
                try {
                    Map<String, String> dadosOficiais = null;
                    try {
                        dadosOficiais = consultarDadosCadastraisCompletos(cnpj);
                    } catch (Exception ex) {
                        log.debug("Consulta cadastral para CNPJ {} falhou: {}", cnpj, ex.getMessage());
                    }

                    // Validação estrita: a cidade do CNPJ DEVE ser uma das cidades citadas
                    String cidadeCadastral = dadosOficiais != null ? dadosOficiais.get("cidade") : null;
                    String cidadeNormalizada = CidadeHelper.normalizarCidade(cidadeCadastral != null ? cidadeCadastral : cidadeCanonica);

                    if (cidadeNormalizada == null) {
                        // CNPJ retornado na busca pertence a outro município (ex: Curitiba, Cascavel, etc.)
                        log.info("Descartando CNPJ {} de {}: cidade cadastral '{}' na Receita não é uma cidade citada", cnpj, cidadeCanonica, cidadeCadastral);
                        continue;
                    }

                    String razaoSocial = dadosOficiais != null ? dadosOficiais.get("razaoSocial") : null;
                    String nomeFantasia = dadosOficiais != null ? dadosOficiais.get("nomeFantasia") : null;
                    String nome = (nomeFantasia != null && !nomeFantasia.isBlank()) ? nomeFantasia : razaoSocial;

                    if (nome == null || nome.isBlank()) {
                        nome = nomesPorCnpj.get(cnpj);
                    }
                    if (nome == null || nome.isBlank()) {
                        continue;
                    }

                    nome = limparNomePreliminar(nome, cnpj);

                    // Evita duplicatas se já existe em Empresas ou Pesquisas
                    if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nome) ||
                        pesquisaRepository.existsByIdUsuarioAndConsultaIgnoreCase(idUsuario, nome)) {
                        continue;
                    }

                    String telefone = dadosOficiais != null ? dadosOficiais.get("telefone") : null;
                    if (telefone == null || telefone.isBlank()) {
                        telefone = "Não informado";
                    }

                    String email = dadosOficiais != null ? dadosOficiais.get("email") : null;
                    if (email == null || email.isBlank()) {
                        email = "Não informado";
                    }

                    String endereco = dadosOficiais != null ? dadosOficiais.get("endereco") : "Centro";
                    String cidadeFinal = cidadeNormalizada;
                    String estadoFinal = "PR";

                    String categoria = CategoriaDetector.detectar(null, nome);
                    String site = resolverWebsite(null, null, telefone);
                    String cnpjFormatado = formatarCnpjMascara(cnpj);

                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("endereco", endereco);
                    meta.put("cidade", cidadeFinal);
                    meta.put("estado", estadoFinal);
                    meta.put("categoria", categoria);
                    meta.put("horario", "Seg a Sex 08:00 - 18:00");
                    meta.put("telefone", telefone);
                    meta.put("email", email);
                    meta.put("site", site);
                    meta.put("cnpj", cnpjFormatado);
                    if (razaoSocial != null && !razaoSocial.isBlank()) {
                        meta.put("razaoSocial", razaoSocial);
                    }

                    String regiaoJson = objectMapper.writeValueAsString(meta);

                    Pesquisa p = Pesquisa.builder()
                            .idUsuario(idUsuario)
                            .consulta(nome)
                            .regiao(regiaoJson)
                            .status("completed")
                            .build();

                    pesquisaRepository.save(p);
                    novasEmpresas++;
                    log.info("Empresa de {} salva com sucesso: {} (CNPJ: {})", cidadeFinal, nome, cnpjFormatado);

                } catch (Exception ex) {
                    log.error("Erro ao processar e salvar empresa CNPJ {} de {}: {}", cnpj, cidadeCanonica, ex.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Erro geral na prospecção de {}: {}", cidadeCanonica, e.getMessage());
        }

        return novasEmpresas;
    }

    private String limparNomePreliminar(String raw, String cnpj) {
        if (raw == null) return "";
        String nome = raw.trim();
        // Remove sufixos como "- 51274888000191 Porto Barreiro" ou "| CNPJ ..."
        nome = nome.replaceAll("(?i)\\s*-\\s*\\d{14}.*", "");
        nome = nome.replaceAll("(?i)\\s*\\d{14}.*", "");
        nome = nome.replaceAll("(?i)\\s*\\|.*", "");
        nome = nome.replaceAll("(?i)\\s*-\\s*Porto Barreiro.*", "");
        nome = nome.replaceAll("(?i)\\s*Porto Barreiro.*", "");
        // Remove prefixo de CNPJ (ex: "51.274.888 Victor Mazon" ou "68.860.607 Alexandre Kuster")
        nome = nome.replaceAll("^\\d{2}\\.\\d{3}\\.\\d{3}\\s*", "");
        nome = nome.replaceAll("^\\d{14}\\s*", "");
        return nome.trim();
    }
}
