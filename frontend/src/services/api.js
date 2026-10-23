const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

if (typeof window !== 'undefined') {
  localStorage.removeItem('vertex_empresas');
  localStorage.removeItem('vertex_pesquisas');
  localStorage.removeItem('vertex_notas');
  localStorage.removeItem('vertex_propostas');
}

// Armazenamento de tokens
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
      if (import.meta.env.DEV) {
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
      } else {
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
      error: isConnected ? null : (import.meta.env.DEV ? 'Backend indisponível na porta 8080' : 'Serviço temporariamente indisponível'),
      raw: data,
    };
  }

  async request(endpoint, options = {}) {
    const accessToken = tokenStorage.getAccessToken();
    const headers = {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      'X-CSRF-Protection': '1',
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
      if (import.meta.env.DEV) {
        try {
          response = await fetch(`http://127.0.0.1:8080/api${endpoint}`, {
            ...options,
            headers,
          });
        } catch {
          this.backendAvailable = false;
          throw new Error('Servidor indisponível no momento. Certifique-se de que o backend está ativo.');
        }
      } else {
        this.backendAvailable = false;
        throw new Error('Servidor indisponível no momento. Certifique-se de que o serviço está ativo.');
      }
    }

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

    const text = await response.text();
    let data = null;
    if (text && text.trim()) {
      try {
        data = JSON.parse(text);
      } catch {
        data = { message: text };
      }
    }

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

  // Autenticação
  auth = {
    registrar: async (dados) => {
      const res = await this.request('/auth/registrar', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
      if (res.accessToken) {
        tokenStorage.setTokens(res.accessToken, res.refreshToken);
      }
      return res;
    },

    verificarCodigoCadastro: async (dados) => {
      const res = await this.request('/auth/verificar-codigo-cadastro', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
      if (res.accessToken) {
        tokenStorage.setTokens(res.accessToken, res.refreshToken);
      }
      return res;
    },

    reenviarCodigoCadastro: async (dados) => {
      return await this.request('/auth/reenviar-codigo-cadastro', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
    },

    solicitarRecuperacaoSenha: async (dados) => {
      return await this.request('/auth/recuperar-senha', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
    },

    redefinirSenha: async (dados) => {
      return await this.request('/auth/redefinir-senha', {
        method: 'POST',
        body: JSON.stringify(dados),
      });
    },

    validarEmail: async (email) => {
      return await this.request(`/auth/validar-email?email=${encodeURIComponent(email)}`, {
        method: 'GET',
      });
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
      } finally {
        tokenStorage.clear();
      }
    },

    me: async () => {
      return await this.request('/auth/me', {
        method: 'GET',
      });
    },

    alterarSenha: async ({ senhaAtual, novaSenha }) => {
      return await this.request('/auth/alterar-senha', {
        method: 'POST',
        body: JSON.stringify({ senhaAtual, novaSenha }),
      });
    },

    deletarConta: async ({ senha } = {}) => {
      return await this.request('/auth/conta', {
        method: 'DELETE',
        body: JSON.stringify({ senha }),
      });
    },

    atualizarFoto: async (fotoUrl) => {
      return await this.request('/auth/foto', {
        method: 'PUT',
        body: JSON.stringify({ fotoUrl }),
      });
    },

    uploadFoto: async (file, fallbackDataUrl = null) => {
      const accessToken = tokenStorage.getAccessToken();
      const formData = new FormData();
      formData.append('file', file);

      const headers = {
        'X-Requested-With': 'XMLHttpRequest',
        'X-CSRF-Protection': '1',
        ...(accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {}),
      };

      try {
        let response;
        try {
          response = await fetch(`${API_BASE_URL}/auth/foto/upload`, {
            method: 'POST',
            headers,
            body: formData,
          });
        } catch {
          if (import.meta.env.DEV) {
            response = await fetch(`http://127.0.0.1:8080/api/auth/foto/upload`, {
              method: 'POST',
              headers,
              body: formData,
            });
          } else {
            throw new Error('Serviço indisponível no momento.');
          }
        }

        if (response.ok) {
          return await response.json();
        }

        if (fallbackDataUrl) {
          return await this.auth.atualizarFoto(fallbackDataUrl);
        }

        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.mensagem || errorData.message || 'Erro ao enviar foto.');
      } catch (err) {
        if (fallbackDataUrl) {
          try {
            return await this.auth.atualizarFoto(fallbackDataUrl);
          } catch (fallbackErr) {
            throw new Error(fallbackErr.message || err.message || 'Erro ao salvar foto.');
          }
        }
        throw err;
      }
    },

    removerFoto: async () => {
      return await this.request('/auth/foto', {
        method: 'DELETE',
      });
    }
  };

  // Empresas
  empresas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/empresas?page=${page}&size=${size}`);
      } catch {
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
    },

    consultarCnpj: async (cnpj) => {
      const limpo = String(cnpj).replace(/\D/g, '');
      return await this.request(`/empresas/consultar-cnpj/${limpo}`);
    }
  };

  // Notas
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

  // Pesquisas
  pesquisas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/pesquisas?page=${page}&size=${size}`);
      } catch {
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
      return await this.request('/pesquisas/prospectar-regiao', {
        method: 'POST',
      });
    },

    excluir: async (id) => {
      return await this.request(`/pesquisas/${id}`, {
        method: 'DELETE',
      });
    }
  };

  // Propostas
  propostas = {
    listar: async (page = 0, size = 20) => {
      try {
        return await this.request(`/propostas?page=${page}&size=${size}`);
      } catch {
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

  // Suporte Técnico
  suporte = {
    enviarChamado: async ({ email, nome, origem, descricaoErro, mensagemErroSistema }) => {
      return await this.request('/suporte/chamado', {
        method: 'POST',
        body: JSON.stringify({ email, nome, origem, descricaoErro, mensagemErroSistema }),
      });
    },
  };
}

export const api = new ApiClient();
