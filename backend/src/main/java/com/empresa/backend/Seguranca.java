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

// Usuário autenticado (Spring Security)
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
    public String getPassword() { return null; }

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

// Emissão e validação de tokens JWT
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

    Instant extrairExpiracao(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            return exp != null ? exp.toInstant() : Instant.now();
        } catch (Exception e) {
            return Instant.now();
        }
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

// Filtro de autenticação JWT
@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final AccessControlDefense accessControlDefense;

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

        if (accessControlDefense.isTokenRevogado(token)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

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

// Utilitário de autenticação
final class AutenticacaoUtil {

    private AutenticacaoUtil() {}

    static String idUsuarioAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SecurityUser user)) {
            throw new AutenticacaoException("Acesso negado: sessao nao autenticada.");
        }
        return user.getIdUsuario();
    }
}
