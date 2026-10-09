package com.empresa.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoService {

    private final PesquisaRepository pesquisaRepository;
    private final EmpresaRepository empresaRepository;
    private final ObjectMapper objectMapper;

    private static final String[] OVERPASS_SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    };

    // Coordenadas das cidades solicitadas para aproximação geográfica
    private record CidadeCoordenada(String nome, double lat, double lon) {}

    private static final List<CidadeCoordenada> CIDADES = List.of(
            new CidadeCoordenada("Laranjeiras do Sul", -25.4086, -52.4161),
            new CidadeCoordenada("Rio Bonito do Iguaçu", -25.4931, -52.5342),
            new CidadeCoordenada("Virmond", -25.3817, -52.2003),
            new CidadeCoordenada("Cantagalo", -25.3744, -52.0089),
            new CidadeCoordenada("Nova Laranjeiras", -25.3056, -52.5408)
    );

    private static final Set<String> TIPOS_IGNORADOS = Set.of(
            "fountain", "police", "fire_station", "townhall", "school", "bus_station", "library", "charging_station"
    );

    @Transactional
    public List<PesquisaResponse> prospectarEmpresasSemSite(String idUsuario) {
        // Bounding Box delimitando Laranjeiras do Sul, Rio Bonito do Iguaçu, Virmond, Cantagalo e Nova Laranjeiras
        String bbox = "-25.55,-52.60,-25.25,-51.95";
        String query = """
                [out:json][timeout:25];
                (
                  node["shop"]["name"]["website"!~"."](%s);
                  node["amenity"]["name"]["website"!~"."](%s);
                  node["craft"]["name"]["website"!~"."](%s);
                  node["office"]["name"]["website"!~"."](%s);
                  node["healthcare"]["name"]["website"!~"."](%s);
                );
                out body 50;
                """.formatted(bbox, bbox, bbox, bbox, bbox);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        for (String server : OVERPASS_SERVERS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(server))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "VertexBackendProspector/1.0")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode elements = root.path("elements");

                    if (elements.isArray() && elements.size() > 0) {
                        for (JsonNode el : elements) {
                            JsonNode tags = el.path("tags");
                            if (!tags.hasNonNull("name")) continue;

                            String nome = tags.path("name").asText().trim();
                            if (nome.isEmpty() || nome.toLowerCase().startsWith("rua ") || nome.toLowerCase().startsWith("avenida ")) continue;

                            String tipo = tags.path("shop").asText(tags.path("amenity").asText(tags.path("craft").asText("")));
                            if (TIPOS_IGNORADOS.contains(tipo)) continue;

                            // Regra de negócio: se a empresa já estiver salva na tabela EMPRESAS, ela não deve aparecer em nenhuma busca
                            if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nome)) {
                                continue;
                            }

                            // Evita duplicar registros já salvos na tabela PESQUISAS
                            if (pesquisaRepository.existsByIdUsuarioAndConsultaIgnoreCase(idUsuario, nome)) {
                                continue;
                            }

                            double lat = el.path("lat").asDouble();
                            double lon = el.path("lon").asDouble();
                            String cidade = identificarCidade(lat, lon, tags.path("addr:city").asText(null));
                            String estado = "PR";

                            // Endereço limpo: apenas a rua e o número, SEM cidade e SEM estado
                            String enderecoLimpo = extrairEnderecoLimpo(tags);

                            // Atividade / Segmento específico (ex: Pizzaria, Farmácia, Loja de Roupas, etc.)
                            String categoria = CategoriaDetector.detectar(tags, nome);
                            // Horário de funcionamento
                            String horario = formatarHorario(tags.path("opening_hours").asText(null));

                            // Telefone se disponível
                            String telefone = tags.path("phone").asText(tags.path("contact:phone").asText("Não informado"));

                            // Serializa metadados estruturados no campo REGIAO da tabela PESQUISAS
                            Map<String, String> meta = new LinkedHashMap<>();
                            meta.put("endereco", enderecoLimpo);
                            meta.put("cidade", cidade);
                            meta.put("estado", estado);
                            meta.put("categoria", categoria);
                            meta.put("horario", horario);
                            meta.put("telefone", telefone);

                            String regiaoJson = objectMapper.writeValueAsString(meta);

                            Pesquisa p = Pesquisa.builder()
                                    .idUsuario(idUsuario)
                                    .consulta(nome)
                                    .regiao(regiaoJson)
                                    .status("completed")
                                    .build();

                            pesquisaRepository.save(p);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Consulta à Overpass API em {} falhou: {}", server, e.getMessage());
            }
        }

        // Retorna as buscas persistidas que não foram salvas na tabela de empresas
        return pesquisaRepository.findNaoSalvasByIdUsuario(idUsuario, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(PesquisaResponse::from)
                .toList();
    }

    private String extrairEnderecoLimpo(JsonNode tags) {
        String rua = tags.path("addr:street").asText(tags.path("addr:place").asText("Centro")).trim();
        String numero = tags.hasNonNull("addr:housenumber") ? "Nº " + tags.path("addr:housenumber").asText().trim() : "";

        if (rua.equalsIgnoreCase("Centro") || rua.isBlank()) {
            return numero.isEmpty() ? "Centro" : "Centro, " + numero;
        }
        return numero.isEmpty() ? rua : rua + ", " + numero;
    }
    private String formatarHorario(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Não informado";
        }

        String h = raw.trim();
        return h.replace("Mo-Fr", "Seg a Sex")
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

    private String identificarCidade(double lat, double lon, String cidadeInformada) {
        if (cidadeInformada != null && !cidadeInformada.isBlank()) {
            return cidadeInformada;
        }

        String maisProxima = CIDADES.get(0).nome();
        double menorDistancia = Double.MAX_VALUE;

        for (CidadeCoordenada c : CIDADES) {
            double d = Math.hypot(lat - c.lat(), lon - c.lon());
            if (d < menorDistancia) {
                menorDistancia = d;
                maisProxima = c.nome();
            }
        }

        return maisProxima;
    }
}
