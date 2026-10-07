package com.empresa.backend;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

// Todas as entidades JPA do dominio, agrupadas em um unico arquivo por
// conveniencia. Nenhuma classe publica aqui: no mesmo pacote, visibilidade
// "package-private" (sem modificador) e suficiente para uso por
// repository/service/controller, que estao no mesmo pacote.

@Entity
@Table(name = "USUARIO")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Usuario {

    @Id
    @Column(name = "ID_USUARIO")
    private String idUsuario;

    @Column(name = "NOME", nullable = false)
    private String nome;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "EMAIL_VERIFICADO", nullable = false)
    @Builder.Default
    private boolean emailVerificado = false;

    @Column(name = "IMAGEM")
    private String imagem;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idUsuario == null) idUsuario = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}

@Entity
@Table(name = "SESSAO")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Sessao {

    @Id
    @Column(name = "ID_SESSAO")
    private String idSessao;

    @Column(name = "EXPIRA_EM", nullable = false)
    private Instant expiraEm;

    @Column(name = "TOKEN", nullable = false, unique = true)
    private String token;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "ENDERECO_IP")
    private String enderecoIp;

    @Column(name = "AGENTE_USUARIO")
    private String agenteUsuario;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "REVOGADA", nullable = false)
    @Builder.Default
    private boolean revogada = false;

    @PrePersist
    public void prePersist() {
        if (idSessao == null) idSessao = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}

@Entity
@Table(name = "CONTA")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Conta {

    @Id
    @Column(name = "ID_CONTA")
    private String idConta;

    @Column(name = "ID_CONTA_PROVEDOR", nullable = false)
    private String idContaProvedor;

    @Column(name = "ID_PROVEDOR", nullable = false)
    private String idProvedor;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "TOKEN_ACESSO")
    private String tokenAcesso;

    @Column(name = "TOKEN_ATUALIZACAO")
    private String tokenAtualizacao;

    @Column(name = "TOKEN_ID")
    private String tokenId;

    @Column(name = "TOKEN_ACESSO_EXPIRA_EM")
    private Instant tokenAcessoExpiraEm;

    @Column(name = "TOKEN_ATUALIZACAO_EXPIRA_EM")
    private Instant tokenAtualizacaoExpiraEm;

    @Column(name = "ESCOPO")
    private String escopo;

    @Column(name = "SENHA")
    private String senha;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idConta == null) idConta = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}

@Entity
@Table(name = "VERIFICACAO")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Verificacao {

    @Id
    @Column(name = "ID_VERIFICACAO")
    private String idVerificacao;

    @Column(name = "IDENTIFICADOR", nullable = false)
    private String identificador;

    @Column(name = "VALOR", nullable = false)
    private String valor;

    @Column(name = "EXPIRA_EM", nullable = false)
    private Instant expiraEm;

    @Column(name = "CRIADO_EM")
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idVerificacao == null) idVerificacao = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }
}

@Entity
@Table(name = "PESQUISAS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Pesquisa {

    @Id
    @Column(name = "ID_PESQUISA")
    private String idPesquisa;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "CONSULTA", nullable = false)
    private String consulta;

    @Column(name = "REGIAO")
    private String regiao;

    @Column(name = "STATUS", nullable = false)
    @Builder.Default
    private String status = "completed";

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    public void prePersist() {
        if (idPesquisa == null) idPesquisa = UUID.randomUUID().toString();
        criadoEm = Instant.now();
    }
}

@Entity
@Table(name = "EMPRESAS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Empresa {

    @Id
    @Column(name = "ID_EMPRESA")
    private String idEmpresa;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "ID_PESQUISA")
    private String idPesquisa;

    @Column(name = "NOME", nullable = false)
    private String nome;

    @Column(name = "CATEGORIA")
    private String categoria;

    @Column(name = "CIDADE")
    private String cidade;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "ENDERECO")
    private String endereco;

    @Column(name = "TELEFONE")
    private String telefone;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "SITE")
    private String site;

    @Column(name = "DESCRICAO", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "DADOS_BRUTOS", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String dadosBrutos;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idEmpresa == null) idEmpresa = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}

@Entity
@Table(name = "PROPOSTAS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Proposta {

    @Id
    @Column(name = "ID_PROPOSTA")
    private String idProposta;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "ID_EMPRESA", nullable = false)
    private String idEmpresa;

    @Column(name = "TITULO", nullable = false)
    private String titulo;

    @Column(name = "STATUS", nullable = false)
    @Builder.Default
    private String status = "draft";

    @Column(name = "RESUMO", columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "ESCOPO", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String escopo;

    @Column(name = "VALIDO_ATE")
    private Instant validoAte;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idProposta == null) idProposta = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}

@Entity
@Table(name = "VERSOES_PROPOSTA")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class VersaoProposta {

    @Id
    @Column(name = "ID_VERSAO_PROPOSTA")
    private String idVersaoProposta;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "ID_PROPOSTA", nullable = false)
    private String idProposta;

    @Column(name = "VERSAO", nullable = false)
    @Builder.Default
    private Integer versao = 1;

    @Column(name = "CONTEUDO", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String conteudo;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    public void prePersist() {
        if (idVersaoProposta == null) idVersaoProposta = UUID.randomUUID().toString();
        criadoEm = Instant.now();
    }
}

@Entity
@Table(name = "NOTAS_EMPRESA")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class NotaEmpresa {

    @Id
    @Column(name = "ID_NOTA_EMPRESA")
    private String idNotaEmpresa;

    @Column(name = "ID_USUARIO", nullable = false)
    private String idUsuario;

    @Column(name = "ID_EMPRESA", nullable = false)
    private String idEmpresa;

    @Column(name = "CONTEUDO", nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ATUALIZADO_EM", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (idNotaEmpresa == null) idNotaEmpresa = UUID.randomUUID().toString();
        Instant now = Instant.now();
        criadoEm = now;
        atualizadoEm = now;
    }

    @PreUpdate
    public void preUpdate() { atualizadoEm = Instant.now(); }
}
