package com.empresa.backend;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Principal do Spring Security representando o usuario autenticado via JWT. */
@Getter
class SecurityUser implements UserDetails {

    private final String idUsuario;
    private final String email;
    private final boolean emailVerificado;

    SecurityUser(Usuario usuario) {
        this.idUsuario = usuario.getIdUsuario();
        this.email = usuario.getEmail();
        this.emailVerificado = usuario.isEmailVerificado();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() { return null; } // autenticacao via JWT, senha nao e usada aqui

    @Override
    public String getUsername() { return idUsuario; }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}

/**
 * Emissao/validacao de access tokens JWT de curta duracao.
 * Refresh tokens NAO sao JWT: sao valores aleatorios opacos, com o hash
 * persistido na tabela SESSAO (rotacao a cada uso) — ver AuthService.
 */
@Component
class JwtService {

    @Value("${app.security.jwt.secret}")
    private String secret;

    @Value("${app.security.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET ausente ou fraco. Defina uma chave com no minimo 256 bits (32+ caracteres) via variavel de ambiente.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    String gerarAccessToken(String idUsuario) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(idUsuario)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    String extrairIdUsuario(String token) {
        return parseClaims(token).getSubject();
    }

    boolean tokenValido(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

/** Le o header Authorization: Bearer, valida o JWT e popula o SecurityContext. */
@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (jwtService.tokenValido(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String idUsuario = jwtService.extrairIdUsuario(token);
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(idUsuario);

            if (usuarioOpt.isPresent()) {
                SecurityUser securityUser = new SecurityUser(usuarioOpt.get());
                var authToken = new UsernamePasswordAuthenticationToken(
                        securityUser, null, securityUser.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}

/** Utilitario para obter o id do usuario autenticado a partir do contexto de seguranca. */
final class AutenticacaoUtil {

    private AutenticacaoUtil() {}

    static String idUsuarioAtual() {
        SecurityUser user = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getIdUsuario();
    }
}
