package com.empresa.backend;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class AuthService {

    private static final String PROVEDOR_CREDENCIAIS = "credentials";

    private final UsuarioRepository usuarioRepository;
    private final ContaRepository contaRepository;
    private final SessaoRepository sessaoRepository;
    private final VerificacaoRepository verificacaoRepository;
    private final PesquisaRepository pesquisaRepository;
    private final EmpresaRepository empresaRepository;
    private final PropostaRepository propostaRepository;
    private final VersaoPropostaRepository versaoPropostaRepository;
    private final NotaEmpresaRepository notaEmpresaRepository;
    private final EmailService emailService;
    private final EmailValidatorService emailValidatorService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessControlDefense accessControlDefense;
    private final FotoUrlSecurityService fotoUrlSecurityService;

    @Value("${app.security.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.security.jwt.refresh-token-days}")
    private long refreshTokenDays;

    CsrfTokenResponse obterCsrfToken(HttpServletRequest httpReq) {
        String token = HashUtil.gerarTokenOpaco();
        return CsrfTokenResponse.of(token);
    }

    @Transactional
    RegistrarResponse registrar(RegistrarRequest req) {
        if (req.aceitouTermos() == null || !req.aceitouTermos()) {
            throw new BusinessException("É obrigatório concordar com os Termos de Uso para criar uma conta.");
        }

        String emailNormalizado = req.email().trim().toLowerCase();

        EmailValidatorService.ResultadoValidacao validacao = emailValidatorService.validarEmail(emailNormalizado);
        if (!validacao.valido()) {
            throw new BusinessException(validacao.mensagem());
        }

        Optional<Usuario> existenteOpt = usuarioRepository.findByEmailIgnoreCase(emailNormalizado);
        if (existenteOpt.isPresent()) {
            Usuario existente = existenteOpt.get();
            if (existente.isEmailVerificado()) {
                throw new BusinessException("Este e-mail já está cadastrado. Faça login na sua conta.");
            } else {
                contaRepository.findByIdUsuarioAndIdProvedor(existente.getIdUsuario(), PROVEDOR_CREDENCIAIS)
                        .ifPresent(contaRepository::delete);
                usuarioRepository.delete(existente);
            }
        }

        String codigo = HashUtil.gerarCodigoNumerico(6);
        String valor = codigo + "||" + req.nome().trim() + "||" + passwordEncoder.encode(req.senha());
        salvarCodigoVerificacao("CADASTRO:" + emailNormalizado, valor);

        emailService.enviarCodigoVerificacao(emailNormalizado, req.nome().trim(), codigo);

        return RegistrarResponse.of(true, emailNormalizado, "Código de confirmação enviado para seu e-mail. Digite o código para concluir seu cadastro.");
    }

    @Transactional
    AuthResponse verificarCodigoCadastro(VerificarCodigoRequest req, HttpServletRequest httpReq) {
        String emailNormalizado = req.email().trim().toLowerCase();
        String identificador = "CADASTRO:" + emailNormalizado;

        Verificacao verificacao = verificacaoRepository.findTopByIdentificadorOrderByCriadoEmDesc(identificador)
                .orElseThrow(() -> new BusinessException("Nenhum cadastro pendente encontrado para este e-mail. Solicite um novo código."));

        if (verificacao.getExpiraEm().isBefore(Instant.now())) {
            verificacaoRepository.delete(verificacao);
            throw new BusinessException("Código de verificação expirado. Por favor, realize o cadastro novamente.");
        }

        String[] partes = verificacao.getValor().split("\\|\\|", 3);
        String codigoEsperado = partes[0];
        String nome = partes.length > 1 ? partes[1] : req.email().split("@")[0];
        String senhaCriptografada = partes.length > 2 ? partes[2] : null;

        if (!codigoEsperado.trim().equals(req.codigo().trim())) {
            accessControlDefense.registrarFalhaOtp(identificador, () -> verificacaoRepository.delete(verificacao));
            throw new BusinessException("Código de verificação incorreto");
        }

        accessControlDefense.registrarSucessoOtp(identificador);

        Usuario usuario = Usuario.builder()
                .nome(nome)
                .email(emailNormalizado)
                .emailVerificado(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        if (senhaCriptografada != null) {
            Conta conta = Conta.builder()
                    .idContaProvedor(usuario.getIdUsuario())
                    .idProvedor(PROVEDOR_CREDENCIAIS)
                    .idUsuario(usuario.getIdUsuario())
                    .senha(senhaCriptografada)
                    .build();
            contaRepository.save(conta);
        }

        verificacaoRepository.delete(verificacao);

        return emitirTokens(usuario, httpReq);
    }

    @Transactional
    MensagemResponse reenviarCodigoCadastro(ReenviarCodigoRequest req) {
        String emailNormalizado = req.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            Usuario u = usuarioRepository.findByEmailIgnoreCase(emailNormalizado).orElse(null);
            if (u != null && u.isEmailVerificado()) {
                return MensagemResponse.of("Este e-mail já está cadastrado e verificado. Você pode fazer login normalmente.");
            }
        }

        String identificador = "CADASTRO:" + emailNormalizado;
        Verificacao verificacao = verificacaoRepository.findTopByIdentificadorOrderByCriadoEmDesc(identificador)
                .orElseThrow(() -> new BusinessException("Nenhum cadastro pendente para este e-mail. Realize o cadastro novamente."));

        String[] partes = verificacao.getValor().split("\\|\\|", 3);
        String nome = partes.length > 1 ? partes[1] : "Usuário";
        String senhaCriptografada = partes.length > 2 ? partes[2] : "";

        String novoCodigo = HashUtil.gerarCodigoNumerico(6);
        verificacao.setValor(novoCodigo + "||" + nome + "||" + senhaCriptografada);
        verificacao.setExpiraEm(Instant.now().plus(Duration.ofMinutes(15)));
        verificacaoRepository.save(verificacao);

        emailService.enviarCodigoVerificacao(emailNormalizado, nome, novoCodigo);

        return MensagemResponse.of("Novo código de verificação enviado para seu e-mail.");
    }

    @Transactional
    AuthResponse login(LoginRequest req, HttpServletRequest httpReq) {
        String emailNormalizado = req.email().trim().toLowerCase();
        String ip = httpReq != null ? clientIp(httpReq) : "127.0.0.1";

        accessControlDefense.verificarBloqueioLogin(emailNormalizado, ip);

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmailIgnoreCase(emailNormalizado);
        if (usuarioOpt.isEmpty()) {
            accessControlDefense.registrarFalhaLogin(emailNormalizado, ip);
            if (verificacaoRepository.findTopByIdentificadorOrderByCriadoEmDesc("CADASTRO:" + emailNormalizado).isPresent()) {
                throw new EmailNaoVerificadoException("Seu cadastro ainda não foi confirmado. Digite o código de 6 dígitos enviado para seu e-mail.");
            }
            throw new AutenticacaoException("Credenciais invalidas");
        }

        Usuario usuario = usuarioOpt.get();

        Conta conta = contaRepository.findByIdUsuarioAndIdProvedor(usuario.getIdUsuario(), PROVEDOR_CREDENCIAIS)
                .orElseThrow(() -> {
                    accessControlDefense.registrarFalhaLogin(emailNormalizado, ip);
                    return new AutenticacaoException("Credenciais invalidas");
                });

        if (conta.getSenha() == null || !passwordEncoder.matches(req.senha(), conta.getSenha())) {
            accessControlDefense.registrarFalhaLogin(emailNormalizado, ip);
            throw new AutenticacaoException("Credenciais invalidas");
        }

        if (!usuario.isEmailVerificado()) {
            throw new EmailNaoVerificadoException("Seu e-mail ainda não foi verificado. Enviamos um código para " + emailNormalizado + ".");
        }

        accessControlDefense.registrarSucessoLogin(emailNormalizado, ip);
        return emitirTokens(usuario, httpReq);
    }

    @Transactional
    MensagemResponse solicitarRecuperacaoSenha(SolicitarRecuperacaoRequest req) {
        String emailNormalizado = req.email().trim().toLowerCase();
        usuarioRepository.findByEmailIgnoreCase(emailNormalizado).ifPresent(usuario -> {
            String codigo = HashUtil.gerarCodigoNumerico(6);
            salvarCodigoVerificacao("RECUPERACAO:" + emailNormalizado, codigo);
            emailService.enviarCodigoRecuperacaoSenha(emailNormalizado, usuario.getNome(), codigo);
        });

        return MensagemResponse.of("Se o e-mail informado estiver cadastrado, enviamos as instruções com o código para redefinir a senha.");
    }

    @Transactional
    MensagemResponse redefinirSenha(RedefinirSenhaRequest req) {
        String emailNormalizado = req.email().trim().toLowerCase();
        String identificador = "RECUPERACAO:" + emailNormalizado;

        Verificacao verificacao = verificacaoRepository.findByIdentificadorAndValor(identificador, req.codigo())
                .orElseThrow(() -> {
                    accessControlDefense.registrarFalhaOtp(identificador, () -> verificacaoRepository.deleteByIdentificador(identificador));
                    return new BusinessException("Código de recuperação inválido");
                });

        if (verificacao.getExpiraEm().isBefore(Instant.now())) {
            verificacaoRepository.delete(verificacao);
            throw new BusinessException("Código expirado. Solicite uma nova recuperação de senha.");
        }

        accessControlDefense.registrarSucessoOtp(identificador);

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(emailNormalizado)
                .orElseThrow(() -> new BusinessException("Não foi possível redefinir a senha"));

        Conta conta = contaRepository.findByIdUsuarioAndIdProvedor(usuario.getIdUsuario(), PROVEDOR_CREDENCIAIS)
                .orElseThrow(() -> new BusinessException("Conta de credenciais não encontrada"));

        conta.setSenha(passwordEncoder.encode(req.novaSenha()));
        contaRepository.save(conta);

        if (!usuario.isEmailVerificado()) {
            usuario.setEmailVerificado(true);
            usuarioRepository.save(usuario);
        }

        sessaoRepository.revogarTodasDoUsuario(usuario.getIdUsuario());
        verificacaoRepository.delete(verificacao);

        return new MensagemResponse("Senha alterada com sucesso! Você já pode entrar com sua nova senha.");
    }

    @Transactional
    MensagemResponse alterarSenha(String idUsuario, AlterarSenhaRequest req, String authHeader) {
        Conta conta = contaRepository.findByIdUsuarioAndIdProvedor(idUsuario, PROVEDOR_CREDENCIAIS)
                .orElseThrow(() -> new BusinessException("Conta de credenciais nao encontrada"));

        if (!passwordEncoder.matches(req.senhaAtual(), conta.getSenha())) {
            throw new BusinessException("Senha atual incorreta.");
        }

        if (passwordEncoder.matches(req.novaSenha(), conta.getSenha())) {
            throw new BusinessException("A nova senha deve ser diferente da senha atual.");
        }

        conta.setSenha(passwordEncoder.encode(req.novaSenha()));
        contaRepository.save(conta);

        sessaoRepository.revogarTodasDoUsuario(idUsuario);

        return MensagemResponse.of("Senha alterada com sucesso! Suas outras sessoes foram encerradas.");
    }

    private void salvarCodigoVerificacao(String identificador, String codigo) {
        verificacaoRepository.deleteByIdentificador(identificador);
        Verificacao verificacao = Verificacao.builder()
                .identificador(identificador)
                .valor(codigo)
                .expiraEm(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
        verificacaoRepository.save(verificacao);
    }

    @Transactional
    AuthResponse refresh(RefreshRequest req, HttpServletRequest httpReq) {
        String hash = HashUtil.sha256(req.refreshToken());

        Optional<Sessao> sessaoAtivaOpt = sessaoRepository.findByTokenAndRevogadaFalse(hash);
        if (sessaoAtivaOpt.isEmpty()) {
            sessaoRepository.findByToken(hash).ifPresent(s -> {
                sessaoRepository.revogarTodasDoUsuario(s.getIdUsuario());
                throw new AutenticacaoException("Tentativa de reutilizacao de sessao revogada detectada. Todas as sessoes ativas foram desconectadas por seguranca.");
            });
            throw new AutenticacaoException("Sessao invalida ou expirada");
        }

        Sessao sessao = sessaoAtivaOpt.get();
        if (sessao.getExpiraEm().isBefore(Instant.now())) {
            sessaoRepository.revogarPorId(sessao.getIdSessao());
            throw new AutenticacaoException("Sessao invalida ou expirada");
        }

        sessaoRepository.revogarPorId(sessao.getIdSessao());

        Usuario usuario = usuarioRepository.findById(sessao.getIdUsuario())
                .orElseThrow(() -> new AutenticacaoException("Sessao invalida ou expirada"));

        return emitirTokens(usuario, httpReq);
    }

    @Transactional
    void logout(String refreshToken, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            accessControlDefense.revogarTokenJwt(token, jwtService.extrairExpiracao(token));
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            String hash = HashUtil.sha256(refreshToken);
            sessaoRepository.findByTokenAndRevogadaFalse(hash)
                    .ifPresent(s -> sessaoRepository.revogarPorId(s.getIdSessao()));
        }
    }

    @Transactional
    void logoutTodasSessoes(String idUsuario, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            accessControlDefense.revogarTokenJwt(token, jwtService.extrairExpiracao(token));
        }
        sessaoRepository.revogarTodasDoUsuario(idUsuario);
    }

    @Transactional(readOnly = true)
    UsuarioResponse obterUsuarioAtual(String idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return UsuarioResponse.of(usuario);
    }

    @Transactional
    UsuarioResponse atualizarFoto(String idUsuario, AtualizarFotoRequest req) {
        accessControlDefense.verificarLimiteAtualizacaoFoto(idUsuario);
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String fotoUrl = req != null && req.fotoUrl() != null ? req.fotoUrl().trim() : null;
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            fotoUrlSecurityService.validarUrlFotoSegura(fotoUrl);
            usuario.setImagem(fotoUrl);
        } else {
            usuario.setImagem(null);
        }

        Usuario salvo = usuarioRepository.save(usuario);
        return UsuarioResponse.of(salvo);
    }

    @Transactional
    UsuarioResponse uploadFoto(String idUsuario, MultipartFile file) {
        accessControlDefense.verificarLimiteAtualizacaoFoto(idUsuario);
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String dataUri = fotoUrlSecurityService.processarUploadFotoSegura(file);
        usuario.setImagem(dataUri);
        Usuario salvo = usuarioRepository.save(usuario);
        return UsuarioResponse.of(salvo);
    }

    @Transactional
    void deletarConta(String idUsuario, DeletarContaRequest req, String authHeader) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (req != null && req.senha() != null && !req.senha().isBlank()) {
            Conta conta = contaRepository.findByIdUsuarioAndIdProvedor(idUsuario, PROVEDOR_CREDENCIAIS)
                    .orElse(null);
            if (conta != null && conta.getSenha() != null && !passwordEncoder.matches(req.senha(), conta.getSenha())) {
                throw new BusinessException("Senha incorreta. Não foi possível deletar a conta.");
            }
        }

        logoutTodasSessoes(idUsuario, authHeader);

        notaEmpresaRepository.deleteByIdUsuario(idUsuario);
        versaoPropostaRepository.deleteByIdUsuario(idUsuario);
        propostaRepository.deleteByIdUsuario(idUsuario);
        empresaRepository.deleteByIdUsuario(idUsuario);
        pesquisaRepository.deleteByIdUsuario(idUsuario);
        sessaoRepository.deleteByIdUsuario(idUsuario);
        contaRepository.deleteByIdUsuario(idUsuario);
        usuarioRepository.delete(usuario);
    }

    private AuthResponse emitirTokens(Usuario usuario, HttpServletRequest httpReq) {
        String accessToken = jwtService.gerarAccessToken(usuario.getIdUsuario());

        String refreshTokenPuro = HashUtil.gerarTokenOpaco();
        Sessao sessao = Sessao.builder()
                .idUsuario(usuario.getIdUsuario())
                .token(HashUtil.sha256(refreshTokenPuro))
                .expiraEm(Instant.now().plusSeconds(refreshTokenDays * 24 * 3600))
                .enderecoIp(httpReq != null ? clientIp(httpReq) : null)
                .agenteUsuario(httpReq != null ? truncar(httpReq.getHeader("User-Agent"), 500) : null)
                .build();
        sessaoRepository.save(sessao);

        return AuthResponse.of(accessToken, refreshTokenPuro, accessTokenMinutes * 60);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() > max ? valor.substring(0, max) : valor;
    }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PesquisaService {

    private final PesquisaRepository pesquisaRepository;

    @Transactional
    PesquisaResponse criar(String idUsuario, PesquisaRequest req) {
        Pesquisa pesquisa = Pesquisa.builder()
                .idUsuario(idUsuario)
                .consulta(req.consulta().trim())
                .regiao(req.regiao())
                .build();
        return PesquisaResponse.from(pesquisaRepository.save(pesquisa));
    }

    Page<PesquisaResponse> listar(String idUsuario, Pageable pageable) {
        return pesquisaRepository.findNaoSalvasByIdUsuario(idUsuario, pageable).map(PesquisaResponse::from);
    }

    PesquisaResponse buscar(String idUsuario, String idPesquisa) {
        return PesquisaResponse.from(buscarEntidade(idUsuario, idPesquisa));
    }

    @Transactional
    void excluir(String idUsuario, String idPesquisa) {
        Pesquisa pesquisa = buscarEntidade(idUsuario, idPesquisa);
        pesquisaRepository.delete(pesquisa);
    }

    private Pesquisa buscarEntidade(String idUsuario, String idPesquisa) {
        return pesquisaRepository.findByIdPesquisaAndIdUsuario(idPesquisa, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Pesquisa nao encontrada"));
    }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional
    EmpresaResponse criar(String idUsuario, EmpresaRequest req) {
        String nomeTrim = req.nome().trim();

        if (empresaRepository.existsByIdUsuarioAndNomeIgnoreCase(idUsuario, nomeTrim)) {
            throw new IllegalArgumentException("Esta empresa já foi salva no sistema");
        }
        if (req.idPesquisa() != null && !req.idPesquisa().isBlank()
                && empresaRepository.existsByIdUsuarioAndIdPesquisa(idUsuario, req.idPesquisa())) {
            throw new IllegalArgumentException("Esta empresa já foi salva no sistema");
        }

        Empresa empresa = Empresa.builder()
                .idUsuario(idUsuario)
                .idPesquisa(req.idPesquisa())
                .nome(nomeTrim)
                .categoria(req.categoria())
                .cidade(req.cidade())
                .estado(req.estado())
                .endereco(req.endereco())
                .telefone(req.telefone())
                .email(req.email())
                .site(req.site())
                .descricao(req.descricao())
                .cnpj(req.cnpj())
                .build();
        return EmpresaResponse.from(empresaRepository.save(empresa));
    }

    Page<EmpresaResponse> listar(String idUsuario, Pageable pageable) {
        return empresaRepository.findByIdUsuario(idUsuario, pageable).map(EmpresaResponse::from);
    }

    EmpresaResponse buscar(String idUsuario, String idEmpresa) {
        return EmpresaResponse.from(buscarEntidade(idUsuario, idEmpresa));
    }

    @Transactional
    EmpresaResponse atualizar(String idUsuario, String idEmpresa, EmpresaRequest req) {
        Empresa empresa = buscarEntidade(idUsuario, idEmpresa);
        empresa.setNome(req.nome().trim());
        empresa.setCategoria(req.categoria());
        empresa.setCidade(req.cidade());
        empresa.setEstado(req.estado());
        empresa.setEndereco(req.endereco());
        empresa.setTelefone(req.telefone());
        empresa.setEmail(req.email());
        empresa.setSite(req.site());
        empresa.setDescricao(req.descricao());
        empresa.setCnpj(req.cnpj());
        return EmpresaResponse.from(empresaRepository.save(empresa));
    }

    @Transactional
    void excluir(String idUsuario, String idEmpresa) {
        empresaRepository.delete(buscarEntidade(idUsuario, idEmpresa));
    }

    private Empresa buscarEntidade(String idUsuario, String idEmpresa) {
        return empresaRepository.findByIdEmpresaAndIdUsuario(idEmpresa, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada"));
    }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PropostaService {

    private final PropostaRepository propostaRepository;
    private final EmpresaRepository empresaRepository;
    private final VersaoPropostaRepository versaoPropostaRepository;

    @Transactional
    PropostaResponse criar(String idUsuario, PropostaRequest req) {
        Empresa empresa = empresaRepository.findByIdEmpresaAndIdUsuario(req.idEmpresa(), idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada"));

        Proposta proposta = Proposta.builder()
                .idUsuario(idUsuario)
                .idEmpresa(empresa.getIdEmpresa())
                .titulo(req.titulo().trim())
                .resumo(req.resumo())
                .escopo(req.escopoJson())
                .build();
        proposta = propostaRepository.save(proposta);

        VersaoProposta versao = VersaoProposta.builder()
                .idUsuario(idUsuario)
                .idProposta(proposta.getIdProposta())
                .versao(1)
                .conteudo(req.escopoJson() != null ? req.escopoJson() : "{}")
                .build();
        versaoPropostaRepository.save(versao);

        return PropostaResponse.from(proposta);
    }

    Page<PropostaResponse> listar(String idUsuario, Pageable pageable) {
        return propostaRepository.findByIdUsuario(idUsuario, pageable).map(PropostaResponse::from);
    }

    PropostaResponse buscar(String idUsuario, String idProposta) {
        return PropostaResponse.from(buscarEntidade(idUsuario, idProposta));
    }

    @Transactional
    PropostaResponse novaVersao(String idUsuario, String idProposta, String conteudoJson) {
        Proposta proposta = buscarEntidade(idUsuario, idProposta);

        int proximaVersao = versaoPropostaRepository
                .findTopByIdPropostaAndIdUsuarioOrderByVersaoDesc(idProposta, idUsuario)
                .map(v -> v.getVersao() + 1)
                .orElse(1);

        VersaoProposta versao = VersaoProposta.builder()
                .idUsuario(idUsuario)
                .idProposta(idProposta)
                .versao(proximaVersao)
                .conteudo(conteudoJson)
                .build();
        versaoPropostaRepository.save(versao);

        proposta.setEscopo(conteudoJson);
        return PropostaResponse.from(propostaRepository.save(proposta));
    }

    private Proposta buscarEntidade(String idUsuario, String idProposta) {
        return propostaRepository.findByIdPropostaAndIdUsuario(idProposta, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Proposta nao encontrada"));
    }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotaEmpresaService {

    private final NotaEmpresaRepository notaEmpresaRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional
    NotaEmpresa criar(String idUsuario, String idEmpresa, NotaRequest req) {
        Empresa empresa = empresaRepository.findByIdEmpresaAndIdUsuario(idEmpresa, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa nao encontrada"));

        NotaEmpresa nota = NotaEmpresa.builder()
                .idUsuario(idUsuario)
                .idEmpresa(empresa.getIdEmpresa())
                .conteudo(req.conteudo().trim())
                .build();
        return notaEmpresaRepository.save(nota);
    }

    List<NotaEmpresa> listar(String idUsuario, String idEmpresa) {
        return notaEmpresaRepository.findByIdEmpresaAndIdUsuarioOrderByCriadoEmDesc(idEmpresa, idUsuario);
    }

    @Transactional
    void excluir(String idUsuario, String idEmpresa, String idNota) {
        NotaEmpresa nota = notaEmpresaRepository.findByIdNotaEmpresaAndIdEmpresaAndIdUsuario(idNota, idEmpresa, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Nota nao encontrada para a empresa informada"));
        notaEmpresaRepository.delete(nota);
    }
}
