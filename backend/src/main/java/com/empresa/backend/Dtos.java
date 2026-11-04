package com.empresa.backend;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// ===== Autenticacao =====

record RegistrarRequest(

    @NotBlank(message = "Nome e obrigatorio")
    @Size(min = 2, max = 150)
    String nome,

    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Email invalido")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Senha e obrigatoria")
    @SenhaForte
    String senha,

    Boolean aceitouTermos

) {}

record LoginRequest(
    @NotBlank @Email(message = "Email invalido") String email,
    @NotBlank(message = "Senha e obrigatoria") String senha
) {}

record RefreshRequest(
    @NotBlank(message = "Refresh token e obrigatorio") String refreshToken
) {}

record RegistrarResponse(
    boolean precisaVerificacao,
    String email,
    String mensagem
) {
    static RegistrarResponse of(boolean precisaVerificacao, String email, String mensagem) {
        return new RegistrarResponse(precisaVerificacao, email, mensagem);
    }
}

record VerificarCodigoRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email invalido") String email,
    @NotBlank(message = "Codigo e obrigatorio") @Size(min = 6, max = 6, message = "Codigo deve ter 6 digitos") String codigo
) {}

record ReenviarCodigoRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email invalido") String email
) {}

record SolicitarRecuperacaoRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email invalido") String email
) {}

record RedefinirSenhaRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email invalido") String email,
    @NotBlank(message = "Codigo e obrigatorio") @Size(min = 6, max = 6, message = "Codigo deve ter 6 digitos") String codigo,
    @NotBlank(message = "Nova senha e obrigatoria") @SenhaForte String novaSenha
) {}

record AlterarSenhaRequest(
    @NotBlank(message = "Senha atual e obrigatoria") String senhaAtual,
    @NotBlank(message = "Nova senha e obrigatoria") @SenhaForte String novaSenha
) {}

record MensagemResponse(String mensagem) {
    static MensagemResponse of(String mensagem) {
        return new MensagemResponse(mensagem);
    }
}

record CsrfTokenResponse(String csrfToken) {
    static CsrfTokenResponse of(String token) {
        return new CsrfTokenResponse(token);
    }
}

record AuthResponse(String accessToken, String refreshToken, long expiraEmSegundos, String tipo) {
    static AuthResponse of(String accessToken, String refreshToken, long expiraEmSegundos) {
        return new AuthResponse(accessToken, refreshToken, expiraEmSegundos, "Bearer");
    }
}

record UsuarioResponse(
    String idUsuario,
    String nome,
    String email,
    boolean emailVerificado,
    String imagem,
    Instant criadoEm,
    Instant atualizadoEm
) {
    static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(
            u.getIdUsuario(),
            u.getNome(),
            u.getEmail(),
            u.isEmailVerificado(),
            u.getImagem(),
            u.getCriadoEm(),
            u.getAtualizadoEm()
        );
    }
}

record AtualizarFotoRequest(
    @Size(max = 500, message = "URL da foto não pode exceder 500 caracteres")
    String fotoUrl
) {}

record DeletarContaRequest(
    String senha
) {}

// ===== Pesquisas =====

record PesquisaRequest(
    @NotBlank @Size(max = 500) String consulta,
    @Size(max = 150) String regiao
) {}

record PesquisaResponse(
    String idPesquisa,
    String consulta,
    String regiao,
    String status,
    Instant criadoEm,
    String endereco,
    String cidade,
    String estado,
    String categoria,
    String horario,
    String telefone,
    String email,
    String cnpj,
    String site,
    Double latitude,
    Double longitude,
    Boolean localizacaoConfirmada,
    String tipoLocalizacao
) {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    static PesquisaResponse from(Pesquisa p) {
        String raw = p.getRegiao();
        String endereco = "";
        String cidade = null;
        String estado = "PR";
        String categoria = "Comércio Local";
        String horario = "Não informado";
        String telefone = "Não informado";
        String emailEmpresa = "Não informado";
        String cnpj = null;
        String site = "";
        Double latitude = null;
        Double longitude = null;
        Boolean localizacaoConfirmada = null;
        String tipoLocalizacao = null;

        if (raw != null && raw.trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(raw);
                endereco = node.path("endereco").asText("").trim();
                cidade = node.path("cidade").asText(null);
                estado = node.path("estado").asText("PR");
                categoria = node.path("categoria").asText(categoria);
                horario = node.path("horario").asText(horario);
                telefone = node.path("telefone").asText(telefone);
                emailEmpresa = node.path("email").asText(emailEmpresa);
                cnpj = node.path("cnpj").asText(null);
                site = node.path("site").asText("");

                if (node.hasNonNull("latitude")) {
                    latitude = node.path("latitude").asDouble();
                } else if (node.hasNonNull("lat")) {
                    latitude = node.path("lat").asDouble();
                }
                if (node.hasNonNull("longitude")) {
                    longitude = node.path("longitude").asDouble();
                } else if (node.hasNonNull("lon")) {
                    longitude = node.path("lon").asDouble();
                }

                if (node.hasNonNull("localizacaoConfirmada")) {
                    localizacaoConfirmada = node.path("localizacaoConfirmada").asBoolean();
                }
                if (node.hasNonNull("tipoLocalizacao")) {
                    tipoLocalizacao = node.path("tipoLocalizacao").asText();
                }
            } catch (Exception ignored) {}
        } else if (raw != null && !raw.isBlank()) {
            if (raw.contains("(") && raw.contains(")")) {
                int open = raw.indexOf('(');
                int close = raw.indexOf(')', open);
                if (close > open) {
                    endereco = raw.substring(0, open).trim();
                    String cidEst = raw.substring(open + 1, close).trim();
                    String[] partes = cidEst.split("-");
                    if (partes.length > 0 && !partes[0].isBlank()) cidade = partes[0].trim();
                    if (partes.length > 1 && !partes[1].isBlank()) estado = partes[1].trim();
                }
            } else {
                endereco = raw.trim();
            }
        }

        // Identificação por menção na consulta, endereço ou texto bruto da região
        String textoBusca = ((p.getConsulta() != null ? p.getConsulta() : "") + " "
                + (endereco != null ? endereco : "") + " "
                + (raw != null ? raw : "")).toLowerCase();

        for (CidadeHelper.CidadeInfo c : CidadeHelper.CIDADES_CITADAS) {
            if (textoBusca.contains(c.nome().toLowerCase())) {
                cidade = c.nome();
                break;
            }
        }

        // Se for posto de combustíveis ou estabelecimento de rodovia e endereço estava como fictício "Centro":
        if ((textoBusca.contains("posto") || textoBusca.contains("shell") || textoBusca.contains("combustivel") || textoBusca.contains("br-277") || textoBusca.contains("rodovia"))
                && (endereco == null || endereco.isBlank() || endereco.equalsIgnoreCase("Centro"))) {
            if (latitude != null && longitude != null) {
                if (longitude > -52.45 && longitude < -52.00 && latitude > -25.65 && latitude < -25.32) {
                    endereco = "Rodovia BR-277";
                }
            } else if (textoBusca.contains("br-277") || textoBusca.contains("br 277")) {
                endereco = "Rodovia BR-277";
            }
        }

        // Removemos o "Centro" padrão fictício para não iludir o usuário quando o logradouro não for real
        if (endereco != null && endereco.equalsIgnoreCase("Centro")) {
            endereco = "";
        }

        // Normalização precisa da cidade
        String cidadeNormalizada = CidadeHelper.normalizarCidade(cidade);
        if (cidadeNormalizada != null) {
            cidade = cidadeNormalizada;
        } else if (cidade == null || cidade.isBlank()) {
            cidade = "Laranjeiras do Sul";
        }

        boolean temCoordenadas = (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0));
        boolean temEndereco = (endereco != null && !endereco.isBlank());

        if (localizacaoConfirmada == null) {
            localizacaoConfirmada = temCoordenadas || temEndereco;
        }
        if (tipoLocalizacao == null) {
            if (temCoordenadas && temEndereco) {
                tipoLocalizacao = "EXATA";
            } else if (temCoordenadas) {
                tipoLocalizacao = "GPS";
            } else if (temEndereco) {
                tipoLocalizacao = "ENDERECO";
            } else {
                tipoLocalizacao = "APROXIMADA";
            }
        }

        String regiaoFormatada = cidade + " - " + estado;
        String categoriaRefinada = CategoriaDetector.refinar(categoria, p.getConsulta());

        return new PesquisaResponse(
            p.getIdPesquisa(),
            p.getConsulta(),
            regiaoFormatada,
            p.getStatus(),
            p.getCriadoEm(),
            endereco,
            cidade,
            estado,
            categoriaRefinada,
            horario,
            telefone,
            emailEmpresa,
            cnpj,
            site,
            latitude,
            longitude,
            localizacaoConfirmada,
            tipoLocalizacao
        );
    }
}

// ===== Empresas =====

record EmpresaRequest(
    @NotBlank @Size(max = 255) String nome,
    @Size(max = 100) String categoria,
    @Size(max = 100) String cidade,
    @Size(max = 100) String estado,
    @Size(max = 255) String endereco,
    @Size(max = 30) String telefone,
    @Size(max = 255) String email,
    @Size(max = 255) String site,
    @Size(max = 4000) String descricao,
    @Size(max = 20) String cnpj,
    String idPesquisa,
    Double latitude,
    Double longitude
) {}

record EmpresaResponse(
    String idEmpresa, String nome, String categoria, String cidade, String estado,
    String endereco, String telefone, String email, String site, String descricao,
    String cnpj, Instant criadoEm, Instant atualizadoEm,
    Double latitude, Double longitude, Boolean localizacaoConfirmada, String tipoLocalizacao
) {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    static EmpresaResponse from(Empresa e) {
        String cat = CategoriaDetector.refinar(e.getCategoria(), e.getNome());
        String cidade = e.getCidade();
        String endereco = e.getEndereco() != null ? e.getEndereco().trim() : "";
        if (endereco.equalsIgnoreCase("Centro")) {
            endereco = "";
        }

        String nomeLow = (e.getNome() != null ? e.getNome() : "").toLowerCase();
        if (nomeLow.contains("cantagalo")) {
            cidade = "Cantagalo";
        } else if (nomeLow.contains("virmond")) {
            cidade = "Virmond";
        } else if (nomeLow.contains("porto barreiro")) {
            cidade = "Porto Barreiro";
        } else if (nomeLow.contains("nova laranjeiras")) {
            cidade = "Nova Laranjeiras";
        } else if (nomeLow.contains("rio bonito")) {
            cidade = "Rio Bonito do Iguaçu";
        } else if (nomeLow.contains("laranjeiras do sul")) {
            cidade = "Laranjeiras do Sul";
        }

        Double lat = null;
        Double lon = null;
        Boolean confirmada = null;
        String tipo = null;

        if (e.getDadosBrutos() != null && e.getDadosBrutos().trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode n = MAPPER.readTree(e.getDadosBrutos());
                if (n.hasNonNull("latitude")) lat = n.path("latitude").asDouble();
                else if (n.hasNonNull("lat")) lat = n.path("lat").asDouble();
                if (n.hasNonNull("longitude")) lon = n.path("longitude").asDouble();
                else if (n.hasNonNull("lon")) lon = n.path("lon").asDouble();
                if (n.hasNonNull("localizacaoConfirmada")) confirmada = n.path("localizacaoConfirmada").asBoolean();
                if (n.hasNonNull("tipoLocalizacao")) tipo = n.path("tipoLocalizacao").asText();
            } catch (Exception ignored) {}
        }

        boolean temCoords = (lat != null && lon != null && (lat != 0.0 || lon != 0.0));
        boolean temEndereco = (!endereco.isBlank());
        if (confirmada == null) confirmada = temCoords || temEndereco;
        if (tipo == null) {
            tipo = (temCoords && temEndereco) ? "EXATA" : (temCoords ? "GPS" : (temEndereco ? "ENDERECO" : "APROXIMADA"));
        }

        return new EmpresaResponse(
                e.getIdEmpresa(), e.getNome(), cat, cidade,
                e.getEstado(), endereco, e.getTelefone(), e.getEmail(), e.getSite(),
                e.getDescricao(), e.getCnpj(), e.getCriadoEm(), e.getAtualizadoEm(),
                lat, lon, confirmada, tipo
        );
    }
}

// ===== Propostas =====

record PropostaRequest(
    @NotBlank String idEmpresa,
    @NotBlank @Size(max = 255) String titulo,
    @Size(max = 4000) String resumo,
    String escopoJson
) {}

record PropostaResponse(
    String idProposta, String idEmpresa, String titulo, String status, String resumo,
    Instant validoAte, Instant criadoEm, Instant atualizadoEm
) {
    static PropostaResponse from(Proposta p) {
        return new PropostaResponse(p.getIdProposta(), p.getIdEmpresa(), p.getTitulo(), p.getStatus(),
                p.getResumo(), p.getValidoAte(), p.getCriadoEm(), p.getAtualizadoEm());
    }
}

// ===== Notas =====

record NotaRequest(@NotBlank @Size(max = 4000) String conteudo) {}

// ===== Consulta CNPJ =====

record DadosCnpjResponse(
    String cnpj,
    String razaoSocial,
    String nomeFantasia,
    String telefone,
    String email,
    String endereco,
    String cidade,
    String estado,
    String situacao
) {}

// ===== Suporte Tecnico =====

record ChamadoSuporteRequest(
    @NotBlank(message = "O e-mail de contato e obrigatorio")
    @Email(message = "Informe um e-mail valido")
    @Size(max = 255)
    String email,

    @Size(max = 150)
    String nome,

    @NotBlank(message = "A origem do chamado e obrigatoria")
    @Size(max = 100)
    String origem,

    @NotBlank(message = "Por favor, descreva o erro que esta ocorrendo no site")
    @Size(min = 5, max = 5000, message = "A descricao do erro deve ter entre 5 e 5000 caracteres")
    String descricaoErro,

    @Size(max = 500)
    String mensagemErroSistema
) {}

