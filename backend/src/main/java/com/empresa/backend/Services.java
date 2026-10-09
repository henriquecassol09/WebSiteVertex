package com.empresa.backend;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final EmailService emailService;
    private final EmailValidatorService emailValidatorService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.security.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.security.jwt.refresh-token-days}")
    private long refreshTokenDays;

    @Transactional
    RegistrarResponse registrar(RegistrarRequest req) {
        String emailNormalizado = req.email().trim().toLowerCase();

        // 1. Validação Open-Source profunda de e-mail (sintaxe, DNS MX e domínios descartáveis)
        EmailValidatorService.ResultadoValidacao validacao = emailValidatorService.validarEmail(emailNormalizado);
        if (!validacao.valido()) {
            throw new BusinessException(validacao.mensagem());
        }

        // 2. Se já existir conta verificada com este e-mail, impede cadastro duplicado
        Optional<Usuario> existenteOpt = usuarioRepository.findByEmailIgnoreCase(emailNormalizado);
        if (existenteOpt.isPresent()) {
            Usuario existente = existenteOpt.get();
            if (existente.isEmailVerificado()) {
                throw new BusinessException("Este e-mail já está cadastrado. Faça login na sua conta.");
            } else {
                // Limpa registro pendente antigo se houver
                contaRepository.findByIdUsuarioAndIdProvedor(existente.getIdUsuario(), PROVEDOR_CREDENCIAIS)
                        .ifPresent(contaRepository::delete);
                usuarioRepository.delete(existente);
            }
        }

        // 3. NÃO salva Usuario nem Conta no banco aqui!
        // Salva apenas a intenção de cadastro com código de 6 dígitos temporário (15 min)
        String codigo = HashUtil.gerarCodigoNumerico(6);
        String valor = codigo + "||" + req.nome().trim() + "||" + passwordEncoder.encode(req.senha());
        salvarCodigoVerificacao("CADASTRO:" + emailNormalizado, valor);

        // 4. Dispara o e-mail com o código de segurança
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
            throw new BusinessException("Código de verificação incorreto");
        }

        // SOMENTE AGORA o usuário e a conta são oficialmente criados no banco de dados!
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

        // Remove a verificação utilizada
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

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmailIgnoreCase(emailNormalizado);
        if (usuarioOpt.isEmpty()) {
            if (verificacaoRepository.findTopByIdentificadorOrderByCriadoEmDesc("CADASTRO:" + emailNormalizado).isPresent()) {
                throw new EmailNaoVerificadoException("Seu cadastro ainda não foi confirmado. Digite o código de 6 dígitos enviado para seu e-mail.");
            }
            throw new AutenticacaoException("Credenciais invalidas");
        }

        Usuario usuario = usuarioOpt.get();

        Conta conta = contaRepository.findByIdUsuarioAndIdProvedor(usuario.getIdUsuario(), PROVEDOR_CREDENCIAIS)
                .orElseThrow(() -> new AutenticacaoException("Credenciais invalidas"));

        if (conta.getSenha() == null || !passwordEncoder.matches(req.senha(), conta.getSenha())) {
            // Mesma mensagem para usuario inexistente ou senha errada — evita enumeracao
            throw new AutenticacaoException("Credenciais invalidas");
        }

        if (!usuario.isEmailVerificado()) {
            throw new EmailNaoVerificadoException("Seu e-mail ainda não foi verificado. Enviamos um código para " + emailNormalizado + ".");
        }

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
                .orElseThrow(() -> new BusinessException("Código de recuperação inválido"));

        if (verificacao.getExpiraEm().isBefore(Instant.now())) {
            verificacaoRepository.delete(verificacao);
            throw new BusinessException("Código expirado. Solicite uma nova recuperação de senha.");
        }

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

        Sessao sessao = sessaoRepository.findByTokenAndRevogadaFalse(hash)
                .orElseThrow(() -> new AutenticacaoException("Sessao invalida ou expirada"));

        if (sessao.getExpiraEm().isBefore(Instant.now())) {
            sessaoRepository.revogarPorId(sessao.getIdSessao());
            throw new AutenticacaoException("Sessao invalida ou expirada");
        }

        // Rotacao: a sessao usada e sempre revogada, mesmo em caso de sucesso,
        // e uma nova e emitida. Reuso de um refresh token ja usado e bloqueado.
        sessaoRepository.revogarPorId(sessao.getIdSessao());

        Usuario usuario = usuarioRepository.findById(sessao.getIdUsuario())
                .orElseThrow(() -> new AutenticacaoException("Sessao invalida ou expirada"));

        return emitirTokens(usuario, httpReq);
    }

    @Transactional
    void logout(String refreshToken) {
        String hash = HashUtil.sha256(refreshToken);
        sessaoRepository.findByTokenAndRevogadaFalse(hash)
                .ifPresent(s -> sessaoRepository.revogarPorId(s.getIdSessao()));
    }

    @Transactional
    void logoutTodasSessoes(String idUsuario) {
        sessaoRepository.revogarTodasDoUsuario(idUsuario);
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
        // Busca sempre filtrada por idUsuario: garante isolamento entre usuarios (multi-tenancy)
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

        // Não permitir salvar uma empresa duas vezes
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
        // Garante que a empresa referenciada tambem pertence ao usuario autenticado
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
    void excluir(String idUsuario, String idNota) {
        NotaEmpresa nota = notaEmpresaRepository.findByIdNotaEmpresaAndIdUsuario(idNota, idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Nota nao encontrada"));
        notaEmpresaRepository.delete(nota);
    }
}
