# VERTEX - Sistema Integrado (TCC)

Monorepo estruturado para desenvolvimento em dupla, contendo Backend em **Spring Boot 3+ (Java 21)** e Frontend em **React 19 (Vite + TailwindCSS)**, integrado ao banco serverless **PostgreSQL no Neon**.

---

## 📁 Estrutura do Repositório

```text
vertex/
├── backend/                  # API REST Spring Boot 3.3 (Maven)
│   ├── src/main/java/        # Código Java (Controllers, Services, Security, Entities)
│   ├── src/main/resources/   # application.yml, migrations Flyway (SQL)
│   ├── pom.xml               # Dependências Maven
│   └── .env.example          # Modelo de variáveis de ambiente com Neon
├── frontend/                 # SPA React 19 (Vite + TailwindCSS)
│   ├── src/                  # Componentes, Contextos, Serviços de API
│   ├── package.json          # Dependências Node
│   ├── vite.config.js        # Configuração do Vite e Proxy reverso local
│   └── .env.example          # Modelo de variáveis de ambiente do Frontend
├── .gitignore                # Proteção contra credenciais e artefatos compilados
└── README.md                 # Guia geral de execução e integração
```

---

## 🚀 Como Executar o Projeto

### 1. Configurar o Banco de Dados (Neon PostgreSQL)

1. Crie uma conta gratuita em [neon.tech](https://neon.tech) e crie um projeto (ex: `vertex-db`).
2. No dashboard do Neon, acesse **Connection Details** e copie a connection string no formato **JDBC**.
3. No diretório `backend/`, copie `.env.example` para `.env`:
   ```bash
   cd backend
   cp .env.example .env
   ```
4. Preencha as credenciais no `.env`:
   ```env
   DB_URL=jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME=seu_usuario
   DB_PASSWORD=sua_senha
   JWT_SECRET=chave_secreta_com_mais_de_32_caracteres_vertex_tcc_2026
   ```

> [!IMPORTANT]
> O parâmetro `?sslmode=require` na URL é **obrigatório** para a conexão segura com o Neon.

---

### 2. Inicializar o Backend (Spring Boot)

```bash
cd backend
# No Windows (PowerShell):
.\mvnw.cmd spring-boot:run

# No Linux / macOS:
./mvnw spring-boot:run
```

O backend inicializará na porta **`8080`**. O Flyway criará automaticamente todas as tabelas no Neon na primeira inicialização.

#### Validar Endpoint de Diagnóstico (Health Check):
Acesse no navegador ou terminal:
```bash
curl http://localhost:8080/api/health
```
Resposta esperada (HTTP 200):
```json
{
  "application": "UP",
  "timestamp": "2026-09-28T...",
  "database": "CONNECTED",
  "databaseType": "PostgreSQL (Neon Serverless)"
}
```

---

### 3. Inicializar o Frontend (React + Vite)

Em um segundo terminal:

```bash
cd frontend
npm install
npm run dev
```

O frontend estará disponível em:
👉 **`http://localhost:3000`**

O cabeçalho do sistema inclui um indicador visual em tempo real do status de conexão com o backend e com o banco de dados Neon, permitindo revalidar a conectividade a qualquer momento com um clique.

---

## 🔒 Boas Práticas Implementadas

- **Credenciais Seguras:** Nenhuma senha ou segredo em código-fonte; tudo é injetado via variáveis de ambiente com validação de formato e comprimento.
- **Pool de Conexões Serverless:** Configuração sob medida do HikariCP com limites enxutos (`maximum-pool-size=5`) e timeouts adequados para a arquitetura serverless do Neon.
- **CORS Configurado:** Origens locais autorizadas (`http://localhost:3000` e `http://localhost:5173`) com headers e métodos padronizados.
- **Rate Limiting & JWT:** Proteção contra força bruta via Bucket4j e autenticação stateless com tokens rotacionados.
