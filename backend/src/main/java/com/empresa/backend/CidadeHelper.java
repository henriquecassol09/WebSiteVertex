package com.empresa.backend;

import java.text.Normalizer;
import java.util.List;

public final class CidadeHelper {

    private CidadeHelper() {}

    public record CidadeInfo(String nome, double lat, double lon, String ddd) {}

    // Exatamente as 6 cidades oficiais da comarca
    public static final List<CidadeInfo> CIDADES_CITADAS = List.of(
            new CidadeInfo("Laranjeiras do Sul", -25.4086, -52.4161, "42"),
            new CidadeInfo("Rio Bonito do Iguaçu", -25.4931, -52.5342, "42"),
            new CidadeInfo("Porto Barreiro", -25.5469, -52.4078, "42"),
            new CidadeInfo("Virmond", -25.3817, -52.2003, "42"),
            new CidadeInfo("Cantagalo", -25.3744, -52.0089, "42"),
            new CidadeInfo("Nova Laranjeiras", -25.3056, -52.5408, "42")
    );

    // Raio euclidiano estrito para manter a busca exclusivamente nas cidades cadastradas (~13 km)
    public static final double RAIO_MAXIMO_GRAUS = 0.12;

    public static String removerAcentos(String str) {
        if (str == null) return "";
        return Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public static boolean isCidadeCitada(String nomeCidade) {
        return normalizarCidade(nomeCidade) != null;
    }

    public static String normalizarCidade(String nomeCidade) {
        if (nomeCidade == null || nomeCidade.isBlank()) return null;

        String limpo = removerAcentos(nomeCidade).toLowerCase().trim();

        for (CidadeInfo c : CIDADES_CITADAS) {
            String cNorm = removerAcentos(c.nome()).toLowerCase().trim();
            if (limpo.equals(cNorm)) {
                return c.nome();
            }
        }

        if (limpo.contains("porto barreiro")) return "Porto Barreiro";
        if (limpo.contains("nova laranjeiras")) return "Nova Laranjeiras";
        if (limpo.contains("laranjeiras do sul") || limpo.equals("laranjeiras") || limpo.equals("lds")) return "Laranjeiras do Sul";
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

    public static CidadeInfo identificarCidade(double lat, double lon, String cidadeInformada) {
        if (cidadeInformada != null && !cidadeInformada.isBlank()) {
            CidadeInfo info = buscarInfoPorNome(cidadeInformada);
            if (info != null) {
                return info;
            }
            return null; // Rejeita qualquer cidade fora do cadastro oficial
        }

        if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0.0 && lon == 0.0)) {
            return null;
        }

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

    public static String formatarNomeCidade(String nome) {
        if (nome == null || nome.isBlank()) return "";
        String[] palavras = nome.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palavras) {
            if (p.isEmpty()) continue;
            if (p.equals("do") || p.equals("da") || p.equals("dos") || p.equals("das") || p.equals("de")) {
                sb.append(p).append(" ");
            } else {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
