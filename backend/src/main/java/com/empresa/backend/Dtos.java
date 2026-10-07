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

record PesquisaResponse(String idPesquisa, String consulta, String regiao, String status, Instant criadoEm) {
    static PesquisaResponse from(Pesquisa p) {
        return new PesquisaResponse(p.getIdPesquisa(), p.getConsulta(), p.getRegiao(), p.getStatus(), p.getCriadoEm());
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
        return new EmpresaResponse(e.getIdEmpresa(), e.getNome(), e.getCategoria(), e.getCidade(),
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
