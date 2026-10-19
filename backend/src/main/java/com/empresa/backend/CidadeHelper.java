package com.empresa.backend;

import java.text.Normalizer;
import java.util.List;

/**
 * Utilitário centralizado para validação e normalização estrita das 6 cidades citadas da região.
 * Nenhuma cidade fora deste conjunto é permitida no módulo de prospecção.
 */
public final class CidadeHelper {

    private CidadeHelper() {}

    public record CidadeInfo(String nome, double lat, double lon, String ddd) {}

    public static final List<CidadeInfo> CIDADES_CITADAS = List.of(
            new CidadeInfo("Laranjeiras do Sul", -25.4086, -52.4161, "42"),
            new CidadeInfo("Rio Bonito do Iguaçu", -25.4931, -52.5342, "42"),
            new CidadeInfo("Porto Barreiro", -25.5469, -52.4078, "42"),
            new CidadeInfo("Virmond", -25.3817, -52.2003, "42"),
            new CidadeInfo("Cantagalo", -25.3744, -52.0089, "42"),
            new CidadeInfo("Nova Laranjeiras", -25.3056, -52.5408, "42")
    );

    // Raio máximo euclidiano em graus para associação por coordenadas geográficas (~12 a 13 km)
    public static final double RAIO_MAXIMO_GRAUS = 0.12;

    public static String removerAcentos(String str) {
        if (str == null) return "";
        return Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Verifica se o nome da cidade informado pertence ao rol oficial de cidades citadas.
     */
    public static boolean isCidadeCitada(String nomeCidade) {
        return normalizarCidade(nomeCidade) != null;
    }

    /**
     * Normaliza uma string de cidade para o nome canônico se pertencer a uma das 6 cidades citadas.
     * Retorna null se for de outra cidade (ex: Curitiba, Cascavel, Marquinho, Candói, etc.).
     */
    public static String normalizarCidade(String nomeCidade) {
        if (nomeCidade == null || nomeCidade.isBlank()) return null;
        String limpo = removerAcentos(nomeCidade).toLowerCase().trim();

        // 1. Correspondência exata normalizada primeiro
        for (CidadeInfo c : CIDADES_CITADAS) {
            String cNorm = removerAcentos(c.nome()).toLowerCase().trim();
            if (limpo.equals(cNorm)) {
                return c.nome();
            }
        }

        // 2. Termos específicos identificadores (sem risco de falso positivo)
        if (limpo.contains("porto barreiro")) return "Porto Barreiro";
        if (limpo.contains("nova laranjeiras")) return "Nova Laranjeiras";
        if (limpo.contains("laranjeiras do sul") || limpo.equals("laranjeiras")) return "Laranjeiras do Sul";
        if (limpo.contains("rio bonito")) return "Rio Bonito do Iguaçu";
        if (limpo.contains("cantagalo")) return "Cantagalo";
        if (limpo.contains("virmond")) return "Virmond";

        return null;
    }

    public static CidadeInfo buscarInfoPorNome(String nomeCidade) {
        String normalizado = normalizarCidade(nomeCidade);
        if (normalizado == null) return null;
        for (CidadeInfo c : CIDADES_CITADAS) {
            if (c.nome().equalsIgnoreCase(normalizado)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Identifica a cidade citada compatível com base no addr:city ou lat/lon.
     * Se addr:city estiver preenchido com OUTRA cidade fora da comarca, ou
     * se as coordenadas estiverem fora do raio máximo, retorna null.
     */
    public static CidadeInfo identificarCidade(double lat, double lon, String cidadeInformada) {
        if (cidadeInformada != null && !cidadeInformada.isBlank()) {
            CidadeInfo info = buscarInfoPorNome(cidadeInformada);
            // Se cidadeInformada veio com valor, mas não é uma das 6 cidades citadas,
            // rejeita estritamente (ex: estabelecimentos com tag addr:city de Candói, Marquinho, etc.)
            if (info == null) {
                return null;
            }
            return info;
        }

        // Se addr:city não foi informado na tag, calcula a proximidade geográfica com as 6 cidades
        CidadeInfo maisProxima = null;
        double menorDistancia = Double.MAX_VALUE;

        for (CidadeInfo c : CIDADES_CITADAS) {
            double d = Math.hypot(lat - c.lat(), lon - c.lon());
            if (d < menorDistancia) {
                menorDistancia = d;
                maisProxima = c;
            }
        }

        if (maisProxima != null && menorDistancia <= RAIO_MAXIMO_GRAUS) {
            return maisProxima;
        }

        return null;
    }
}
