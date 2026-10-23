import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api, tokenStorage } from '../services/api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [backendConnected, setBackendConnected] = useState(false);
  const [databaseConnected, setDatabaseConnected] = useState(false);
  const [healthStatus, setHealthStatus] = useState(null);

  const checkConnection = useCallback(async () => {
    const health = await api.checkHealth();
    const isOnline = !!health.available;
    const isDbConnected = health.database === 'CONNECTED';
    setBackendConnected(isOnline);
    setDatabaseConnected(isDbConnected);
    setHealthStatus(health);
    return isOnline && isDbConnected;
  }, []);

  const carregarUsuario = useCallback(async () => {
    try {
      const dados = await api.auth.me();
      if (dados) {
        setUser(dados);
        localStorage.setItem('vertex_usuario', JSON.stringify(dados));
        return dados;
      }
    } catch {
      // Falha silenciosa se desconectado
    }
    return null;
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      await checkConnection();

      const storedUser = localStorage.getItem('vertex_usuario');
      if (storedUser) {
        try {
          setUser(JSON.parse(storedUser));
        } catch {
          localStorage.removeItem('vertex_usuario');
        }
      }

      if (tokenStorage.getAccessToken()) {
        await carregarUsuario();
      }

      setLoading(false);
    };

    initAuth();

    const interval = setInterval(checkConnection, 30000);
    return () => clearInterval(interval);
  }, [checkConnection, carregarUsuario]);

  const login = async (email, senha) => {
    const res = await api.auth.login({ email, senha });
    try {
      const dadosUsuario = await api.auth.me();
      if (dadosUsuario) {
        setUser(dadosUsuario);
        localStorage.setItem('vertex_usuario', JSON.stringify(dadosUsuario));
        return res;
      }
    } catch {
      // Continua com fallback
    }
    const userData = {
      email,
      nome: email.split('@')[0],
    };
    setUser(userData);
    localStorage.setItem('vertex_usuario', JSON.stringify(userData));
    return res;
  };

  const register = async (nome, email, senha, aceitouTermos = true) => {
    const res = await api.auth.registrar({ nome, email, senha, aceitouTermos });
    return res;
  };

  const confirmRegister = async (email, codigo, nome) => {
    const res = await api.auth.verificarCodigoCadastro({ email, codigo });
    try {
      const dadosUsuario = await api.auth.me();
      if (dadosUsuario) {
        setUser(dadosUsuario);
        localStorage.setItem('vertex_usuario', JSON.stringify(dadosUsuario));
        return res;
      }
    } catch {
      // Continua com fallback
    }
    const userData = {
      nome: nome || email.split('@')[0],
      email,
    };
    setUser(userData);
    localStorage.setItem('vertex_usuario', JSON.stringify(userData));
    return res;
  };

  const alterarSenha = async (senhaAtual, novaSenha) => {
    const res = await api.auth.alterarSenha({ senhaAtual, novaSenha });
    return res;
  };

  const atualizarFoto = async (fotoUrl) => {
    const res = await api.auth.atualizarFoto(fotoUrl);
    if (res) {
      setUser(res);
      localStorage.setItem('vertex_usuario', JSON.stringify(res));
    }
    return res;
  };

  const uploadFoto = async (file, fallbackDataUrl = null) => {
    const res = await api.auth.uploadFoto(file, fallbackDataUrl);
    if (res) {
      setUser(res);
      localStorage.setItem('vertex_usuario', JSON.stringify(res));
    }
    return res;
  };

  const removerFoto = async () => {
    const res = await api.auth.removerFoto();
    if (res) {
      setUser(res);
      localStorage.setItem('vertex_usuario', JSON.stringify(res));
    }
    return res;
  };

  const deletarConta = async (senha) => {
    const res = await api.auth.deletarConta({ senha });
    tokenStorage.clear();
    localStorage.removeItem('vertex_usuario');
    setUser(null);
    return res;
  };

  const logout = async () => {
    await api.auth.logout();
    tokenStorage.clear();
    localStorage.removeItem('vertex_usuario');
    setUser(null);
  };

  const logoutTodasSessoes = async () => {
    await api.auth.logoutTodasSessoes();
    tokenStorage.clear();
    localStorage.removeItem('vertex_usuario');
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        backendConnected,
        databaseConnected,
        healthStatus,
        checkConnection,
        carregarUsuario,
        login,
        register,
        confirmRegister,
        alterarSenha,
        atualizarFoto,
        uploadFoto,
        removerFoto,
        deletarConta,
        logout,
        logoutTodasSessoes,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return context;
};
