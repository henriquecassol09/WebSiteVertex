/**
 * VERTEX - Cliente de API HTTP
 * Totalmente alinhado aos contratos e DTOs do backend Spring Boot 3.3.
 * ZERO DADOS INVENTADOS: Nenhuma empresa, pesquisa ou proposta fictícia.
 * Apenas consome o que o backend retorna.
 */

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
    try {
      const res = await fetch(`${API_BASE_URL}/health`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      });
      const data = await res.json().catch(() => ({}));
      const isConnected = res.ok && data.database === 'CONNECTED';
      this.backendAvailable = res.ok;
      this.healthData = data;
      return {
        available: res.ok,
        database: data.database || (res.ok ? 'CONNECTED' : 'DOWN'),
        application: data.application || (res.ok ? 'UP' : 'DOWN'),
        error: data.error || null,
        raw: data,
      };
    } catch (err) {
      this.backendAvailable = false;
      this.healthData = null;
      return {
        available: false,
        database: 'DISCONNECTED',
        application: 'UNREACHABLE',
        error: err.message,
      };
    }
  }

  async request(endpoint, options = {}) {
    const accessToken = tokenStorage.getAccessToken();
    const headers = {
      'Content-Type': 'application/json',
      ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {}),
      ...options.headers,
    };

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
      });

      // Se token expirou (401), tentar refresh
      if (response.status === 401 && tokenStorage.getRefreshToken() && !options._retry) {
        const refreshed = await this.auth.refresh(tokenStorage.getRefreshToken());
        if (refreshed?.accessToken) {
          return this.request(endpoint, { ...options, _retry: true });
        }
      }

      if (response.status === 204) return null;

      if (response.status >= 500) {
        this.backendAvailable = false;
        throw new Error('BACKEND_UNAVAILABLE');
      }

      let data;
      try {
        data = await response.json();
      } catch {
        this.backendAvailable = false;
        throw new Error('BACKEND_UNAVAILABLE');
      }

      if (!response.ok) {
        throw new Error(data.erro || data.message || `Erro HTTP ${response.status}`);
      }

      this.backendAvailable = true;
      return data;
    } catch (err) {
      if (
        err.message === 'BACKEND_UNAVAILABLE' ||
        err.name === 'TypeError' ||
        err.name === 'SyntaxError' ||
        err.message.includes('Failed to fetch') ||
        err.message.includes('ECONNREFUSED')
      ) {
        this.backendAvailable = false;
        throw new Error('BACKEND_UNAVAILABLE');
      }
      throw err;
    }
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
