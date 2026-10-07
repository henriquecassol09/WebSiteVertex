# VERTEX — Front-end Web (React + Tailwind CSS)

Interface corporativa de alta fidelidade e conversão construída em React e Tailwind CSS, estritamente mapeada sobre os contratos do backend Spring Boot 3.3 / PostgreSQL da **VERTEX**.

---

## 💎 Identidade Visual VERTEX

- **Estética**: Minimalista, corporativa e afiada (*sharp edge*), alinhada ao corte geométrico da logo **VERTEX**.
- **Paleta de Cores**:
  - `Laranja Vibrante`: `#FF5500` (elementos de destaque, CTAs principais, micro-interações).
  - `Cinza-Chumbo / Charcoal`: `#111315` / `#1A1D20` (tipografia de títulos e elementos estruturais).
  - `Texto Principal`: `#3A3F45` / `#1A1D20` (leitura confortável de alta densidade).
  - `Fundo Minimalista`: `#F8F9FA` / `#FFFFFF` (off-white limpo com foco no whitespace e respiro).
  - `Bordas Sutis`: `#E5E8EB` (definição precisa sem poluição visual).
- **Tipografia**: Família *Inter* sem serifa, estruturada em hierarquia de pesos (Bold/Semibold para cabeçalhos e tabelas, Regular para texto, Monospace para UUIDs, tokens e timestamps ISO).

---

## 🎯 Conformidade Estrita aos Dados do Backend (Zero Dados Inventados)

Nenhum campo fictício foi adicionado. A UI renderiza exatamente os contratos definidos nos DTOs Java e nas tabelas SQL:

| Módulo / Rota Backend | Contrato de Entrada / DTO | Propriedades Exibidas na UI |
|---|---|---|
| `/api/auth/registrar` | `RegistrarRequest` (nome, email, senha) | Validação `@SenhaForte` (mín. 10 chars, maiúscula, minúscula, número e especial) |
| `/api/auth/login` | `LoginRequest` (email, senha) | Retorno `AuthResponse` com Bearer JWT e Refresh Token |
| `/api/auth/logout-todas-sessoes` | Revogação de sessões ativas | Ação de segurança acessível no menu do usuário |
| `/api/empresas` | `EmpresaRequest` & `EmpresaResponse` | `idEmpresa`, `nome`, `categoria`, `cidade`, `estado`, `endereco`, `telefone`, `email`, `site`, `descricao`, `idPesquisa`, `criadoEm`, `atualizadoEm` |
| `/api/empresas/{id}/notas` | `NotaRequest` & `NotaEmpresa` | `idNotaEmpresa`, `conteudo` (máx 4000), `criadoEm`, `atualizadoEm` |
| `/api/pesquisas` | `PesquisaRequest` & `PesquisaResponse` | `idPesquisa`, `consulta` (máx 500), `regiao` (máx 150), `status`, `criadoEm` |
| `/api/propostas` | `PropostaRequest` & `PropostaResponse` | `idProposta`, `idEmpresa`, `titulo`, `status`, `resumo`, `validoAte`, `criadoEm`, `atualizadoEm` |
| `/api/propostas/{id}/versoes` | Conteúdo JSON da nova versão | Publicação de nova versão incrementada no backend |

---

## 🚀 Como Rodar o Front-end

```bash
cd vertex/frontend
npm install
npm run dev
```

A aplicação iniciará na porta `http://localhost:3000` (configurada no `vite.config.js` com proxy automático transparente para `http://localhost:8080/api`).

### Build de Produção
```bash
npm run build
npm run preview
```
