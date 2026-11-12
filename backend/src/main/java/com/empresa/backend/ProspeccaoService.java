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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProspeccaoService {

    private final PesquisaRepository pesquisaRepository;
    private final EmpresaRepository empresaRepository;
    private final ObjectMapper objectMapper;
    private final TavilyApiKeyRotator tavilyApiKeyRotator;

    @org.springframework.beans.factory.annotation.Autowired
    public ProspeccaoService(
            PesquisaRepository pesquisaRepository,
            EmpresaRepository empresaRepository,
            ObjectMapper objectMapper,
            TavilyApiKeyRotator tavilyApiKeyRotator) {
        this.pesquisaRepository = pesquisaRepository;
        this.empresaRepository = empresaRepository;
        this.objectMapper = objectMapper;
        this.tavilyApiKeyRotator = tavilyApiKeyRotator != null ? tavilyApiKeyRotator : new TavilyApiKeyRotator();
    }

    public ProspeccaoService(
            PesquisaRepository pesquisaRepository,
            EmpresaRepository empresaRepository,
            ObjectMapper objectMapper) {
        this(pesquisaRepository, empresaRepository, objectMapper, new TavilyApiKeyRotator());
    }

    private static final String[] OVERPASS_SERVERS = {
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter"
    };

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
            "fountain", "police", "fire_station", "townhall", "school", "bus_station",
            "library", "charging_station", "courthouse", "place_of_worship", "graveyard",
            "cemetery", "public_building"
    );

    /**
     * Validador e filtro de estabelecimentos unificado para TODAS as APIs:
     * Descarta entidades públicas/governamentais, cartórios, colégios públicos,
     * postos policiais, igrejas/capelas mortuárias, partidos políticos e nomes inválidos.
     */
    public static boolean isEstabelecimentoInvalido(String nome, String tipo) {
        if (nome == null || nome.trim().length() < 2) {
            return true;
        }

        String nomeLimpo = nome.trim();
        String nomeLower = CidadeHelper.removerAcentos(nomeLimpo).toLowerCase(java.util.Locale.ROOT);

        // Nomes que começam com logradouros puros
        if (nomeLower.startsWith("rua ") || nomeLower.startsWith("avenida ") ||
            nomeLower.startsWith("rodovia ") || nomeLower.startsWith("alameda ") ||
            nomeLower.startsWith("linha ") || nomeLower.startsWith("travessa ") ||
            nomeLower.startsWith("estrada ") || nomeLower.startsWith("praca ")) {
            return true;
        }

        // Nomes que são apenas dígitos ou formato de CNPJ
        String apenasDigitos = nomeLimpo.replaceAll("\\D", "");
        if (apenasDigitos.length() >= 8 && nomeLimpo.replaceAll("[0-9./\\-\\s]", "").isEmpty()) {
            return true;
        }

        // Tipos ignorados do OSM
        if (tipo != null && !tipo.isBlank() && TIPOS_IGNORADOS.contains(tipo.toLowerCase(java.util.Locale.ROOT).trim())) {
            return true;
        }

        // Entidades públicas, governamentais, cartorárias, escolares públicas ou eleitorais
        if (nomeLower.contains("prefeitura") ||
            nomeLower.contains("camara municipal") ||
            nomeLower.contains("camara de vereadores") ||
            nomeLower.contains("secretaria municipal") ||
            nomeLower.contains("secretaria de ") ||
            nomeLower.contains("fundo municipal") ||
            nomeLower.contains("conselho tutelar") ||
            nomeLower.contains("municipio de ") ||
            nomeLower.contains("forum eleitoral") ||
            nomeLower.contains("tabelionato") ||
            nomeLower.contains("cartorio") ||
            nomeLower.contains("registro civil") ||
            nomeLower.contains("servico distrital") ||
            nomeLower.contains("escola municipal") ||
            nomeLower.contains("escola estadual") ||
            nomeLower.contains("colegio estadual") ||
            nomeLower.contains("creche municipal") ||
            nomeLower.contains("cemei") ||
            nomeLower.contains("policia militar") ||
            nomeLower.contains("policia civil") ||
            nomeLower.contains("corpo de bombeiros") ||
            nomeLower.contains("delegacia") ||
            nomeLower.contains("destacamento policial") ||
            nomeLower.contains("partido ") ||
            nomeLower.contains("diretorio municipal") ||
            nomeLower.contains("diretorio estadual") ||
            nomeLower.contains("comite financeiro") ||
            nomeLower.contains("eleicao ") ||
            nomeLower.contains("cemiterio") ||
            nomeLower.contains("capela mortuaria") ||
            nomeLower.contains("lista de empresas") ||
            nomeLower.contains("maiores empresas")) {
            return true;
        }

        return false;
    }

    /**
     * Salva ou enriquece as informações de uma busca no banco de dados.
     * Aplica os filtros universais para todas as APIs e assegura que nenhuma informação útil seja descartada.
     */
    public boolean salvarOuAtualizarPesquisa(
            String idUsuario,
            String nome,
            String endereco,
            String cidade,
            String estado,
            String categoria,
            String horario,
            String telefone,
            String email,
            String site,
            String cnpj,
            String razaoSocial,
            Double latitude,
            Double longitude,
            String tipoOrigem) {

        if (nome == null || nome.trim().isBlank()) {
            return false;
        }

        String nomeFinal = nome.trim();
        if (isEstabelecimentoInvalido(nomeFinal, null)) {
            return false;
        }

        if (!CidadeHelper.isCidadeCitada(cidade)) {
            return false;
        }

        String cidadeNormalizada = CidadeHelper.normalizarCidade(cidade);

        // Se a empresa já foi salva pelo usuário como cliente na aba Empresas, não recria em Buscas
        if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nomeFinal)) {
            return false;
        }

        try {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("endereco", endereco != null ? endereco.trim() : "");
            meta.put("cidade", cidadeNormalizada);
            meta.put("estado", estado != null ? estado : "PR");
            meta.put("categoria", categoria != null ? categoria : "Comércio e Serviços");
            meta.put("horario", horario != null ? horario : "Seg a Sex 08:00 - 18:00");
            meta.put("telefone", telefone != null ? telefone : "Não informado");
            meta.put("email", email != null ? email : "Não informado");
            meta.put("site", site != null ? site : "");
            if (cnpj != null && !cnpj.isBlank()) {
                meta.put("cnpj", formatarCnpjMascara(cnpj));
            }
            if (razaoSocial != null && !razaoSocial.isBlank()) {
                meta.put("razaoSocial", razaoSocial);
            }
            boolean temCoords = (latitude != null && longitude != null && !latitude.isNaN() && !longitude.isNaN() && (latitude != 0.0 || longitude != 0.0));
            if (temCoords) {
                meta.put("latitude", String.valueOf(latitude));
                meta.put("longitude", String.valueOf(longitude));
            }
            boolean temEndereco = (endereco != null && !endereco.isBlank() && !endereco.equalsIgnoreCase("Centro"));
            meta.put("localizacaoConfirmada", String.valueOf(temCoords || temEndereco));
            meta.put("tipoLocalizacao", (temCoords && temEndereco) ? "EXATA" : (temCoords ? "GPS" : (temEndereco ? "ENDERECO" : "APROXIMADA")));

            String regiaoJson = objectMapper.writeValueAsString(meta);

            // Se a busca já existe no banco, enriquece com as informações novas encontradas
            Optional<Pesquisa> existenteOpt = pesquisaRepository.findFirstByIdUsuarioAndConsultaIgnoreCase(idUsuario, nomeFinal);
            if (existenteOpt.isPresent()) {
                Pesquisa existente = existenteOpt.get();
                PesquisaResponse respAntiga = PesquisaResponse.from(existente);
                boolean precisaAtualizar = false;

                if ((respAntiga.cnpj() == null || respAntiga.cnpj().isBlank()) && (cnpj != null && !cnpj.isBlank())) {
                    precisaAtualizar = true;
                }
                if (("Não informado".equalsIgnoreCase(respAntiga.telefone()) || respAntiga.telefone() == null) &&
                    (telefone != null && !telefone.isBlank() && !"Não informado".equalsIgnoreCase(telefone))) {
                    precisaAtualizar = true;
                }
                if (("Não informado".equalsIgnoreCase(respAntiga.email()) || respAntiga.email() == null) &&
                    (email != null && !email.isBlank() && !"Não informado".equalsIgnoreCase(email))) {
                    precisaAtualizar = true;
                }
                if ((respAntiga.endereco() == null || respAntiga.endereco().isBlank()) && (endereco != null && !endereco.isBlank() && !endereco.equalsIgnoreCase("Centro"))) {
                    precisaAtualizar = true;
                }
                if (precisaAtualizar) {
                    existente.setRegiao(regiaoJson);
                    pesquisaRepository.save(existente);
                    log.info("Informações da busca enriquecidas e atualizadas no banco: {} ({})", nomeFinal, cidadeNormalizada);
                }
                return false;
            }

            Pesquisa nova = Pesquisa.builder()
                    .idUsuario(idUsuario)
                    .consulta(nomeFinal)
                    .regiao(regiaoJson)
                    .status("completed")
                    .build();

            pesquisaRepository.save(nova);
            log.info("Nova busca salva com sucesso no banco: {} ({}) [Origem: {}]", nomeFinal, cidadeNormalizada, tipoOrigem);
            return true;
        } catch (Exception e) {
            log.error("Erro ao salvar informações da busca para '{}': {}", nomeFinal, e.getMessage());
            return false;
        }
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ConcurrentHashMap<String, Boolean> PROSPECCOES_ATIVAS = new ConcurrentHashMap<>();

    // Constantes para controle estrito de tempo de busca (garantido entre 1 e 2 minutos)
    public static final long TEMPO_MINIMO_BUSCA_MS = 65_000L;    // 1m05s (garante >= 1 minuto)
    public static final long TEMPO_MAXIMO_BUSCA_MS = 105_000L;   // 1m45s (garante < 2 minutos com margem de segurança)
    public static final long DEADLINE_OVERPASS_MS = 40_000L;     // 40s max para etapa Overpass
    public static final long DEADLINE_NOVAS_CIDADES_MS = 85_000L;// 85s max para iniciar nova cidade Tavily
    public static final long DEADLINE_PROCESSAMENTO_MS = 100_000L;// 100s corte imediato de laços internos

    // Prospecção via Overpass (OpenStreetMap) e APIs Web Complementares
    public List<PesquisaResponse> prospectarEmpresasSemSite(String idUsuario) {
        if (idUsuario != null && PROSPECCOES_ATIVAS.putIfAbsent(idUsuario, Boolean.TRUE) != null) {
            throw new BusinessException("Ja existe uma prospeccao em andamento para a sua conta. Aguarde alguns instantes antes de iniciar outra.");
        }

        long inicioMs = System.currentTimeMillis();

        try {
            limparPesquisasInvalidas(idUsuario);

            String bbox = "-25.68,-52.60,-25.25,-51.95";

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
                if (System.currentTimeMillis() - inicioMs >= DEADLINE_OVERPASS_MS) {
                    break;
                }
                try {
                    log.info("Consultando Overpass API em: {}", server);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(server))
                            .timeout(Duration.ofSeconds(15))
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
                                processarElementos(elements, idUsuario, inicioMs);
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

            try {
                for (CidadeHelper.CidadeInfo cid : CidadeHelper.CIDADES_CITADAS) {
                    long decorrido = System.currentTimeMillis() - inicioMs;
                    if (decorrido >= DEADLINE_NOVAS_CIDADES_MS) {
                        log.info("Controle de tempo: limite para novas cidades atingido (decorrido: {}s). Encerrando prospecção de cidades.", decorrido / 1000);
                        break;
                    }
                    try {
                        int puxadas = prospectarCidadeEspecificaTavily(idUsuario, cid.nome(), "PR", inicioMs);
                        if (puxadas > 0) {
                            log.info("Prospecção direcionada Tavily de {} concluída: {} novos estabelecimentos incorporados.", cid.nome(), puxadas);
                        }
                    } catch (Exception e) {
                        log.warn("Falha na prospecção complementar Tavily de {}: {}", cid.nome(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Falha geral na prospecção complementar Tavily: {}", e.getMessage());
            }

            // Se ainda não alcançou o tempo mínimo de 1 minuto (TEMPO_MINIMO_BUSCA_MS = 65s),
            // aproveita o tempo restante para enriquecer pesquisas incompletas do usuário no banco
            long decorrido = System.currentTimeMillis() - inicioMs;
            if (decorrido < TEMPO_MINIMO_BUSCA_MS) {
                enriquecerPesquisasIncompletasComTempoRestante(idUsuario, inicioMs, TEMPO_MINIMO_BUSCA_MS);
            }

            // Sincronização inteligente para assegurar que a busca NUNCA termine antes de 1 minuto (60s)
            decorrido = System.currentTimeMillis() - inicioMs;
            if (decorrido < TEMPO_MINIMO_BUSCA_MS) {
                long esperaMs = TEMPO_MINIMO_BUSCA_MS - decorrido;
                log.info("Ajustando sincronização para garantir faixa de 1 a 2 minutos (aguardando {}s adicionais)...", esperaMs / 1000);
                try {
                    Thread.sleep(esperaMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            limparPesquisasInvalidas(idUsuario);

            long tempoTotalFinal = System.currentTimeMillis() - inicioMs;
            log.info("Prospecção regional finalizada em {} segundos (garantida rigorosamente entre 1 e 2 minutos).", tempoTotalFinal / 1000);

            return pesquisaRepository.findNaoSalvasByIdUsuario(idUsuario, PageRequest.of(0, 500))
                    .getContent()
                    .stream()
                    .map(PesquisaResponse::from)
                    .filter(r -> CidadeHelper.isCidadeCitada(r.cidade()) && !isEstabelecimentoInvalido(r.consulta(), r.categoria()))
                    .toList();
        } finally {
            if (idUsuario != null) {
                PROSPECCOES_ATIVAS.remove(idUsuario);
            }
        }
    }

    private void enriquecerPesquisasIncompletasComTempoRestante(String idUsuario, long inicioMs, long limiteMs) {
        if (idUsuario == null) return;
        try {
            List<Pesquisa> pesquisas = pesquisaRepository.findByIdUsuario(idUsuario, PageRequest.of(0, 50)).getContent();
            for (Pesquisa p : pesquisas) {
                if (System.currentTimeMillis() - inicioMs >= limiteMs) {
                    break;
                }
                PesquisaResponse resp = PesquisaResponse.from(p);
                boolean precisaTelefone = (resp.telefone() == null || resp.telefone().isBlank() || "Não informado".equalsIgnoreCase(resp.telefone()));
                boolean precisaEmail = (resp.email() == null || resp.email().isBlank() || "Não informado".equalsIgnoreCase(resp.email()));

                if ((precisaTelefone || precisaEmail) && resp.cnpj() != null && !resp.cnpj().isBlank()) {
                    String cnpjLimpo = resp.cnpj().replaceAll("\\D", "");
                    if (cnpjLimpo.length() == 14 && validarCnpj(cnpjLimpo)) {
                        try {
                            Map<String, String> dados = consultarDadosCadastraisCompletos(cnpjLimpo);
                            if (dados != null) {
                                String novoTel = dados.get("telefone");
                                String novoMail = dados.get("email");
                                String razao = dados.get("razaoSocial");
                                if ((novoTel != null && !novoTel.isBlank()) || (novoMail != null && !novoMail.isBlank())) {
                                    salvarOuAtualizarPesquisa(
                                            idUsuario,
                                            p.getConsulta(),
                                            resp.endereco(),
                                            resp.cidade(),
                                            resp.estado(),
                                            resp.categoria(),
                                            resp.horario(),
                                            novoTel,
                                            novoMail,
                                            resp.site(),
                                            resp.cnpj(),
                                            razao,
                                            resp.latitude(),
                                            resp.longitude(),
                                            "Enriquecimento Oportunista"
                                    );
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Aviso durante enriquecimento complementar: {}", e.getMessage());
        }
    }

    public void limparPesquisasInvalidas(String idUsuario) {
        if (idUsuario == null) return;
        try {
            List<Pesquisa> pesquisas = pesquisaRepository.findByIdUsuario(idUsuario, PageRequest.of(0, 500)).getContent();
            for (Pesquisa p : pesquisas) {
                PesquisaResponse resp = PesquisaResponse.from(p);
                boolean invalida = false;

                // 1. Cidade não citada na comarca
                if (!CidadeHelper.isCidadeCitada(resp.cidade())) {
                    invalida = true;
                }
                // 2. Consulta vazia ou menor que 2 caracteres
                if (p.getConsulta() == null || p.getConsulta().trim().length() < 2) {
                    invalida = true;
                }
                // 3. Entidades públicas, cartórios, delegacias ou escolas públicas
                if (isEstabelecimentoInvalido(p.getConsulta(), resp.categoria())) {
                    invalida = true;
                }

                if (invalida) {
                    pesquisaRepository.delete(p);
                    log.info("Pesquisa higienizada e removida do banco (filtro universal): '{}' (Cidade: {})",
                            p.getConsulta(), resp.cidade());
                }
            }
        } catch (Exception e) {
            log.warn("Aviso ao higienizar pesquisas com cidades não citadas: {}", e.getMessage());
        }
    }

    private void processarElementos(JsonNode elements, String idUsuario) {
        processarElementos(elements, idUsuario, System.currentTimeMillis());
    }

    private void processarElementos(JsonNode elements, String idUsuario, long inicioMs) {
        int processados = 0;

        for (JsonNode el : elements) {
            long decorrido = System.currentTimeMillis() - inicioMs;
            if (decorrido >= DEADLINE_OVERPASS_MS || decorrido >= DEADLINE_PROCESSAMENTO_MS) {
                log.info("Deadline da etapa Overpass atingido ({}s). Encerrando lote Overpass para prosseguir.", decorrido / 1000);
                break;
            }
            JsonNode tags = el.path("tags");
            if (!tags.hasNonNull("name")) continue;

            String nome = tags.path("name").asText().trim();
            String tipo = tags.path("shop").asText(tags.path("amenity").asText(tags.path("craft").asText("")));

            // Aplica filtro universal de estabelecimentos comerciais
            if (isEstabelecimentoInvalido(nome, tipo)) {
                continue;
            }

            if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nome)) {
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

            Double latVal = (!Double.isNaN(lat) && lat != 0.0) ? lat : null;
            Double lonVal = (!Double.isNaN(lon) && lon != 0.0) ? lon : null;

            boolean salvou = salvarOuAtualizarPesquisa(
                    idUsuario,
                    nome,
                    enderecoLimpo,
                    cidade,
                    estado,
                    categoria,
                    horario,
                    telefoneFinal,
                    emailFinal,
                    siteFinal,
                    cnpjFinal,
                    razaoSocialOficial,
                    latVal,
                    lonVal,
                    "OpenStreetMap/Overpass"
            );

            if (salvou) {
                processados++;
            }
        }

        log.info("Prospecção dinâmica concluída: {} estabelecimentos coletados e enriquecidos via APIs web.", processados);
    }

    // Consultas cadastrais de CNPJ (CNPJá, OpenCNPJ, BrasilAPI, Minha Receita)
    public Map<String, String> consultarDadosCadastraisCompletos(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        if (cnpjLimpo.length() != 14) return null;

        Map<String, String> dadosConsolidados = new LinkedHashMap<>();

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

    /**
     * Executa chamada HTTP à Tavily Search API com rotação automática de chave.
     * Se uma chave atingir limite de requisições ou cota (HTTP 429, 432, etc.),
     * rotaciona imediatamente para a próxima chave e repete a busca de forma transparente.
     */
    public JsonNode executarChamadaTavilyComRotacao(Map<String, Object> payloadBase, Duration timeout) {
        if (tavilyApiKeyRotator == null) {
            log.warn("TavilyApiKeyRotator não configurado.");
            return null;
        }

        int totalChaves = tavilyApiKeyRotator.getTotalChaves();
        if (totalChaves == 0) {
            log.warn("Nenhuma chave de API do Tavily disponível no pool.");
            return null;
        }

        for (int tentativa = 1; tentativa <= totalChaves; tentativa++) {
            String apiKey = tavilyApiKeyRotator.obterChaveAtual();
            String chaveMascarada = TavilyApiKeyRotator.mascararChave(apiKey);

            try {
                Map<String, Object> payload = new LinkedHashMap<>(payloadBase);
                payload.put("api_key", apiKey);

                String jsonPayload = objectMapper.writeValueAsString(payload);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(TAVILY_API_URL))
                        .timeout(timeout != null ? timeout : Duration.ofSeconds(6))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body();

                if (status == 200 && body != null) {
                    return objectMapper.readTree(body);
                }

                if (tavilyApiKeyRotator.isLimiteOuChaveInvalida(status, body)) {
                    log.warn("[Tavily] Limite atingido ou chave recusada ({}) [HTTP {}]. Alternando automaticamente para a próxima chave...",
                            chaveMascarada, status);
                    tavilyApiKeyRotator.rotacionarParaProxima(apiKey, "HTTP " + status + " - Limite/Cota");
                    continue; // Repete a busca com a próxima chave disponível
                }

                if (status >= 500) {
                    log.warn("[Tavily] Servidor retornou HTTP {} para a chave {}. Tentando chave alternativa...",
                            status, chaveMascarada);
                    tavilyApiKeyRotator.rotacionarParaProxima(apiKey, "HTTP " + status + " - Erro servidor Tavily");
                    continue;
                }

                log.warn("[Tavily] Retornou HTTP {} para a chave {}: {}", status, chaveMascarada, body);
                return null;
            } catch (Exception e) {
                log.warn("[Tavily] Falha de comunicação com chave {}: {}. Alternando para a próxima chave...",
                        chaveMascarada, e.getMessage());
                tavilyApiKeyRotator.rotacionarParaProxima(apiKey, "Exceção: " + e.getMessage());
            }
        }

        log.error("[Tavily] Todas as {} chaves configuradas falharam ou atingiram o limite de buscas.", totalChaves);
        return null;
    }

    // Busca de CNPJ via Tavily Search API com rotação automática de chave
    public String buscarCnpjViaTavily(String nome, String cidade, String estado) {
        if (tavilyApiKeyRotator == null || tavilyApiKeyRotator.getTotalChaves() == 0) return null;
        if (isEstabelecimentoInvalido(nome, null)) return null;
        if (cidade != null && !CidadeHelper.isCidadeCitada(cidade)) return null;

        try {
            String cidadeNormalizada = CidadeHelper.normalizarCidade(cidade);
            String query = String.format("%s %s %s CNPJ", nome, cidadeNormalizada != null ? cidadeNormalizada : cidade, estado != null ? estado : "PR");

            Map<String, Object> payload = new LinkedHashMap<>();
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

            JsonNode root = executarChamadaTavilyComRotacao(payload, Duration.ofMillis(4500));
            if (root != null) {
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

    // Prospecção direcionada via Tavily
    public int prospectarCidadeEspecificaTavily(String idUsuario, String cidade, String estado) {
        return prospectarCidadeEspecificaTavily(idUsuario, cidade, estado, System.currentTimeMillis());
    }

    public int prospectarCidadeEspecificaTavily(String idUsuario, String cidade, String estado, long inicioMs) {
        String cidadeCanonica = CidadeHelper.normalizarCidade(cidade);
        if (cidadeCanonica == null) {
            log.warn("Tentativa de prospecção para cidade não citada: '{}'. Operação abortada.", cidade);
            return 0;
        }

        if (tavilyApiKeyRotator == null || tavilyApiKeyRotator.getTotalChaves() == 0) {
            log.warn("Tavily API Keys ausentes; prospecção para {} não pôde ser executada.", cidadeCanonica);
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
                if (System.currentTimeMillis() - inicioMs >= DEADLINE_NOVAS_CIDADES_MS) {
                    log.info("Deadline de tempo para novas queries Tavily atingido ({}s). Encerrando queries de {}.",
                            (System.currentTimeMillis() - inicioMs) / 1000, cidadeCanonica);
                    break;
                }
                try {
                    Map<String, Object> payloadBase = new LinkedHashMap<>();
                    payloadBase.put("query", q);
                    payloadBase.put("search_depth", "basic");
                    payloadBase.put("max_results", 10);
                    payloadBase.put("include_domains", List.of(
                            "casadosdados.com.br",
                            "cnpj.biz",
                            "econodata.com.br",
                            "consultas.plus",
                            "infocnpj.com.br",
                            "situacaocadastral.info"
                    ));

                    JsonNode root = executarChamadaTavilyComRotacao(payloadBase, Duration.ofSeconds(6));
                    if (root != null) {
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
                if (System.currentTimeMillis() - inicioMs >= DEADLINE_PROCESSAMENTO_MS) {
                    log.info("Deadline de segurança próximo a 2 minutos (decorrido: {}s). Interrompendo coleta de CNPJs de {}.",
                            (System.currentTimeMillis() - inicioMs) / 1000, cidadeCanonica);
                    break;
                }
                try {
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
                        nome = nomesPorCnpj.get(cnpj);
                    }
                    if (nome == null || nome.isBlank()) {
                        continue;
                    }

                    nome = limparNomePreliminar(nome, cnpj);

                    // Se após a limpeza o nome preliminar ficou vazio mas temos a razão social, usa a razão social sem prefixo de CNPJ
                    if (nome.isBlank() && razaoSocial != null && !razaoSocial.isBlank()) {
                        nome = limparNomePreliminar(razaoSocial, cnpj);
                    }

                    // APLICA O FILTRO UNIVERSAL DE ESTABELECIMENTOS PARA A API TAVILY
                    if (isEstabelecimentoInvalido(nome, null)) {
                        log.debug("Descartando estabelecimento inválido/órgão público via Tavily: '{}'", nome);
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

                    boolean salvou = salvarOuAtualizarPesquisa(
                            idUsuario,
                            nome,
                            endereco,
                            cidadeFinal,
                            estadoFinal,
                            categoria,
                            "Seg a Sex 08:00 - 18:00",
                            telefone,
                            email,
                            site,
                            cnpjFormatado,
                            razaoSocial,
                            null,
                            null,
                            "Tavily Search"
                    );

                    if (salvou) {
                        novasEmpresas++;
                    }

                } catch (Exception ex) {
                    log.error("Erro ao processar e salvar empresa CNPJ {} de {}: {}", cnpj, cidadeCanonica, ex.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Erro geral na prospecção de {}: {}", cidadeCanonica, e.getMessage());
        }

        return novasEmpresas;
    }

    public static String limparNomePreliminar(String raw, String cnpj) {
        if (raw == null) return "";
        String nome = raw.trim();

        // 1. Remove prefixo numérico de CNPJ no início (ex: "05.059.448/0001-02 Empresa", "51.274.888 Empresa", "51274888000191 Empresa")
        nome = nome.replaceAll("^\\d{2}\\.?\\d{3}\\.?\\d{3}(?:/?\\d{4}-?\\d{2})?\\s*[-–:]?\\s*", "");
        nome = nome.replaceAll("^\\d{14}\\s*[-–:]?\\s*", "");
        nome = nome.replaceAll("^\\d{8}\\s*[-–:]?\\s*", "");

        // 2. Remove sufixo de CNPJ no final
        nome = nome.replaceAll("(?i)\\s*[-–:]?\\s*\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}.*$", "");
        nome = nome.replaceAll("(?i)\\s*[-–:]?\\s*\\d{14}.*$", "");

        // 3. Remove sufixos de portais da web no final (Casa dos Dados, Econodata, CNPJ Biz, etc.)
        nome = nome.replaceAll("(?i)\\s*\\|\\s*(Casa dos Dados|CNPJ Biz|Econodata|Consulta.*|Tavily.*)$", "");
        nome = nome.replaceAll("(?i)\\s*[-–]\\s*(Casa dos Dados|CNPJ Biz|Econodata|Consulta.*|Tavily.*)$", "");
        nome = nome.replaceAll("(?i)\\s*\\|.*$", "");

        // 4. Remove sufixos de localização no FINAL (ex: " - Porto Barreiro, PR" ou " em Porto Barreiro - PR")
        nome = nome.replaceAll("(?i)\\s+em\\s+(Laranjeiras do Sul|Rio Bonito do Iguaçu|Porto Barreiro|Virmond|Cantagalo|Nova Laranjeiras)\\s*[-–,]?\\s*(PR)?$", "");
        nome = nome.replaceAll("(?i)\\s*[-–]\\s*(Laranjeiras do Sul|Rio Bonito do Iguaçu|Porto Barreiro|Virmond|Cantagalo|Nova Laranjeiras)\\s*[-–,]?\\s*(PR)?$", "");
        nome = nome.replaceAll("(?i)\\s*[-–,]?\\s*PR$", "");

        return nome.trim();
    }
}
