# Backend — Spring Boot 3.3 / Java 21

Backend REST para o schema anexado (USUARIO, SESSAO, CONTA, VERIFICACAO,
PESQUISAS, EMPRESAS, PROPOSTAS, VERSOES_PROPOSTA, NOTAS_EMPRESA), construído
com foco em segurança.

## Stack

- Java 21, Spring Boot 3.3
- Spring Security 6 (stateless, JWT)
- Spring Data JPA + Hibernate 6
- PostgreSQL + Flyway (migrações versionadas — `ddl-auto=validate`, nunca `update`)
- jjwt 0.12 (JWT)
- Bucket4j (rate limiting)
- Bean Validation (Jakarta Validation)

## Como rodar localmente

```bash
cp .env.example .env
# edite o .env com as credenciais do Neon e gere um JWT_SECRET forte:
openssl rand -base64 48

# Executar a aplicação (o banco conecta diretamente no Neon via .env):
./mvnw spring-boot:run
```

A aplicação roda em `http://localhost:8080`. O Flyway cria o schema
automaticamente na primeira execução (`V1__schema_inicial.sql`).

> **Nota:** este projeto foi montado neste ambiente sem acesso ao Maven
> Central, então não foi possível rodar `mvn compile` aqui. Revise o `pom.xml`
> e rode `mvn clean verify` no seu ambiente antes do primeiro deploy.

## Estrutura de arquivos

Código consolidado em **um único pacote** (`com.empresa.backend`) e **10
arquivos Java**, agrupando classes relacionadas no mesmo arquivo (padrão
válido em Java: várias classes/interfaces por arquivo, desde que só uma seja
`public` e o nome do arquivo bata com ela):

| Arquivo | Conteúdo |
|---|---|
| `BackendApplication.java` | classe principal (`main`) |
| `Entities.java` | as 9 entidades JPA (Usuario, Sessao, Conta, Verificacao, Pesquisa, Empresa, Proposta, VersaoProposta, NotaEmpresa) |
| `Repositories.java` | todos os `JpaRepository` |
| `Dtos.java` | todos os records de request/response |
| `Seguranca.java` | `JwtService`, `JwtAuthenticationFilter`, `SecurityUser`, `AutenticacaoUtil` |
| `Config.java` | `SecurityConfig`, `RateLimitFilter`, `LimpezaSessoesScheduler` |
| `Util.java` | validador de senha forte (`@SenhaForte`) e `HashUtil` |
| `Services.java` | `AuthService` e os services de negócio |
| `Controllers.java` | todos os `@RestController` |
| `Exceptions.java` | exceções de domínio + handler global |

Se o projeto crescer bastante, o próximo passo natural é voltar a separar em
pacotes (`entity`, `repository`, `service`...) — mas para o tamanho atual,
essa organização compacta funciona bem e reduz a navegação entre arquivos.

## Medidas de segurança implementadas

**Autenticação e sessão**
- Senhas com hash BCrypt (custo 12), nunca armazenadas em texto puro.
- Access token JWT (HS256) de vida curta (15 min por padrão), assinado com
  chave de no mínimo 256 bits vinda de variável de ambiente (`JWT_SECRET`) —
  a aplicação recusa subir se a chave for fraca ou ausente.
- Refresh token opaco (aleatório, 384 bits), **não é JWT**: apenas o hash
  SHA-256 é persistido na tabela `SESSAO`. O valor puro só existe na resposta
  ao cliente, uma única vez.
- Rotação de refresh token a cada uso (a sessão antiga é sempre revogada);
  reutilizar um refresh token já usado invalida a sessão.
- Endpoint de logout e "logout de todas as sessões" (revoga todos os
  refresh tokens do usuário — útil em caso de comprometimento).
- Mensagens de erro de login/registro deliberadamente genéricas, para não
  permitir enumeração de e-mails cadastrados.
- Senha forte obrigatória no registro (mínimo 10 caracteres, maiúscula,
  minúscula, número e caractere especial), validada com anotação customizada.

**Autorização e isolamento de dados (multi-tenancy)**
- Todo recurso de negócio (`PESQUISAS`, `EMPRESAS`, `PROPOSTAS`,
  `VERSOES_PROPOSTA`, `NOTAS_EMPRESA`) é sempre buscado filtrando por
  `ID_USUARIO` do usuário autenticado extraído do JWT — nunca por um id
  vindo do corpo/query da requisição. Isso impede um usuário de acessar
  dados de outro (IDOR).
- Criar uma proposta ou nota exige que a empresa referenciada também
  pertença ao usuário autenticado.

**Camada HTTP**
- Sessão stateless (`SessionCreationPolicy.STATELESS`) — sem `JSESSIONID`.
- CORS restrito a uma allowlist configurável (`CORS_ALLOWED_ORIGINS`), sem
  wildcard `*`.
- CSRF desabilitado de forma segura (API stateless sem cookies de sessão
  para autenticar, então não se aplica).
- Rate limiting por IP: limite mais restrito em `/api/auth/**` (proteção
  contra força bruta / credential stuffing) e um limite geral para as
  demais rotas.
- Cabeçalhos de segurança: `Strict-Transport-Security` (HSTS com preload),
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`.

**Dados e erros**
- Todas as consultas passam por JPA/Hibernate com parâmetros nomeados —
  sem concatenação de SQL, eliminando SQL injection.
- Handler global de exceções: nunca expõe stack trace, mensagem interna de
  banco ou detalhe de implementação ao cliente; tudo é logado no servidor.
- Validação de entrada em profundidade (`@Valid` + Bean Validation) em
  todos os endpoints que recebem corpo.
- Campos sensíveis (`SENHA`, tokens) nunca retornam em nenhuma resposta da
  API — os DTOs de resposta são explícitos, sem serializar a entidade
  diretamente.
- Job agendado diário para expurgar sessões expiradas da tabela `SESSAO`.

**Configuração/produção**
- `application-prod.yml` habilita TLS (`server.ssl`) — nunca servir HTTP
  puro com dados sensíveis.
- `ddl-auto: validate` — o schema é controlado exclusivamente por
  migrações Flyway versionadas, nunca gerado automaticamente por Hibernate.
- Segredos (senha do banco, `JWT_SECRET`) vêm de variáveis de ambiente,
  nunca hardcoded no repositório.
- Actuator expõe apenas `/health` e `/info`, sem detalhes internos.

## Recomendações para produção (fora do escopo deste código)

- Colocar a API atrás de um WAF/API Gateway.
- Rate limiting distribuído (Redis) se houver múltiplas instâncias.
- Rotação periódica do `JWT_SECRET` com suporte a múltiplas chaves ativas.
- 2FA/MFA para contas sensíveis.
- Auditoria (log estruturado de ações sensíveis: login, exclusão, alteração
  de proposta).
- Backups automatizados e criptografados do Postgres.
- Verificação de e-mail (a tabela `VERIFICACAO` já está modelada para isso;
  este MVP não implementou o fluxo de envio de e-mail).

## Endpoints principais

```
POST /api/auth/registrar
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/logout-todas-sessoes   (autenticado)

GET    /api/pesquisas
POST   /api/pesquisas
GET    /api/pesquisas/{id}

GET    /api/empresas
POST   /api/empresas
GET    /api/empresas/{id}
PUT    /api/empresas/{id}
DELETE /api/empresas/{id}

GET    /api/empresas/{idEmpresa}/notas
POST   /api/empresas/{idEmpresa}/notas
DELETE /api/empresas/{idEmpresa}/notas/{idNota}

GET  /api/propostas
POST /api/propostas
GET  /api/propostas/{id}
POST /api/propostas/{id}/versoes
```

Todas as rotas exceto `/api/auth/registrar`, `/api/auth/login`,
`/api/auth/refresh` e `/actuator/health` exigem
`Authorization: Bearer <access_token>`.
