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
    String senha

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

record MensagemResponse(String mensagem) {
    static MensagemResponse of(String mensagem) {
        return new MensagemResponse(mensagem);
    }
}

record AuthResponse(String accessToken, String refreshToken, long expiraEmSegundos, String tipo) {
    static AuthResponse of(String accessToken, String refreshToken, long expiraEmSegundos) {
        return new AuthResponse(accessToken, refreshToken, expiraEmSegundos, "Bearer");
    }
}

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
    String telefone
) {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    static PesquisaResponse from(Pesquisa p) {
        String raw = p.getRegiao();
        String endereco = "Centro";
        String cidade = "Laranjeiras do Sul";
        String estado = "PR";
        String categoria = "Comércio Local";
        String horario = "Não informado";
        String telefone = "Não informado";

        if (raw != null && raw.trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(raw);
                endereco = node.path("endereco").asText(endereco);
                cidade = node.path("cidade").asText(cidade);
                estado = node.path("estado").asText(estado);
                categoria = node.path("categoria").asText(categoria);
                horario = node.path("horario").asText(horario);
                telefone = node.path("telefone").asText(telefone);
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
            telefone
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
    String idPesquisa
) {}

record EmpresaResponse(
    String idEmpresa, String nome, String categoria, String cidade, String estado,
    String endereco, String telefone, String email, String site, String descricao,
    Instant criadoEm, Instant atualizadoEm
) {
    static EmpresaResponse from(Empresa e) {
        String cat = CategoriaDetector.refinar(e.getCategoria(), e.getNome());
        return new EmpresaResponse(e.getIdEmpresa(), e.getNome(), cat, e.getCidade(),
                e.getEstado(), e.getEndereco(), e.getTelefone(), e.getEmail(), e.getSite(),
                e.getDescricao(), e.getCriadoEm(), e.getAtualizadoEm());
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
