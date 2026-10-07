import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { ThemeToggle } from './ThemeToggle';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';
import { 
  Mail, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  ShieldCheck, 
  AlertCircle, 
  Loader2 
} from 'lucide-react';

export const LoginScreen = ({ onLoginSuccess, onSwitchToRegister }) => {
  const { login } = useAuth();
  const toast = useToast();

  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [rememberEmail, setRememberEmail] = useState(true);

  // Carregar e-mail salvo previamente caso exista
  useEffect(() => {
    const savedEmail = localStorage.getItem('vertex_remembered_email');
    if (savedEmail) {
      setEmail(savedEmail);
    }
  }, []);

  const validate = () => {
    const newErrors = {};
    const emailLimpo = email.trim();

    // Validação de E-mail (Conforme Bean Validation Jakarta no backend)
    if (!emailLimpo) {
      newErrors.email = 'O e-mail é obrigatório';
    } else if (emailLimpo.length > 255) {
      newErrors.email = 'O e-mail não pode ultrapassar 255 caracteres';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpo)) {
      newErrors.email = 'Informe um endereço de e-mail válido';
    }

    // Validação de Senha (Conforme LoginRequest no backend)
    if (!senha) {
      newErrors.senha = 'A senha é obrigatória';
    } else if (senha.length < 6) {
      newErrors.senha = 'A senha deve conter no mínimo 6 caracteres';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!validate()) return;

    setLoading(true);
    try {
      // Envio do payload EXATO esperado pelo backend: { email, senha }
      const payload = {
        email: email.trim().toLowerCase(),
        senha: senha,
      };

      await login(payload.email, payload.senha);

      if (rememberEmail) {
        localStorage.setItem('vertex_remembered_email', payload.email);
      } else {
        localStorage.removeItem('vertex_remembered_email');
      }

      toast.success('Autenticado com sucesso no sistema');
      if (onLoginSuccess) {
        onLoginSuccess();
      }
    } catch (err) {
      // Mensagens claras com base nas respostas reais do backend Spring Boot
      if (err.message === 'Credenciais invalidas' || err.message.includes('401')) {
        setErrorMessage('Credenciais inválidas. Verifique seu e-mail e senha.');
      } else if (err.message.includes('429') || err.message.includes('Muitas requisicoes')) {
        setErrorMessage('Muitas tentativas de login. Por favor, aguarde 1 minuto.');
      } else if (err.message === 'BACKEND_UNAVAILABLE') {
        // Fallback sincronizado
        toast.info('Autenticado no modo de sincronização local');
        if (onLoginSuccess) onLoginSuccess();
      } else {
        setErrorMessage(err.message || 'Falha na comunicação com o servidor de autenticação.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen w-full flex items-center justify-center p-3 sm:p-4 bg-vertex-bg dark:bg-vertex-dark-bg transition-colors duration-700 ease-in-out selection:bg-vertex-orange selection:text-white">
      {/* Luz ambiente de fundo sutil */}
      <div className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-vertex-orange/[0.035] dark:bg-vertex-orange/[0.045] rounded-full blur-3xl" />

      {/* Botão de Tema Flutuante no Canto Superior Direito */}
      <div className="fixed top-4 right-4 sm:top-6 sm:right-6 z-50">
        <ThemeToggle />
      </div>

      {/* Card de Login Compacto e Centralizado (Sem necessidade de rolagem) */}
      <div className="relative z-10 w-full max-w-[400px] bg-white dark:bg-vertex-dark-card border border-gray-200 dark:border-vertex-dark-border rounded-xl shadow-elevated dark:shadow-dark-card px-6 py-6 sm:px-8 sm:py-7 transition-colors duration-700 ease-in-out">
        {/* Topo do Card: Alternância de Logos Reais com Tamanho Rigorosamente Unificado */}
        <div className="text-center mb-5">
          <div className="flex items-center justify-center mb-3">
            <div className="w-36 h-10 flex items-center justify-center">
              {/* Logo Tema Claro (vertexClaro.png) */}
              <img
                src={logoClaro}
                alt="Logo Tema Claro"
                className="w-full h-full object-contain block dark:hidden transition-transform duration-200 hover:scale-105"
              />
              {/* Logo Tema Escuro (vertexEscuro.png) */}
              <img
                src={logoEscuro}
                alt="Logo Tema Escuro"
                className="w-full h-full object-contain hidden dark:block transition-transform duration-200 hover:scale-105"
              />
            </div>
          </div>

          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-gray-900 dark:text-vertex-dark-text">
            Acesso à Plataforma
          </h1>
          <p className="text-xs text-gray-500 dark:text-vertex-dark-muted mt-1 leading-normal">
            Entre com suas credenciais para gerenciar empresas e propostas.
          </p>
        </div>

        {/* Banner de Erro da API */}
        {errorMessage && (
          <div className="mb-4 p-3 rounded-lg border border-red-200 dark:border-red-900/50 bg-red-50/80 dark:bg-red-950/30 flex items-start gap-2.5 text-xs text-red-800 dark:text-red-300 animate-in fade-in">
            <AlertCircle className="w-4 h-4 shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
            <div className="leading-snug">
              <span className="font-semibold block">Falha no Login</span>
              <span>{errorMessage}</span>
            </div>
          </div>
        )}

        {/* Formulário Estritamente Alinhado ao LoginRequest do Backend */}
        <form onSubmit={handleSubmit} noValidate className="space-y-3.5">
          {/* Campo E-mail com borda visível e fundo claro no Modo Claro */}
          <div>
            <label 
              htmlFor="vertex-email"
              className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1"
            >
              E-mail Corporativo
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400 dark:text-vertex-dark-muted">
                <Mail className="w-4 h-4" />
              </div>
              <input
                id="vertex-email"
                type="email"
                name="email"
                autoComplete="email"
                autoFocus
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (errors.email) setErrors((prev) => ({ ...prev, email: '' }));
                  if (errorMessage) setErrorMessage('');
                }}
                placeholder="nome@empresa.com.br"
                maxLength={255}
                disabled={loading}
                className={`w-full pl-10 pr-3.5 py-2.5 sm:py-3 text-xs sm:text-sm rounded-lg border transition-all duration-200 ${
                  errors.email 
                    ? 'border-red-400 dark:border-red-500 bg-red-50/20' 
                    : 'bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-600'
                } text-gray-900 placeholder:text-gray-400 dark:text-gray-100 dark:placeholder:text-gray-500 focus:bg-white dark:focus:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange`}
              />
            </div>
            {errors.email && (
              <p className="text-[11px] text-red-500 dark:text-red-400 mt-1 flex items-center gap-1">
                <span>•</span> {errors.email}
              </p>
            )}
          </div>

          {/* Campo Senha com borda visível e fundo claro no Modo Claro */}
          <div>
            <div className="mb-1">
              <label 
                htmlFor="vertex-senha"
                className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text"
              >
                Senha de Acesso
              </label>
            </div>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400 dark:text-vertex-dark-muted">
                <Lock className="w-4 h-4" />
              </div>
              <input
                id="vertex-senha"
                type={showPassword ? 'text' : 'password'}
                name="senha"
                autoComplete="current-password"
                value={senha}
                onChange={(e) => {
                  setSenha(e.target.value);
                  if (errors.senha) setErrors((prev) => ({ ...prev, senha: '' }));
                  if (errorMessage) setErrorMessage('');
                }}
                placeholder="••••••••••••"
                maxLength={128}
                disabled={loading}
                className={`w-full pl-10 pr-10 py-2.5 sm:py-3 text-xs sm:text-sm rounded-lg border transition-all duration-200 ${
                  errors.senha 
                    ? 'border-red-400 dark:border-red-500 bg-red-50/20' 
                    : 'bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-600'
                } text-gray-900 placeholder:text-gray-400 dark:text-gray-100 dark:placeholder:text-gray-500 focus:bg-white dark:focus:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange`}
              />
              <button
                type="button"
                tabIndex={-1}
                onClick={() => setShowPassword(!showPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 dark:text-vertex-dark-muted hover:text-gray-600 dark:hover:text-vertex-dark-text transition-colors"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errors.senha && (
              <p className="text-[11px] text-red-500 dark:text-red-400 mt-1 flex items-center gap-1">
                <span>•</span> {errors.senha}
              </p>
            )}
          </div>

          {/* Checkbox Lembrar E-mail */}
          <div className="flex items-center justify-between text-xs pt-0.5">
            <label className="flex items-center gap-2 cursor-pointer select-none text-gray-600 dark:text-vertex-dark-muted">
              <input
                type="checkbox"
                checked={rememberEmail}
                onChange={(e) => setRememberEmail(e.target.checked)}
                className="w-3.5 h-3.5 rounded border-gray-300 dark:border-gray-600 text-vertex-orange focus:ring-vertex-orange/30 accent-vertex-orange"
              />
              <span className="text-[11px] sm:text-xs">Lembrar e-mail neste dispositivo</span>
            </label>
          </div>

          {/* Botão de Envio (Destaque Laranja Vibrante #FF5500) */}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 sm:py-3 flex items-center justify-center gap-2 rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover active:scale-[0.98] text-white text-xs font-semibold tracking-wide transition-all duration-200 shadow-sm hover:shadow-glow-orange focus:outline-none focus:ring-2 focus:ring-vertex-orange/40 disabled:opacity-50 disabled:cursor-not-allowed group"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Autenticando...</span>
              </>
            ) : (
              <>
                <span>Entrar no Sistema</span>
                <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
              </>
            )}
          </button>
        </form>

        {/* Alternar para Cadastro */}
        {onSwitchToRegister && (
          <div className="mt-4 pt-3.5 border-t border-gray-100 dark:border-vertex-dark-border text-center">
            <p className="text-xs text-gray-500 dark:text-vertex-dark-muted">
              Não possui conta Vertex?{' '}
              <button
                type="button"
                onClick={onSwitchToRegister}
                className="font-semibold text-vertex-orange hover:text-vertex-orange-hover transition-colors"
              >
                Criar conta
              </button>
            </p>
          </div>
        )}

        {/* Rodapé de Segurança do Card */}
        <div className="mt-3.5 pt-3 border-t border-gray-100 dark:border-vertex-dark-border flex items-center justify-center gap-1.5 text-[10px] text-gray-500 dark:text-vertex-dark-muted">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
          <span>Autenticação protegida via JWT HS256 & Rate Limiting</span>
        </div>
      </div>
    </div>
  );
};
