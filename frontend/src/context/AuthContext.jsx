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
      setLoading(false);
    };

    initAuth();

    const interval = setInterval(checkConnection, 30000);
    return () => clearInterval(interval);
  }, [checkConnection]);

  const login = async (email, senha) => {
    const res = await api.auth.login({ email, senha });
    const userData = {
      email,
      nome: email.split('@')[0],
    };
    setUser(userData);
    localStorage.setItem('vertex_usuario', JSON.stringify(userData));
    return res;
  };

  const register = async (nome, email, senha) => {
    const res = await api.auth.registrar({ nome, email, senha });
    return res;
  };

  const confirmRegister = async (email, codigo, nome) => {
    const res = await api.auth.verificarCodigoCadastro({ email, codigo });
    const userData = {
      nome: nome || email.split('@')[0],
      email,
    };
    setUser(userData);
    localStorage.setItem('vertex_usuario', JSON.stringify(userData));
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
        login,
        register,
        confirmRegister,
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
