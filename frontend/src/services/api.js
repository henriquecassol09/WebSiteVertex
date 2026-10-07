/**
 * VERTEX - Cliente de API HTTP
 * Totalmente alinhado aos contratos e DTOs do backend Spring Boot 3.3.
 * ZERO DADOS INVENTADOS: Nenhuma empresa, pesquisa ou proposta fictícia.
 * Apenas consome o que o backend retorna.
 */

import { buscarEmpresasSemSiteRegiao } from './overpassService';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

// Limpeza garantida de resquícios de mocks no navegador do usuário
if (typeof window !== 'undefined') {
  localStorage.removeItem('vertex_empresas');
  localStorage.removeItem('vertex_pesquisas');
  localStorage.removeItem('vertex_notas');
  localStorage.removeItem('vertex_propostas');
}

// Gerenciamento de Tokens Reais
export const tokenStorage = {
  getAccessToken: () => localStorage.getItem('vertex_access_token'),
  getRefreshToken: () => localStorage.getItem('vertex_refresh_token'),
  setTokens: (access, refresh) => {
    if (access) localStorage.setItem('vertex_access_token', access);
    if (refresh) localStorage.setItem('vertex_refresh_token', refresh);
  },
  clear: () => {
    localStorage.removeItem('vertex_access_token');
    localStorage.removeItem('vertex_refresh_token');
  }
};

class ApiClient {
  constructor() {
    this.backendAvailable = false;
  }

  async checkHealth() {
    let res = null;
    let data = {};

    try {
      res = await fetch(`${API_BASE_URL}/health`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      });
      if (res.ok) {
        data = await res.json().catch(() => ({}));
      }
    } catch {
      // Fallback direto na porta 8080 caso o proxy local falhe
      try {
        res = await fetch(`http://127.0.0.1:8080/api/health`, {
          method: 'GET',
          headers: { 'Accept': 'application/json' },
        });
        if (res.ok) {
          data = await res.json().catch(() => ({}));
        }
      } catch {
        res = null;
      }
    }

    const isConnected = !!(res && res.ok);
    const dbStatus = data.database || (isConnected ? 'CONNECTED' : 'DISCONNECTED');
    this.backendAvailable = isConnected;
    this.healthData = data;

    return {
      available: isConnected,
      database: dbStatus,
      application: data.application || (isConnected ? 'UP' : 'DOWN'),
      error: isConnected ? null : 'Backend indisponível na porta 8080',
      raw: data,
    };
  }

  async request(endpoint, options = {}) {
    const accessToken = tokenStorage.getAccessToken();
    const headers = {
      'Content-Type': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {}),
      ...options.headers,
    };

    let response;
    try {
      response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
      });
    } catch {
      // Fallback direto no backend se o proxy do Vite tiver oscilado
      try {
        response = await fetch(`http://127.0.0.1:8080/api${endpoint}`, {
          ...options,
          headers,
        });
      } catch {
        this.backendAvailable = false;
        throw new Error('Servidor indisponível no momento. Certifique-se de que o backend está ativo.');
      }
    }

    // Se token expirou (401), tentar refresh apenas em rotas protegidas (não em /auth)
    if (response.status === 401 && !endpoint.startsWith('/auth') && tokenStorage.getRefreshToken() && !options._retry) {
      const refreshed = await this.auth.refresh(tokenStorage.getRefreshToken());
      if (refreshed?.accessToken) {
        return this.request(endpoint, { ...options, _retry: true });
      }
    }

    if (response.status === 204) {
      this.backendAvailable = true;
      return null;
    }

    // Leitura segura do corpo (evita quebrar quando o backend retorna corpo vazio em 403/401)
    const text = await response.text();
    let data = null;
    if (text && text.trim()) {
      try {
        data = JSON.parse(text);
      } catch {
        data = { message: text };
      }
    }

    // Erros HTTP onde o backend ESTÁ ATIVO e respondendo
    if (response.status === 401) {
      this.backendAvailable = true;
      const msg = data?.erro || data?.message || 'Credenciais inválidas ou sessão expirada';
      throw new Error(msg);
    }

    if (response.status === 403) {
      this.backendAvailable = true;
      const msg = data?.erro || data?.message || 'Acesso não autorizado. Por favor, realize login.';
      throw new Error(msg);
    }

    if (!response.ok) {
      this.backendAvailable = response.status < 500;
      let msg = data?.erro || data?.message;
      if (data?.detalhes && typeof data.detalhes === 'object') {
        const detalhesTexto = Object.values(data.detalhes).join('; ');
        if (detalhesTexto) msg = `${msg || 'Erro de validação'}: ${detalhesTexto}`;
      }
      throw new Error(msg || `Erro HTTP ${response.status}`);
    }

    this.backendAvailable = true;
    return data;
  }

  // ===== AUTENTICAÇÃO (/api/auth) =====
  auth = {
    registrar: async (dados) => {
      const res = await this.request('/auth/registrar', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
      tokenStorage.setTokens(res.accessToken, res.refreshToken);
      return res;
    },

    login: async (credenciais) => {
      const res = await this.request('/auth/login', {
        method: 'POST',
        body: JSON.stringify(credenciais),
      });
      tokenStorage.setTokens(res.accessToken, res.refreshToken);
      return res;
    },

    refresh: async (refreshToken) => {
      try {
        const res = await this.request('/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        });
        tokenStorage.setTokens(res.accessToken, res.refreshToken);
        return res;
      } catch {
        tokenStorage.clear();
        return null;
      }
    },

    logout: async () => {
      const refreshToken = tokenStorage.getRefreshToken();
      try {
        if (refreshToken) {
          await this.request('/auth/logout', {
            method: 'POST',
            body: JSON.stringify({ refreshToken }),
          });
        }
      } catch {
        // silencioso
      } finally {
        tokenStorage.clear();
      }
    },

    logoutTodasSessoes: async () => {
      try {
        await this.request('/auth/logout-todas-sessoes', {
          method: 'POST',
        });
      } catch {
        // silencioso
      } finally {
        tokenStorage.clear();
      }
    }
  };

  // ===== EMPRESAS (/api/empresas) =====
  empresas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/empresas?page=${page}&size=${size}`);
      } catch (err) {
        // Quando não há backend respondendo, retorna vazio (ZERO DADOS INVENTADOS)
        return {
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: page,
          size,
          first: true,
          last: true,
          empty: true,
        };
      }
    },

    buscar: async (id) => {
      return await this.request(`/empresas/${id}`);
    },

    criar: async (empresaReq) => {
      return await this.request('/empresas', {
        method: 'POST',
        body: JSON.stringify(empresaReq),
      });
    },

    atualizar: async (id, empresaReq) => {
      return await this.request(`/empresas/${id}`, {
        method: 'PUT',
        body: JSON.stringify(empresaReq),
      });
    },

    excluir: async (id) => {
      return await this.request(`/empresas/${id}`, {
        method: 'DELETE',
      });
    }
  };

  // ===== NOTAS DA EMPRESA (/api/empresas/{idEmpresa}/notas) =====
  notas = {
    listar: async (idEmpresa) => {
      try {
        return await this.request(`/empresas/${idEmpresa}/notas`);
      } catch {
        return [];
      }
    },

    criar: async (idEmpresa, notaReq) => {
      return await this.request(`/empresas/${idEmpresa}/notas`, {
        method: 'POST',
        body: JSON.stringify(notaReq),
      });
    },

    excluir: async (idEmpresa, idNota) => {
      return await this.request(`/empresas/${idEmpresa}/notas/${idNota}`, {
        method: 'DELETE',
      });
    }
  };

  // ===== PESQUISAS (/api/pesquisas) =====
  pesquisas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/pesquisas?page=${page}&size=${size}`);
      } catch (err) {
        return {
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: page,
          size,
          first: true,
          last: true,
          empty: true,
        };
      }
    },

    buscar: async (id) => {
      return await this.request(`/pesquisas/${id}`);
    },

    criar: async (pesquisaReq) => {
      return await this.request('/pesquisas', {
        method: 'POST',
        body: JSON.stringify(pesquisaReq),
      });
    },

    prospectarRegiao: async () => {
      return await buscarEmpresasSemSiteRegiao();
    },

    excluir: async (id) => {
      return await this.request(`/pesquisas/${id}`, {
        method: 'DELETE',
      });
    }
  };

  // ===== PROPOSTAS (/api/propostas) =====
  propostas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/propostas?page=${page}&size=${size}`);
      } catch (err) {
        return {
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: page,
          size,
          first: true,
          last: true,
          empty: true,
        };
      }
    },

    buscar: async (id) => {
      return await this.request(`/propostas/${id}`);
    },

    criar: async (propostaReq) => {
      return await this.request('/propostas', {
        method: 'POST',
        body: JSON.stringify(propostaReq),
      });
    },

    novaVersao: async (id, conteudoJson) => {
      return await this.request(`/propostas/${id}/versoes`, {
        method: 'POST',
        body: typeof conteudoJson === 'string' ? conteudoJson : JSON.stringify(conteudoJson),
      });
    }
  };
}

export const api = new ApiClient();
