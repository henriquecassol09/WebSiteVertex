package com.empresa.backend;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.Locale;

public final class CategoriaDetector {

    private CategoriaDetector() {}

    /**
     * Identifica a categoria/segmento específico combinando tags do OSM e o nome do estabelecimento.
     */
    public static String detectar(JsonNode tags, String nome) {
        // 1. Tenta tags especializadas do OSM
        if (tags != null) {
            String cuisine = tags.path("cuisine").asText("").toLowerCase().trim();
            if (cuisine.contains("pizza")) return "Pizzaria";
            if (cuisine.contains("burger") || cuisine.contains("hamburguer")) return "Hamburgueria";
            if (cuisine.contains("ice_cream") || cuisine.contains("sorvete")) return "Sorveteria";
            if (cuisine.contains("sandwich") || cuisine.contains("pastel")) return "Lanchonete";

            String amenity = tags.path("amenity").asText("").toLowerCase().trim();
            String shop = tags.path("shop").asText("").toLowerCase().trim();
            String craft = tags.path("craft").asText("").toLowerCase().trim();
            String healthcare = tags.path("healthcare").asText("").toLowerCase().trim();
            String office = tags.path("office").asText("").toLowerCase().trim();
            String tourism = tags.path("tourism").asText("").toLowerCase().trim();
            String leisure = tags.path("leisure").asText("").toLowerCase().trim();

            String categoriaTag = mapearTagEspecifica(amenity, shop, craft, healthcare, office, tourism, leisure);
            if (categoriaTag != null && !categoriaTag.equals("Comércio Local")) {
                // Se a tag for muito genérica como 'restaurant' e o nome tiver 'pizza', refina
                String refinadaPeloNome = refinarPeloNome(nome);
                if (refinadaPeloNome != null) {
                    return refinadaPeloNome;
                }
                return categoriaTag;
            }
        }

        // 2. Se as tags forem genéricas ou ausentes, infere pelo nome da empresa
        String categoriaNome = refinarPeloNome(nome);
        if (categoriaNome != null) {
            return categoriaNome;
        }

        return "Comércio e Serviços";
    }

    /**
     * Refina a categoria a partir do nome ou categoria existente genérica.
     */
    public static String refinar(String categoriaExistente, String nome) {
        if (categoriaExistente != null &&
            !categoriaExistente.isBlank() &&
            !categoriaExistente.equalsIgnoreCase("Comércio Local") &&
            !categoriaExistente.equalsIgnoreCase("Não informado")) {
            return categoriaExistente;
        }

        String identificada = refinarPeloNome(nome);
        return identificada != null ? identificada : (categoriaExistente != null && !categoriaExistente.isBlank() ? categoriaExistente : "Comércio e Serviços");
    }

    private static String mapearTagEspecifica(String amenity, String shop, String craft, String healthcare, String office, String tourism, String leisure) {
        // Amenity
        if (amenity.equals("pharmacy") || healthcare.equals("pharmacy") || shop.equals("chemist")) return "Farmácia";
        if (amenity.equals("veterinary")) return "Clínica Veterinária";
        if (amenity.equals("dentist") || healthcare.equals("dentist")) return "Consultório Odontológico";
        if (amenity.equals("clinic") || healthcare.equals("clinic")) return "Clínica Médica";
        if (amenity.equals("hospital")) return "Hospital";
        if (amenity.equals("fuel")) return "Posto de Combustíveis";
        if (amenity.equals("cafe")) return "Cafeteria";
        if (amenity.equals("bar") || amenity.equals("pub")) return "Bar e Choperia";
        if (amenity.equals("fast_food")) return "Lanchonete / Fast Food";
        if (amenity.equals("restaurant")) return "Restaurante";
        if (amenity.equals("ice_cream")) return "Sorveteria";
        if (amenity.equals("car_wash")) return "Lava Jato";

        // Shop
        if (shop.equals("clothes") || shop.equals("clothing") || shop.equals("boutique") || shop.equals("fashion") || shop.equals("apparel")) return "Loja de Roupas";
        if (shop.equals("shoes")) return "Loja de Calçados";
        if (shop.equals("supermarket")) return "Supermercado";
        if (shop.equals("convenience")) return "Loja de Conveniência";
        if (shop.equals("grocery")) return "Mercearia / Mercado";
        if (shop.equals("bakery")) return "Panificadora / Padaria";
        if (shop.equals("butcher")) return "Açougue / Casa de Carnes";
        if (shop.equals("pet")) return "Pet Shop";
        if (shop.equals("hairdresser")) return "Salão de Beleza / Cabeleireiro";
        if (shop.equals("beauty") || shop.equals("cosmetics")) return "Estética e Cosméticos";
        if (shop.equals("car_repair")) return "Oficina Mecânica";
        if (shop.equals("car_parts")) return "Loja de Autopeças";
        if (shop.equals("motorcycle")) return "Oficina / Loja de Motos";
        if (shop.equals("tyres")) return "Borracharia e Pneus";
        if (shop.equals("hardware") || shop.equals("building_materials")) return "Materiais de Construção";
        if (shop.equals("paint")) return "Loja de Tintas";
        if (shop.equals("furniture")) return "Loja de Móveis";
        if (shop.equals("electronics") || shop.equals("computer")) return "Eletrônicos e Informática";
        if (shop.equals("mobile_phone")) return "Loja de Celulares e Acessórios";
        if (shop.equals("optician")) return "Óptica";
        if (shop.equals("jewelry") || shop.equals("jewellery")) return "Joalheria e Relojoaria";
        if (shop.equals("stationery") || shop.equals("books")) return "Papelaria e Livraria";
        if (shop.equals("florist")) return "Floricultura";
        if (shop.equals("bicycle")) return "Bicicletaria";
        if (shop.equals("agrarian") || shop.equals("agricultural") || shop.equals("feed")) return "Agropecuária";
        if (shop.equals("gift") || shop.equals("variety_store")) return "Loja de Presentes e Variedades";
        if (shop.equals("toys")) return "Loja de Brinquedos";
        if (shop.equals("gas")) return "Distribuidora de Gás e Água";

        // Craft
        if (craft.equals("car_repair")) return "Oficina Mecânica";
        if (craft.equals("carpenter")) return "Marcenaria";
        if (craft.equals("glaziery")) return "Vidraçaria";
        if (craft.equals("blacksmith") || craft.equals("metal_construction")) return "Serralheria";

        // Tourism
        if (tourism.equals("hotel")) return "Hotelaria";
        if (tourism.equals("guest_house") || tourism.equals("hostel")) return "Pousada";

        // Leisure
        if (leisure.equals("fitness_centre")) return "Academia";

        // Office
        if (office.equals("estate_agent")) return "Imobiliária";
        if (office.equals("lawyer")) return "Escritório de Advocacia";
        if (office.equals("accountant")) return "Escritório de Contabilidade";

        return null;
    }

    public static String refinarPeloNome(String nome) {
        if (nome == null || nome.isBlank()) return null;

        String n = normalizar(nome);

        // Pizzaria e Massas
        if (n.contains("pizza") || n.contains("pizzaria") || n.contains("forneria")) return "Pizzaria";

        // Farmácias e Medicamentos
        if (n.contains("farma") || n.contains("droga") || n.contains("medic") || n.contains("manipulacao")) return "Farmácia";

        // Roupas, Moda e Confecções
        if (n.contains("calcado") || n.contains("sapat") || n.contains("tenis")) return "Loja de Calçados";
        if (n.contains("roupa") || n.contains("moda") || n.contains("confec") || n.contains("boutique") ||
            n.contains("vestu") || n.contains("looks") || n.contains("estilo") || n.contains("fashion") ||
            n.contains("jeans") || n.contains("lingerie") || n.contains("malhas") || n.contains("intim") ||
            n.contains("camis") || n.contains("trajes")) return "Loja de Roupas";

        // Alimentação: Padaria, Confeitaria, Açougue
        if (n.contains("padaria") || n.contains("panific") || n.contains("pao") || n.contains("confeit") || n.contains("bolos") || n.contains("tortas")) return "Panificadora / Confeitaria";
        if (n.contains("acougue") || n.contains("carnes") || n.contains("frigorifico") || n.contains("bovino")) return "Açougue / Casa de Carnes";
        if (n.contains("sorvet") || n.contains("gelato") || n.contains("acai")) return "Sorveteria / Açaiteria";
        if (n.contains("lanche") || n.contains("burger") || n.contains("hamburg") || n.contains("pastel") || n.contains("dog") || n.contains("hotdog")) return "Lanchonete / Hamburgueria";
        if (n.contains("restaurante") || n.contains("churrasc") || n.contains("gourmet") || n.contains("buffet") || n.contains("grill") || n.contains("comida")) return "Restaurante";
        if (n.contains("cafe") || n.contains("bistro") || n.contains("doceria")) return "Cafeteria / Bistrô";
        if (n.contains("bar ") || n.contains("bar.") || n.contains("pub") || n.contains("choperia") || n.contains("boteco") || n.contains("cerveja") || n.contains("bebidas")) return "Bar / Choperia";

        // Mercados e Mercearias
        if (n.contains("mercado") || n.contains("supermercado") || n.contains("mercearia") || n.contains("armazem") || n.contains("emporio") || n.contains("hiper")) return "Supermercado / Mercado";

        // Automotivo: Oficinas, Peças, Pneus, Postos
        if (n.contains("auto peca") || n.contains("autope") || n.contains("pecas auto")) return "Loja de Autopeças";
        if (n.contains("mecanic") || n.contains("oficina") || n.contains("auto eletric") || n.contains("funilaria") || n.contains("pintura automotiva") || n.contains("auto center") || n.contains("car service")) return "Oficina Mecânica";
        if (n.contains("pneu") || n.contains("borracharia") || n.contains("alinhamento")) return "Borracharia e Pneus";
        if (n.contains("lavacar") || n.contains("lava jato") || n.contains("estetica auto")) return "Lava Jato";
        if (n.contains("posto ") || n.contains("combustiv") || n.contains("abastec")) return "Posto de Combustíveis";
        if (n.contains("moto ") || n.contains("motos") || n.contains("motocicl")) return "Oficina / Loja de Motos";

        // Saúde Animal e Agro
        if (n.contains("veterin") || n.contains("vet ") || n.contains("vet.")) return "Clínica Veterinária";
        if (n.contains("pet") || n.contains("banho e tosa") || n.contains("racoes")) return "Pet Shop";
        if (n.contains("agro") || n.contains("agricol") || n.contains("sementes") || n.contains("adubos") || n.contains("rural")) return "Agropecuária";

        // Construção, Casa e Móveis
        if (n.contains("tinta")) return "Loja de Tintas";
        if (n.contains("construc") || n.contains("materiais") || n.contains("madeireira") || n.contains("ferrag")) return "Materiais de Construção";
        if (n.contains("moveis") || n.contains("estofad") || n.contains("colchao") || n.contains("colchoes") || n.contains("marcenaria") || n.contains("decorac")) return "Loja de Móveis e Decoração";
        if (n.contains("vidro") || n.contains("vidracaria") || n.contains("box")) return "Vidraçaria";
        if (n.contains("serralh")) return "Serralheria";

        // Tecnologia e Óptica
        if (n.contains("celular") || n.contains("smart") || n.contains("phone") || n.contains("acessorios")) return "Loja de Celulares e Acessórios";
        if (n.contains("informatica") || n.contains("computad") || n.contains("eletron") || n.contains("tech")) return "Eletrônicos e Informática";
        if (n.contains("otica") || n.contains("optica") || n.contains("oculos") || n.contains("relogio") || n.contains("joia") || n.contains("relojoaria") || n.contains("semijoias")) return "Óptica e Joalheria";

        // Beleza e Bem-Estar
        if (n.contains("barbearia") || n.contains("barber")) return "Barbearia";
        if (n.contains("salao") || n.contains("cabeleireir") || n.contains("hair") || n.contains("studio de beleza") || n.contains("coiffeur")) return "Salão de Beleza / Cabeleireiro";
        if (n.contains("estetica") || n.contains("cosmet") || n.contains("unhas") || n.contains("manicure") || n.contains("sobrancelh") || n.contains("depilac")) return "Estética e Cosméticos";
        if (n.contains("academia") || n.contains("fitness") || n.contains("crossfit") || n.contains("pilates") || n.contains("musculac") || n.contains("treino")) return "Academia";

        // Saúde Humana
        if (n.contains("dentist") || n.contains("odont") || n.contains("sorriso") || n.contains("oral")) return "Consultório Odontológico";
        if (n.contains("clinica") || n.contains("medica") || n.contains("laboratorio") || n.contains("exames") || n.contains("saude") || n.contains("terapia") || n.contains("fisioterap") || n.contains("psicol")) return "Clínica Médica / Saúde";

        // Outros
        if (n.contains("hotel") || n.contains("pousada") || n.contains("hospedag")) return "Hotelaria";
        if (n.contains("imobiliaria") || n.contains("imoveis") || n.contains("corretor")) return "Imobiliária";
        if (n.contains("advoc") || n.contains("advog") || n.contains("juridic")) return "Escritório de Advocacia";
        if (n.contains("contab") || n.contains("fiscal")) return "Escritório de Contabilidade";
        if (n.contains("papelaria") || n.contains("livraria") || n.contains("copiadora")) return "Papelaria e Livraria";
        if (n.contains("flor") || n.contains("plantas") || n.contains("paisag")) return "Floricultura";
        if (n.contains("bike") || n.contains("biciclet")) return "Bicicletaria";
        if (n.contains("brinqued")) return "Loja de Brinquedos";
        if (n.contains("bazar") || n.contains("variedad") || n.contains("presentes") || n.contains("utilidad")) return "Loja de Presentes e Variedades";
        if (n.contains("gas ") || n.contains("gas.") || n.contains("agua mineral")) return "Distribuidora de Gás e Água";

        return null;
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
