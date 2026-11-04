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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoService {

    private final PesquisaRepository pesquisaRepository;
    private final EmpresaRepository empresaRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.tavily.api-key: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}")
    private String tavilyApiKey = "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";

    private static final String[] OVERPASS_SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    };

    private static final ConcurrentHashMap<String, Map<String, String>> CNPJ_CACHE = new ConcurrentHashMap<>();

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final String CNPJA_OPEN_API = "https://open.cnpja.com/office/";
    private static final String OPEN_CNPJ_API = "https://api.opencnpj.org/";
    private static final String BRASIL_API_CNPJ = "https://brasilapi.com.br/api/cnpj/v1/";
    private static final String MINHA_RECEITA_API = "https://minhareceita.org/";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final Pattern CNPJ_PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})|(\\b\\d{14}\\b)"
    );

    private static final Set<String> TIPOS_IGNORADOS = Set.of(
            "fountain", "police", "fire_station", "townhall", "school", "bus_station", "library", "charging_station"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ConcurrentHashMap<String, Boolean> PROSPECCOES_ATIVAS = new ConcurrentHashMap<>();

    // Prospecção via Overpass (OpenStreetMap) e Tavily regional
    public List<PesquisaResponse> prospectarEmpresasSemSite(String idUsuario) {
        if (idUsuario != null && PROSPECCOES_ATIVAS.putIfAbsent(idUsuario, Boolean.TRUE) != null) {
            throw new BusinessException("Ja existe uma prospeccao em andamento para a sua conta. Aguarde alguns instantes antes de iniciar outra.");
        }

        try {
            limparPesquisasInvalidas(idUsuario);

            String bbox = "-25.68,-52.60,-25.25,-51.95";

            String query = """
                    [out:json][timeout:10];
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
                            .timeout(Duration.ofSeconds(4))
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

            // Prospecção regional assíncrona para todas as 6 cidades citadas
            try {
                prospectarTodasCidadesRegiao(idUsuario);
            } catch (Exception e) {
                log.warn("Falha na prospecção complementar regional: {}", e.getMessage());
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

    public void prospectarTodasCidadesRegiao(String idUsuario) {
        log.info("Iniciando prospecção regional paralela para todas as 6 cidades citadas (usuário: {})", idUsuario);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<CompletableFuture<Integer>> futures = CidadeHelper.CIDADES_CITADAS.stream()
                    .map(info -> CompletableFuture.supplyAsync(() -> {
                        try {
                            int novos = prospectarCidadeEspecificaTavily(idUsuario, info.nome(), "PR");
                            log.info("Prospecção regional para {}: {} novos estabelecimentos incorporados.", info.nome(), novos);
                            return novos;
                        } catch (Exception ex) {
                            log.warn("Falha na prospecção complementar de {}: {}", info.nome(), ex.getMessage());
                            return 0;
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(45, TimeUnit.SECONDS);

            int total = futures.stream().mapToInt(f -> {
                try { return f.getNow(0); } catch (Exception e) { return 0; }
            }).sum();
            log.info("Prospecção complementar regional concluída: {} novos estabelecimentos incorporados no total.", total);
        } catch (Exception e) {
            log.warn("Aviso ou limite de tempo na prospecção regional: {}", e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    public void limparPesquisasInvalidas(String idUsuario) {
        if (idUsuario == null) return;
        try {
            List<Pesquisa> pesquisas = pesquisaRepository.findByIdUsuario(idUsuario, PageRequest.of(0, 500)).getContent();
            for (Pesquisa p : pesquisas) {
                PesquisaResponse resp = PesquisaResponse.from(p);
                String consLow = (p.getConsulta() != null ? p.getConsulta() : "").toLowerCase();
                boolean entidadeFantasma = consLow.contains("usina solar") || consLow.contains("locacao de usina") ||
                        consLow.contains("holding") || consLow.contains("administracao de bens") ||
                        consLow.contains("participacoes ltda");

                if (!CidadeHelper.isCidadeCitada(resp.cidade()) || entidadeFantasma) {
                    pesquisaRepository.delete(p);
                    log.info("Pesquisa higienizada (cidade ou entidade inválida '{}'): {}", resp.cidade(), p.getConsulta());
                }
            }
        } catch (Exception e) {
            log.warn("Aviso ao higienizar pesquisas: {}", e.getMessage());
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

            if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nome)) {
                continue;
            }

            if (pesquisaRepository.existsByIdUsuarioAndConsultaIgnoreCase(idUsuario, nome)) {
                continue;
            }

            double lat = el.path("lat").asDouble(el.path("center").path("lat").asDouble(Double.NaN));
            double lon = el.path("lon").asDouble(el.path("center").path("lon").asDouble(Double.NaN));
            
            String cidadeTag = tags.path("addr:city").asText(tags.path("addr:municipality").asText(tags.path("is_in:city").asText(null)));
            
            String nomeLow = nome.toLowerCase();
            if (cidadeTag != null && !cidadeTag.isBlank() && !CidadeHelper.isCidadeCitada(cidadeTag)) {
                continue; // Obrigatório pertencer exclusivamente às cidades cadastradas
            }

            if (nomeLow.contains("cantagalo")) {
                cidadeTag = "Cantagalo";
            } else if (nomeLow.contains("virmond")) {
                cidadeTag = "Virmond";
            } else if (nomeLow.contains("porto barreiro")) {
                cidadeTag = "Porto Barreiro";
            } else if (nomeLow.contains("nova laranjeiras")) {
                cidadeTag = "Nova Laranjeiras";
            } else if (nomeLow.contains("rio bonito")) {
                cidadeTag = "Rio Bonito do Iguaçu";
            } else if (nomeLow.contains("laranjeiras do sul")) {
                cidadeTag = "Laranjeiras do Sul";
            }

            CidadeHelper.CidadeInfo cidadeInfo = CidadeHelper.identificarCidade(lat, lon, cidadeTag);
            if (cidadeInfo == null) {
                continue;
            }

            String cidade = cidadeInfo.nome();
            String estado = "PR";
            String dddPadrao = cidadeInfo.ddd();

            String enderecoLimpo = extrairEnderecoLimpo(tags, nome, lat, lon);
            String categoria = CategoriaDetector.detectar(tags, nome);
            String horario = formatarHorario(tags.path("opening_hours").asText(null));

            String telefoneOverpass = extrairTelefoneTags(tags, dddPadrao);
            String emailOverpass = extrairEmailTags(tags);
            String siteOverpass = extrairSiteTags(tags);
            String cnpjOverpass = extrairCnpjTags(tags);
            String wikidataId = tags.path("brand:wikidata").asText(tags.path("operator:wikidata").asText(null));

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

            String cnpjCandidato = (cnpjWikidata != null) ? cnpjWikidata : cnpjOverpass;
            if (cnpjCandidato == null) {
                try {
                    cnpjCandidato = buscarCnpjViaTavily(nome, cidade, estado);
                } catch (Exception e) {
                    log.debug("Tavily API: não localizou CNPJ dinamicamente para '{}': {}", nome, e.getMessage());
                }
            }

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
                        if (cidadeCadastral != null && !cidadeCadastral.isBlank()) {
                            String cidNorm = CidadeHelper.normalizarCidade(cidadeCadastral);
                            if (cidNorm == null) {
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
                boolean temCoords = (!Double.isNaN(lat) && !Double.isNaN(lon) && (lat != 0.0 || lon != 0.0) && CidadeHelper.isCoordenadaValidaParaCidade(lat, lon, cidade));
                if (!temCoords) {
                    // Tenta buscar localização via Tavily caso Overpass não encontre coordenadas válidas na cidade
                    double[] coordsTavily = buscarCoordenadasViaTavily(nome, enderecoLimpo, cidade);
                    if (coordsTavily != null) {
                        lat = coordsTavily[0];
                        lon = coordsTavily[1];
                        temCoords = true;
                    }
                }

                if (temCoords) {
                    meta.put("latitude", String.valueOf(lat));
                    meta.put("longitude", String.valueOf(lon));
                    meta.put("localizacaoConfirmada", "true");
                    meta.put("tipoLocalizacao", "EXATA");
                } else {
                    meta.put("localizacaoConfirmada", "false");
                    meta.put("tipoLocalizacao", "APROXIMADA");
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

    // Consultas cadastrais de CNPJ otimizadas (BrasilAPI, CNPJá, Minha Receita com cache)
    public Map<String, String> consultarDadosCadastraisCompletos(String cnpj) {
        if (cnpj == null) return null;
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        if (cnpjLimpo.length() != 14) return null;

        Map<String, String> emCache = CNPJ_CACHE.get(cnpjLimpo);
        if (emCache != null) {
            return emCache;
        }

        Map<String, String> dadosConsolidados = new LinkedHashMap<>();

        // 1. Tentar BrasilAPI primeiro (resposta rápida e completa)
        try {
            HttpRequest reqBrasilApi = HttpRequest.newBuilder()
                    .uri(URI.create(BRASIL_API_CNPJ + cnpjLimpo))
                    .timeout(Duration.ofMillis(2000))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respBrasilApi = httpClient.send(reqBrasilApi, HttpResponse.BodyHandlers.ofString());
            if (respBrasilApi.statusCode() == 200 && respBrasilApi.body() != null && respBrasilApi.body().trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(respBrasilApi.body());
                Map<String, String> dados = extrairDadosBrasilApiJson(root);
                if (dados != null && !dados.isEmpty()) {
                    dadosConsolidados.putAll(dados);
                }
            }
        } catch (Exception ignored) {}

        // 2. Se BrasilAPI não retornou razaoSocial, tentar CNPJá como fallback rápido
        if (!dadosConsolidados.containsKey("razaoSocial")) {
            try {
                HttpRequest reqCnpja = HttpRequest.newBuilder()
                        .uri(URI.create(CNPJA_OPEN_API + cnpjLimpo))
                        .timeout(Duration.ofMillis(2000))
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respCnpja = httpClient.send(reqCnpja, HttpResponse.BodyHandlers.ofString());
                if (respCnpja.statusCode() == 200 && respCnpja.body() != null && respCnpja.body().trim().startsWith("{")) {
                    JsonNode root = objectMapper.readTree(respCnpja.body());
                    Map<String, String> dadosCnpja = extrairDadosCnpjaJson(root);
                    if (dadosCnpja != null && !dadosCnpja.isEmpty()) {
                        dadosConsolidados.putAll(dadosCnpja);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Se ainda faltar razaoSocial, tentar Minha Receita como último fallback
        if (!dadosConsolidados.containsKey("razaoSocial")) {
            try {
                HttpRequest reqMinhaReceita = HttpRequest.newBuilder()
                        .uri(URI.create(MINHA_RECEITA_API + cnpjLimpo))
                        .timeout(Duration.ofMillis(2000))
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respMinhaReceita = httpClient.send(reqMinhaReceita, HttpResponse.BodyHandlers.ofString());
                if (respMinhaReceita.statusCode() == 200 && respMinhaReceita.body() != null && respMinhaReceita.body().trim().startsWith("{")) {
                    JsonNode root = objectMapper.readTree(respMinhaReceita.body());
                    Map<String, String> dadosMinhaReceita = extrairDadosBrasilApiJson(root);
                    if (dadosMinhaReceita != null && !dadosMinhaReceita.isEmpty()) {
                        dadosConsolidados.putAll(dadosMinhaReceita);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!dadosConsolidados.isEmpty()) {
            if (CNPJ_CACHE.size() > 1000) CNPJ_CACHE.clear();
            CNPJ_CACHE.put(cnpjLimpo, dadosConsolidados);
            return dadosConsolidados;
        }

        return null;
    }

    private Map<String, String> extrairDadosCnpjaJson(JsonNode root) {
        Map<String, String> dados = new LinkedHashMap<>();

        String razao = root.path("company").path("name").asText(null);
        if (razao != null && !razao.isBlank()) {
            dados.put("razaoSocial", razao.trim());
        }

        String alias = root.path("alias").asText(null);
        if (alias != null && !alias.isBlank()) {
            dados.put("nomeFantasia", alias.trim());
        }

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

    // Integração Wikidata
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

        JsonNode p856 = claims.path("P856");
        if (p856.isArray() && p856.size() > 0) {
            String site = p856.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (site != null && !site.isBlank()) map.put("website", site);
        }

        JsonNode p1329 = claims.path("P1329");
        if (p1329.isArray() && p1329.size() > 0) {
            String tel = p1329.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (tel != null && !tel.isBlank()) map.put("telefone", tel);
        }

        JsonNode p968 = claims.path("P968");
        if (p968.isArray() && p968.size() > 0) {
            String email = p968.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (email != null && !email.isBlank()) map.put("email", email);
        }

        JsonNode p3644 = claims.path("P3644");
        if (p3644.isArray() && p3644.size() > 0) {
            String cnpj = p3644.get(0).path("mainsnak").path("datavalue").path("value").asText(null);
            if (cnpj != null && !cnpj.isBlank()) map.put("cnpj", cnpj.replaceAll("\\D", ""));
        }

        return map;
    }

    // Busca de CNPJ via Tavily Search API
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

    // Validador de CNPJ (Módulo 11)
    public static boolean validarCnpj(String cnpj) {
        if (cnpj == null) return false;
        String limpo = cnpj.replaceAll("\\D", "");
        if (limpo.length() != 14) return false;

        if (limpo.matches("(\\d)\\1{13}")) return false;

        try {
            int soma = 0;
            int peso = 5;
            for (int i = 0; i < 12; i++) {
                soma += (limpo.charAt(i) - '0') * peso--;
                if (peso < 2) peso = 9;
            }
            int r = soma % 11;
            char dig1 = (r < 2) ? '0' : (char) ((11 - r) + '0');
            if (limpo.charAt(12) != dig1) return false;

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

    // Extração e tratamento de dados das tags OSM
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

    // Resolução de dados de contato
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

    private String extrairEnderecoLimpo(JsonNode tags, String nome, double lat, double lon) {
        String rua = tags.path("addr:street").asText("").trim();
        String numero = tags.path("addr:housenumber").asText("").trim();
        String km = tags.path("addr:km").asText(tags.path("km").asText("")).trim();

        if (!rua.isEmpty()) {
            if (!numero.isEmpty()) {
                return rua + ", " + numero;
            } else if (!km.isEmpty()) {
                return rua + ", Km " + km;
            }
            return rua;
        }

        String place = tags.path("addr:place").asText("").trim();
        if (!place.isEmpty()) {
            return !numero.isEmpty() ? place + ", " + numero : place;
        }

        String refRodovia = tags.path("ref").asText("").trim();
        if (refRodovia.toUpperCase().startsWith("BR-") || refRodovia.toUpperCase().startsWith("PR-")) {
            String prefixo = "Rodovia " + refRodovia;
            if (!km.isEmpty()) return prefixo + ", Km " + km;
            if (!numero.isEmpty()) return prefixo + ", " + numero;
            return prefixo;
        }

        String nomeLow = (nome != null ? nome : "").toLowerCase();
        String amenity = tags.path("amenity").asText("").toLowerCase();
        boolean isPosto = amenity.equals("fuel") || nomeLow.contains("posto ") || nomeLow.startsWith("posto") || nomeLow.contains("combustivel");

        if (nomeLow.contains("br-277") || nomeLow.contains("br 277") || tags.path("ref").asText("").contains("277")) {
            return !km.isEmpty() ? "Rodovia BR-277, Km " + km : "Rodovia BR-277";
        }

        if (isPosto && !Double.isNaN(lat) && !Double.isNaN(lon)) {
            if (lon > -52.48 && lon < -52.00 && lat > -25.65 && lat < -25.32) {
                return "Rodovia BR-277";
            }
        }

        String bairro = tags.path("addr:suburb").asText(tags.path("addr:district").asText("")).trim();
        if (!bairro.isEmpty()) {
            return bairro;
        }

        return "";
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

    // Prospecção direcionada via Tavily para cidades específicas
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
            String q = String.format("site:cnpj.biz OR site:casadosdados.com.br \"%s\" PR (comercio OR loja OR mercado OR restaurante OR farmacia OR oficina OR posto OR servicos OR padaria OR auto)", cidadeCanonica);

            Set<String> cnpjsEncontrados = new java.util.LinkedHashSet<>();
            Map<String, String> nomesPorCnpj = new LinkedHashMap<>();

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
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && resp.body().trim().startsWith("{")) {
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
                log.warn("Erro ao executar busca Tavily para '{}': {}", cidadeCanonica, e.getMessage());
            }

            log.info("Tavily localizou {} CNPJs únicos para {}", cnpjsEncontrados.size(), cidadeCanonica);

            int limite = 8;
            int processados = 0;

            for (String cnpj : cnpjsEncontrados) {
                if (processados >= limite) break;

                try {
                    String nomePreliminar = nomesPorCnpj.get(cnpj);
                    if (nomePreliminar != null) {
                        String nl = nomePreliminar.toLowerCase();
                        if (nl.contains("podemos") || nl.contains("partido") || nl.contains("diretorio") ||
                            nl.contains("municipio de") || nl.contains("prefeitura") || nl.contains("camara municipal") ||
                            nl.contains("lista de empresas") || nl.contains("maiores empresas") ||
                            nl.contains("usina solar") || nl.contains("locacao de usina") || nl.contains("holding") ||
                            nl.contains("administracao de bens") || nl.contains("participacoes ltda")) {
                            continue;
                        }
                    }

                    Map<String, String> dadosOficiais = null;
                    try {
                        dadosOficiais = consultarDadosCadastraisCompletos(cnpj);
                    } catch (Exception ex) {
                        log.debug("Consulta cadastral para CNPJ {} falhou: {}", cnpj, ex.getMessage());
                    }

                    String cidadeCadastral = dadosOficiais != null ? dadosOficiais.get("cidade") : null;
                    String cidadeNormalizada = CidadeHelper.normalizarCidade(cidadeCadastral != null ? cidadeCadastral : cidadeCanonica);

                    if (cidadeNormalizada == null) {
                        log.info("Descartando CNPJ {} de {}: cidade cadastral '{}' na Receita não é uma cidade citada", cnpj, cidadeCanonica, cidadeCadastral);
                        continue;
                    }

                    String razaoSocial = dadosOficiais != null ? dadosOficiais.get("razaoSocial") : null;
                    String nomeFantasia = dadosOficiais != null ? dadosOficiais.get("nomeFantasia") : null;
                    String nome = (nomeFantasia != null && !nomeFantasia.isBlank()) ? nomeFantasia : razaoSocial;

                    if (nome == null || nome.isBlank()) {
                        nome = nomePreliminar;
                    }
                    if (nome == null || nome.isBlank()) {
                        continue;
                    }

                    nome = limparNomePreliminar(nome, cnpj);

                    String nomeCheck = nome.toLowerCase();
                    if (nomeCheck.contains("podemos") || nomeCheck.contains("partido") || nomeCheck.contains("diretorio") ||
                        nomeCheck.contains("municipio de") || nomeCheck.contains("prefeitura") || nomeCheck.contains("camara municipal") ||
                        nomeCheck.contains("usina solar") || nomeCheck.contains("locacao de usina") || nomeCheck.contains("holding") ||
                        nomeCheck.contains("administracao de bens") || nomeCheck.contains("participacoes ltda")) {
                        continue;
                    }

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

                    String endereco = (dadosOficiais != null && dadosOficiais.get("endereco") != null) ? dadosOficiais.get("endereco").trim() : "";
                    if (endereco.equalsIgnoreCase("Centro")) {
                        endereco = "";
                    }
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
                    double[] coordsTavily = buscarCoordenadasViaTavily(nome, endereco, cidadeFinal);
                    boolean temCoords = (coordsTavily != null);
                    if (temCoords) {
                        meta.put("latitude", String.valueOf(coordsTavily[0]));
                        meta.put("longitude", String.valueOf(coordsTavily[1]));
                        meta.put("localizacaoConfirmada", "true");
                        meta.put("tipoLocalizacao", "EXATA");
                    } else {
                        meta.put("localizacaoConfirmada", "false");
                        meta.put("tipoLocalizacao", "APROXIMADA");
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
                    processados++;
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

    public double[] buscarCoordenadasViaTavily(String nome, String endereco, String cidade) {
        if (tavilyApiKey == null || tavilyApiKey.isBlank() || nome == null || cidade == null) {
            return null;
        }
        try {
            String q = String.format("\"%s\" \"%s\" PR (maps OR coordenadas OR localizacao)", nome, cidade);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("api_key", tavilyApiKey);
            payload.put("query", q);
            payload.put("search_depth", "basic");
            payload.put("max_results", 3);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_API_URL))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                if (results.isArray()) {
                    Pattern coordPattern = Pattern.compile("(-25\\.\\d{3,})[\\s,/]+(-5[12]\\.\\d{3,})");
                    for (JsonNode res : results) {
                        String texto = res.path("url").asText("") + " " + res.path("content").asText("") + " " + res.path("title").asText("");
                        Matcher m = coordPattern.matcher(texto);
                        if (m.find()) {
                            double lat = Double.parseDouble(m.group(1));
                            double lon = Double.parseDouble(m.group(2));
                            if (CidadeHelper.isCoordenadaValidaParaCidade(lat, lon, cidade)) {
                                log.info("Tavily localizou coordenadas confirmadas para '{}' em {}: {}, {}", nome, cidade, lat, lon);
                                return new double[]{lat, lon};
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Tavily busca de coordenadas para '{}' em {}: {}", nome, cidade, e.getMessage());
        }
        return null;
    }

    private String limparNomePreliminar(String raw, String cnpj) {
        if (raw == null) return "";
        String nome = raw.trim();
        nome = nome.replaceAll("(?i)\\s*-\\s*\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}.*", "");
        nome = nome.replaceAll("(?i)\\s*-\\s*\\d{14}.*", "");
        nome = nome.replaceAll("(?i)\\s*\\d{14}.*", "");
        nome = nome.replaceAll("(?i)\\s*\\|.*", "");
        nome = nome.replaceAll("(?i)\\s*-\\s*(Econodata|CNPJ Biz|Casa dos Dados).*", "");
        nome = nome.replaceAll("(?i)\\s+em\\s+(Laranjeiras do Sul|Rio Bonito do Iguaçu|Porto Barreiro|Virmond|Cantagalo|Nova Laranjeiras).*$", "");
        nome = nome.replaceAll("(?i)\\s*-\\s*(Laranjeiras do Sul|Rio Bonito do Iguaçu|Porto Barreiro|Virmond|Cantagalo|Nova Laranjeiras).*$", "");
        nome = nome.replaceAll("(?i)\\s*,\\s*PR.*$", "");
        nome = nome.replaceAll("(?i)\\s*-\\s*PR.*$", "");
        nome = nome.replaceAll("(?i)\\s*/\\s*PR.*$", "");
        nome = nome.replaceAll("^\\d{2}\\.\\d{3}\\.\\d{3}\\s*", "");
        nome = nome.replaceAll("^\\d{14}\\s*", "");
        return nome.trim();
    }
}
