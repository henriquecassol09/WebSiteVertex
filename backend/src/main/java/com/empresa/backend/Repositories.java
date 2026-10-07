package com.empresa.backend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface UsuarioRepository extends JpaRepository<Usuario, String> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}

interface SessaoRepository extends JpaRepository<Sessao, String> {

    Optional<Sessao> findByTokenAndRevogadaFalse(String tokenHash);

    @Modifying
    @Query("UPDATE Sessao s SET s.revogada = true WHERE s.idSessao = :id")
    void revogarPorId(@Param("id") String id);

    @Modifying
    @Query("UPDATE Sessao s SET s.revogada = true WHERE s.idUsuario = :idUsuario")
    void revogarTodasDoUsuario(@Param("idUsuario") String idUsuario);

    @Modifying
    @Query("DELETE FROM Sessao s WHERE s.expiraEm < :agora")
    void limparExpiradas(@Param("agora") Instant agora);
}

interface ContaRepository extends JpaRepository<Conta, String> {
    Optional<Conta> findByIdUsuarioAndIdProvedor(String idUsuario, String idProvedor);
}

interface VerificacaoRepository extends JpaRepository<Verificacao, String> {
    Optional<Verificacao> findByIdentificadorAndValor(String identificador, String valorHash);
}

interface PesquisaRepository extends JpaRepository<Pesquisa, String> {
    Page<Pesquisa> findByIdUsuario(String idUsuario, Pageable pageable);
    Optional<Pesquisa> findByIdPesquisaAndIdUsuario(String idPesquisa, String idUsuario);
}

interface EmpresaRepository extends JpaRepository<Empresa, String> {
    Page<Empresa> findByIdUsuario(String idUsuario, Pageable pageable);
    Optional<Empresa> findByIdEmpresaAndIdUsuario(String idEmpresa, String idUsuario);
    Page<Empresa> findByIdUsuarioAndIdPesquisa(String idUsuario, String idPesquisa, Pageable pageable);
}

interface PropostaRepository extends JpaRepository<Proposta, String> {
    Page<Proposta> findByIdUsuario(String idUsuario, Pageable pageable);
    Optional<Proposta> findByIdPropostaAndIdUsuario(String idProposta, String idUsuario);
}

interface VersaoPropostaRepository extends JpaRepository<VersaoProposta, String> {
    List<VersaoProposta> findByIdPropostaAndIdUsuarioOrderByVersaoDesc(String idProposta, String idUsuario);
    Optional<VersaoProposta> findTopByIdPropostaAndIdUsuarioOrderByVersaoDesc(String idProposta, String idUsuario);
}

interface NotaEmpresaRepository extends JpaRepository<NotaEmpresa, String> {
    List<NotaEmpresa> findByIdEmpresaAndIdUsuarioOrderByCriadoEmDesc(String idEmpresa, String idUsuario);
    Optional<NotaEmpresa> findByIdNotaEmpresaAndIdUsuario(String idNotaEmpresa, String idUsuario);
}
