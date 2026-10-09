package com.empresa.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;
    private final EmailValidatorService emailValidatorService;

    @GetMapping("/validar-email")
    ResponseEntity<EmailValidatorService.ResultadoValidacao> validarEmail(@RequestParam String email) {
        return ResponseEntity.ok(emailValidatorService.validarEmail(email));
    }

    @PostMapping("/registrar")
    ResponseEntity<RegistrarResponse> registrar(@Valid @RequestBody RegistrarRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(req));
    }

    @PostMapping("/verificar-codigo-cadastro")
    ResponseEntity<AuthResponse> verificarCodigoCadastro(@Valid @RequestBody VerificarCodigoRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(authService.verificarCodigoCadastro(req, http));
    }

    @PostMapping("/reenviar-codigo-cadastro")
    ResponseEntity<MensagemResponse> reenviarCodigoCadastro(@Valid @RequestBody ReenviarCodigoRequest req) {
        return ResponseEntity.ok(authService.reenviarCodigoCadastro(req));
    }

    @PostMapping("/recuperar-senha")
    ResponseEntity<MensagemResponse> solicitarRecuperacaoSenha(@Valid @RequestBody SolicitarRecuperacaoRequest req) {
        return ResponseEntity.ok(authService.solicitarRecuperacaoSenha(req));
    }

    @PostMapping("/redefinir-senha")
    ResponseEntity<MensagemResponse> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        return ResponseEntity.ok(authService.redefinirSenha(req));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(req, http));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(authService.refresh(req, http));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-todas-sessoes")
    ResponseEntity<Void> logoutTodasSessoes() {
        authService.logoutTodasSessoes(AutenticacaoUtil.idUsuarioAtual());
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/pesquisas")
@RequiredArgsConstructor
class PesquisaController {

    private final PesquisaService pesquisaService;
    private final ProspeccaoService prospeccaoService;

    @PostMapping("/prospectar-regiao")
    ResponseEntity<List<PesquisaResponse>> prospectarRegiao() {
        return ResponseEntity.ok(prospeccaoService.prospectarEmpresasSemSite(AutenticacaoUtil.idUsuarioAtual()));
    }

    @PostMapping
    ResponseEntity<PesquisaResponse> criar(@Valid @RequestBody PesquisaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pesquisaService.criar(AutenticacaoUtil.idUsuarioAtual(), req));
    }

    @GetMapping
    ResponseEntity<Page<PesquisaResponse>> listar(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pesquisaService.listar(AutenticacaoUtil.idUsuarioAtual(), pageable));
    }

    @GetMapping("/{id}")
    ResponseEntity<PesquisaResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(pesquisaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> excluir(@PathVariable String id) {
        pesquisaService.excluir(AutenticacaoUtil.idUsuarioAtual(), id);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
class EmpresaController {

    private final EmpresaService empresaService;

    @PostMapping
    ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody EmpresaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.criar(AutenticacaoUtil.idUsuarioAtual(), req));
    }

    @GetMapping
    ResponseEntity<Page<EmpresaResponse>> listar(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(empresaService.listar(AutenticacaoUtil.idUsuarioAtual(), pageable));
    }

    @GetMapping("/{id}")
    ResponseEntity<EmpresaResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(empresaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @PutMapping("/{id}")
    ResponseEntity<EmpresaResponse> atualizar(@PathVariable String id, @Valid @RequestBody EmpresaRequest req) {
        return ResponseEntity.ok(empresaService.atualizar(AutenticacaoUtil.idUsuarioAtual(), id, req));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> excluir(@PathVariable String id) {
        empresaService.excluir(AutenticacaoUtil.idUsuarioAtual(), id);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/propostas")
@RequiredArgsConstructor
class PropostaController {

    private final PropostaService propostaService;

    @PostMapping
    ResponseEntity<PropostaResponse> criar(@Valid @RequestBody PropostaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propostaService.criar(AutenticacaoUtil.idUsuarioAtual(), req));
    }

    @GetMapping
    ResponseEntity<Page<PropostaResponse>> listar(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(propostaService.listar(AutenticacaoUtil.idUsuarioAtual(), pageable));
    }

    @GetMapping("/{id}")
    ResponseEntity<PropostaResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(propostaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @PostMapping("/{id}/versoes")
    ResponseEntity<PropostaResponse> novaVersao(@PathVariable String id, @RequestBody @NotBlank String conteudoJson) {
        return ResponseEntity.ok(propostaService.novaVersao(AutenticacaoUtil.idUsuarioAtual(), id, conteudoJson));
    }
}

@RestController
@RequestMapping("/api/empresas/{idEmpresa}/notas")
@RequiredArgsConstructor
class NotaEmpresaController {

    private final NotaEmpresaService notaEmpresaService;

    @PostMapping
    ResponseEntity<NotaEmpresa> criar(@PathVariable String idEmpresa, @Valid @RequestBody NotaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaEmpresaService.criar(AutenticacaoUtil.idUsuarioAtual(), idEmpresa, req));
    }

    @GetMapping
    ResponseEntity<List<NotaEmpresa>> listar(@PathVariable String idEmpresa) {
        return ResponseEntity.ok(notaEmpresaService.listar(AutenticacaoUtil.idUsuarioAtual(), idEmpresa));
    }

    @DeleteMapping("/{idNota}")
    ResponseEntity<Void> excluir(@PathVariable String idEmpresa, @PathVariable String idNota) {
        notaEmpresaService.excluir(AutenticacaoUtil.idUsuarioAtual(), idNota);
        return ResponseEntity.noContent().build();
    }
}
