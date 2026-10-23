package com.empresa.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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

    @GetMapping("/csrf-token")
    ResponseEntity<CsrfTokenResponse> obterCsrfToken(HttpServletRequest http) {
        return ResponseEntity.ok(authService.obterCsrfToken(http));
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

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<UsuarioResponse> obterUsuarioAtual() {
        return ResponseEntity.ok(authService.obterUsuarioAtual(AutenticacaoUtil.idUsuarioAtual()));
    }

    @PutMapping("/foto")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<UsuarioResponse> atualizarFoto(@Valid @RequestBody AtualizarFotoRequest req) {
        return ResponseEntity.ok(authService.atualizarFoto(AutenticacaoUtil.idUsuarioAtual(), req));
    }

    @PostMapping(value = "/foto/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<UsuarioResponse> uploadFoto(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(authService.uploadFoto(AutenticacaoUtil.idUsuarioAtual(), file));
    }

    @DeleteMapping("/foto")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<UsuarioResponse> removerFoto() {
        return ResponseEntity.ok(authService.atualizarFoto(AutenticacaoUtil.idUsuarioAtual(), new AtualizarFotoRequest(null)));
    }

    @PostMapping("/alterar-senha")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<MensagemResponse> alterarSenha(@Valid @RequestBody AlterarSenhaRequest req,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(authService.alterarSenha(AutenticacaoUtil.idUsuarioAtual(), req, authHeader));
    }

    @DeleteMapping("/conta")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<MensagemResponse> deletarConta(@RequestBody(required = false) DeletarContaRequest req,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.deletarConta(AutenticacaoUtil.idUsuarioAtual(), req, authHeader);
        return ResponseEntity.ok(MensagemResponse.of("Conta excluída com sucesso."));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req,
                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(req.refreshToken(), authHeader);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-todas-sessoes")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> logoutTodasSessoes(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logoutTodasSessoes(AutenticacaoUtil.idUsuarioAtual(), authHeader);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/pesquisas")
@Validated
@PreAuthorize("isAuthenticated()")
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
    ResponseEntity<PesquisaResponse> buscar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id) {
        return ResponseEntity.ok(pesquisaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> excluir(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id) {
        pesquisaService.excluir(AutenticacaoUtil.idUsuarioAtual(), id);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/empresas")
@Validated
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
class EmpresaController {

    private final EmpresaService empresaService;
    private final ProspeccaoService prospeccaoService;

    @GetMapping("/consultar-cnpj/{cnpj}")
    ResponseEntity<DadosCnpjResponse> consultarCnpj(@PathVariable @Pattern(regexp = "^[0-9.\\-/]{1,20}$", message = "Formato de CNPJ invalido") String cnpj) {
        return ResponseEntity.ok(prospeccaoService.consultarCnpjPublico(cnpj));
    }

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
    ResponseEntity<EmpresaResponse> buscar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id) {
        return ResponseEntity.ok(empresaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @PutMapping("/{id}")
    ResponseEntity<EmpresaResponse> atualizar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id, @Valid @RequestBody EmpresaRequest req) {
        return ResponseEntity.ok(empresaService.atualizar(AutenticacaoUtil.idUsuarioAtual(), id, req));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> excluir(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id) {
        empresaService.excluir(AutenticacaoUtil.idUsuarioAtual(), id);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/propostas")
@Validated
@PreAuthorize("isAuthenticated()")
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
    ResponseEntity<PropostaResponse> buscar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id) {
        return ResponseEntity.ok(propostaService.buscar(AutenticacaoUtil.idUsuarioAtual(), id));
    }

    @PostMapping("/{id}/versoes")
    ResponseEntity<PropostaResponse> novaVersao(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String id, @RequestBody @NotBlank String conteudoJson) {
        return ResponseEntity.ok(propostaService.novaVersao(AutenticacaoUtil.idUsuarioAtual(), id, conteudoJson));
    }
}

@RestController
@RequestMapping("/api/empresas/{idEmpresa}/notas")
@Validated
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
class NotaEmpresaController {

    private final NotaEmpresaService notaEmpresaService;

    @PostMapping
    ResponseEntity<NotaEmpresa> criar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String idEmpresa, @Valid @RequestBody NotaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notaEmpresaService.criar(AutenticacaoUtil.idUsuarioAtual(), idEmpresa, req));
    }

    @GetMapping
    ResponseEntity<List<NotaEmpresa>> listar(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String idEmpresa) {
        return ResponseEntity.ok(notaEmpresaService.listar(AutenticacaoUtil.idUsuarioAtual(), idEmpresa));
    }

    @DeleteMapping("/{idNota}")
    ResponseEntity<Void> excluir(@PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String idEmpresa, @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Identificador invalido") String idNota) {
        notaEmpresaService.excluir(AutenticacaoUtil.idUsuarioAtual(), idEmpresa, idNota);
        return ResponseEntity.noContent().build();
    }
}

@RestController
@RequestMapping("/api/suporte")
@Validated
@RequiredArgsConstructor
class SuporteController {

    private final SuporteService suporteService;

    @PostMapping("/chamado")
    ResponseEntity<MensagemResponse> enviarChamado(@Valid @RequestBody ChamadoSuporteRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(suporteService.enviarChamado(req, http));
    }
}

