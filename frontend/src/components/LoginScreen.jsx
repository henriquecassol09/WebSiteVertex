import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { api } from '../services/api';
import { ThemeToggle } from './ThemeToggle';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';
import { 
  Mail, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  ArrowLeft,
  ShieldCheck, 
  AlertCircle, 
  Loader2,
  KeyRound,
  Check,
  RefreshCw
} from 'lucide-react';

export const LoginScreen = ({ onLoginSuccess, onSwitchToRegister, onSwitchToVerify }) => {
  const { login } = useAuth();
  const toast = useToast();

  // 'login' | 'forgot_request' | 'forgot_reset'
  const [viewMode, setViewMode] = useState('login');

  // Estados de Login
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberEmail, setRememberEmail] = useState(true);

  // Estados de Recuperação de Senha
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotCodigo, setForgotCodigo] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmaNovaSenha, setConfirmaNovaSenha] = useState('');
  const [showNovaSenha, setShowNovaSenha] = useState(false);
  const [resendingCode, setResendingCode] = useState(false);

  // Estados de Feedback
  const [errors, setErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [unverifiedEmail, setUnverifiedEmail] = useState(null);
  const [loading, setLoading] = useState(false);

  // Carregar e-mail salvo previamente caso exista
  useEffect(() => {
    const savedEmail = localStorage.getItem('vertex_remembered_email');
    if (savedEmail) {
      setEmail(savedEmail);
    }
  }, []);

  // Validação de Senha Forte
  const hasMinLength = novaSenha.length >= 10 && novaSenha.length <= 128;
  const hasUpper = /[A-Z]/.test(novaSenha);
  const hasLower = /[a-z]/.test(novaSenha);
  const hasNumber = /[0-9]/.test(novaSenha);
  const hasSpecial = /[^A-Za-z0-9]/.test(novaSenha);
  const isNovaSenhaForte = hasMinLength && hasUpper && hasLower && hasNumber && hasSpecial;

  const validateLogin = () => {
    const newErrors = {};
    const emailLimpo = email.trim();

    if (!emailLimpo) {
      newErrors.email = 'O e-mail é obrigatório';
    } else if (emailLimpo.length > 255) {
      newErrors.email = 'O e-mail não pode ultrapassar 255 caracteres';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpo)) {
      newErrors.email = 'Informe um endereço de e-mail válido';
    }

    if (!senha) {
      newErrors.senha = 'A senha é obrigatória';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setUnverifiedEmail(null);

    if (!validateLogin()) return;

    setLoading(true);
    try {
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
      const msg = err.message || '';
      if (msg.includes('EMAIL_NAO_VERIFICADO') || msg.toLowerCase().includes('ainda não foi verificado')) {
        setUnverifiedEmail(email.trim().toLowerCase());
        setErrorMessage('Seu e-mail ainda não foi confirmado. Enviamos um código para sua caixa de entrada.');
      } else if (msg === 'Credenciais invalidas' || msg.includes('401')) {
        setErrorMessage('Credenciais inválidas. Verifique seu e-mail e senha.');
      } else if (msg.includes('429') || msg.includes('Muitas requisicoes')) {
        setErrorMessage('Muitas tentativas de login. Por favor, aguarde 1 minuto.');
      } else if (msg === 'BACKEND_UNAVAILABLE') {
        toast.info('Autenticado no modo de sincronização local');
        if (onLoginSuccess) onLoginSuccess();
      } else {
        setErrorMessage(msg || 'Falha na comunicação com o servidor de autenticação.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleForgotRequestSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    const errs = {};
    const emailLimpo = forgotEmail.trim();

    if (!emailLimpo) {
      errs.forgotEmail = 'O e-mail é obrigatório';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpo)) {
      errs.forgotEmail = 'Informe um e-mail válido';
    }

    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setLoading(true);
    try {
      const res = await api.auth.solicitarRecuperacaoSenha({ email: emailLimpo.toLowerCase() });
      toast.success(res?.mensagem || 'Código de recuperação enviado para seu e-mail!');
      setForgotCodigo('');
      setViewMode('forgot_reset');
    } catch (err) {
      toast.error(err.message || 'Falha ao solicitar código');
    } finally {
      setLoading(false);
    }
  };

  const handleForgotResetSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    const errs = {};

    if (!forgotCodigo.trim() || forgotCodigo.trim().length !== 6) {
      errs.forgotCodigo = 'Digite o código de 6 dígitos recebido';
    }

    if (!isNovaSenhaForte) {
      errs.novaSenha = 'A nova senha deve atender a todos os requisitos de segurança';
    }

    if (!confirmaNovaSenha.trim()) {
      errs.confirmaNovaSenha = 'Confirme a nova senha';
    } else if (novaSenha !== confirmaNovaSenha) {
      errs.confirmaNovaSenha = 'As senhas não são iguais. Digite novamente para confirmar.';
      setConfirmaNovaSenha('');
      toast.error('As senhas não são iguais! Por favor, redigite a senha.');
    }

    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setLoading(true);
    try {
      const res = await api.auth.redefinirSenha({
        email: forgotEmail.trim().toLowerCase(),
        codigo: forgotCodigo.trim(),
        novaSenha: novaSenha
      });
      toast.success(res?.mensagem || 'Senha alterada com sucesso! Faça login com a nova senha.');
      setEmail(forgotEmail.trim().toLowerCase());
      setViewMode('login');
      setForgotCodigo('');
      setNovaSenha('');
      setConfirmaNovaSenha('');
    } catch (err) {
      toast.error(err.message || 'Código inválido ou expirado');
    } finally {
      setLoading(false);
    }
  };

  const handleResendRecoveryCode = async () => {
    if (!forgotEmail.trim()) return;
    setResendingCode(true);
    try {
      const res = await api.auth.solicitarRecuperacaoSenha({ email: forgotEmail.trim().toLowerCase() });
      toast.success(res?.mensagem || 'Novo código enviado para seu e-mail!');
      setForgotCodigo('');
    } catch (err) {
      toast.error(err.message || 'Não foi possível reenviar o código');
    } finally {
      setResendingCode(false);
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

      {/* Card Principal */}
      <div className="relative z-10 w-full max-w-[420px] bg-white dark:bg-vertex-dark-card border border-gray-200 dark:border-vertex-dark-border rounded-xl shadow-elevated dark:shadow-dark-card px-6 py-6 sm:px-8 sm:py-7 transition-colors duration-700 ease-in-out">
        {/* Topo do Card com Logos */}
        <div className="text-center mb-5">
          <div className="flex items-center justify-center mb-3">
            <div className="w-36 h-10 flex items-center justify-center">
              <img
                src={logoClaro}
                alt="Logo Tema Claro"
                className="w-full h-full object-contain block dark:hidden transition-transform duration-200 hover:scale-105"
              />
              <img
                src={logoEscuro}
                alt="Logo Tema Escuro"
                className="w-full h-full object-contain hidden dark:block transition-transform duration-200 hover:scale-105"
              />
            </div>
          </div>

          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-gray-900 dark:text-vertex-dark-text">
            {viewMode === 'login' && 'Acesso à Plataforma'}
            {viewMode === 'forgot_request' && 'Recuperar Senha'}
            {viewMode === 'forgot_reset' && 'Nova Senha'}
          </h1>
          <p className="text-xs text-gray-500 dark:text-vertex-dark-muted mt-1 leading-normal">
            {viewMode === 'login' && 'Entre com suas credenciais para gerenciar empresas e propostas.'}
            {viewMode === 'forgot_request' && 'Digite seu e-mail para receber um código de 6 dígitos via Gmail.'}
            {viewMode === 'forgot_reset' && `Insira o código enviado para ${forgotEmail} e defina a nova senha.`}
          </p>
        </div>

        {/* Banner de Erro da API */}
        {errorMessage && (
          <div className="mb-4 p-3 rounded-lg border border-red-200 dark:border-red-900/50 bg-red-50/80 dark:bg-red-950/30 text-xs text-red-800 dark:text-red-300 animate-in fade-in space-y-2">
            <div className="flex items-start gap-2.5">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
              <div className="leading-snug">
                <span className="font-semibold block">Aviso</span>
                <span>{errorMessage}</span>
              </div>
            </div>
            {unverifiedEmail && onSwitchToVerify && (
              <button
                type="button"
                onClick={() => onSwitchToVerify(unverifiedEmail)}
                className="w-full py-1.5 px-3 rounded bg-red-600 hover:bg-red-700 text-white font-semibold text-center block transition-colors"
              >
                Digitar Código de Ativação
              </button>
            )}
          </div>
        )}

        {/* MODO 1: LOGIN */}
        {viewMode === 'login' && (
          <form onSubmit={handleLoginSubmit} noValidate className="space-y-3.5">
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

            {/* Linha abaixo da caixa de senha com Lembrar e-mail e Esqueceu a senha */}
            <div className="flex items-center justify-between text-xs pt-0.5">
              <label className="flex items-center gap-2 cursor-pointer select-none text-gray-600 dark:text-vertex-dark-muted">
                <input
                  type="checkbox"
                  checked={rememberEmail}
                  onChange={(e) => setRememberEmail(e.target.checked)}
                  className="w-3.5 h-3.5 rounded border-gray-300 dark:border-gray-600 text-vertex-orange focus:ring-vertex-orange/30 accent-vertex-orange"
                />
                <span className="text-[11px] sm:text-xs">Lembrar e-mail</span>
              </label>

              <button
                type="button"
                onClick={() => {
                  setForgotEmail(email);
                  setErrors({});
                  setErrorMessage('');
                  setViewMode('forgot_request');
                }}
                className="text-[11px] sm:text-xs text-vertex-orange hover:text-vertex-orange-hover font-semibold transition-colors"
              >
                Esqueceu a senha?
              </button>
            </div>

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
        )}

        {/* MODO 2: PEDIR CÓDIGO DE RECUPERAÇÃO */}
        {viewMode === 'forgot_request' && (
          <form onSubmit={handleForgotRequestSubmit} noValidate className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1">
                E-mail da sua Conta
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  autoFocus
                  value={forgotEmail}
                  onChange={(e) => {
                    setForgotEmail(e.target.value);
                    if (errors.forgotEmail) setErrors((prev) => ({ ...prev, forgotEmail: '' }));
                  }}
                  placeholder="nome@empresa.com.br"
                  className="w-full pl-10 pr-3.5 py-2.5 text-xs sm:text-sm rounded-lg border bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange"
                />
              </div>
              {errors.forgotEmail && <p className="text-[11px] text-red-500 mt-1">{errors.forgotEmail}</p>}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 sm:py-3 flex items-center justify-center gap-2 rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs font-semibold transition-all shadow-sm disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Enviando código...</span>
                </>
              ) : (
                <span>Enviar Código para o Gmail</span>
              )}
            </button>

            <button
              type="button"
              onClick={() => {
                setViewMode('login');
                setErrors({});
              }}
              className="w-full flex items-center justify-center gap-1.5 text-xs text-gray-500 dark:text-vertex-dark-muted hover:text-gray-800 dark:hover:text-vertex-dark-text transition-colors pt-1"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              Voltar ao Login
            </button>
          </form>
        )}

        {/* MODO 3: INSERIR CÓDIGO + NOVA SENHA */}
        {viewMode === 'forgot_reset' && (
          <form onSubmit={handleForgotResetSubmit} noValidate className="space-y-3.5">
            <div>
              <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1 text-center">
                Código de 6 dígitos
              </label>
              <div className="relative">
                <KeyRound className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  inputMode="numeric"
                  autoFocus
                  value={forgotCodigo}
                  onChange={(e) => {
                    const val = e.target.value.replace(/\D/g, '').slice(0, 6);
                    setForgotCodigo(val);
                    if (errors.forgotCodigo) setErrors((prev) => ({ ...prev, forgotCodigo: '' }));
                  }}
                  placeholder="000000"
                  maxLength={6}
                  className="w-full text-center text-xl tracking-[0.3em] font-mono py-2 rounded-lg border bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange"
                />
              </div>
              {errors.forgotCodigo && <p className="text-[11px] text-red-500 mt-1 text-center">{errors.forgotCodigo}</p>}
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1">
                Nova Senha
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type={showNovaSenha ? 'text' : 'password'}
                  value={novaSenha}
                  onChange={(e) => {
                    setNovaSenha(e.target.value);
                    if (errors.novaSenha) setErrors((prev) => ({ ...prev, novaSenha: '' }));
                  }}
                  placeholder="••••••••••••"
                  maxLength={128}
                  className="w-full pl-10 pr-10 py-2 text-xs sm:text-sm rounded-lg border bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange"
                />
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() => setShowNovaSenha(!showNovaSenha)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 dark:text-vertex-dark-muted hover:text-gray-600 transition-colors"
                >
                  {showNovaSenha ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {errors.novaSenha && <p className="text-[11px] text-red-500 mt-1">{errors.novaSenha}</p>}
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text">
                  Confirmar Nova Senha
                </label>
                {confirmaNovaSenha && (
                  <span className={`text-[11px] font-medium flex items-center gap-1 ${
                    novaSenha === confirmaNovaSenha ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                  }`}>
                    {novaSenha === confirmaNovaSenha ? (
                      <>
                        <Check className="w-3 h-3" />
                        Senhas iguais
                      </>
                    ) : (
                      <>
                        <AlertCircle className="w-3 h-3" />
                        Senhas diferentes
                      </>
                    )}
                  </span>
                )}
              </div>
              <div className="relative">
                <Lock className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type={showNovaSenha ? 'text' : 'password'}
                  value={confirmaNovaSenha}
                  onChange={(e) => {
                    setConfirmaNovaSenha(e.target.value);
                    if (errors.confirmaNovaSenha) setErrors((prev) => ({ ...prev, confirmaNovaSenha: '' }));
                  }}
                  placeholder="••••••••••••"
                  maxLength={128}
                  className={`w-full pl-10 pr-10 py-2 text-xs sm:text-sm rounded-lg border bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-colors ${
                    errors.confirmaNovaSenha 
                      ? 'border-red-500 bg-red-50/20' 
                      : (confirmaNovaSenha && novaSenha === confirmaNovaSenha)
                        ? 'border-emerald-500/80 bg-emerald-50/10'
                        : (confirmaNovaSenha && novaSenha !== confirmaNovaSenha)
                          ? 'border-amber-400 bg-amber-50/10'
                          : 'border-gray-300 dark:border-gray-700'
                  }`}
                />
                {confirmaNovaSenha && novaSenha === confirmaNovaSenha && (
                  <Check className="w-4 h-4 text-emerald-500 absolute right-3.5 top-1/2 -translate-y-1/2" />
                )}
              </div>
              {errors.confirmaNovaSenha && (
                <p className="text-[11px] text-red-500 mt-1 font-medium flex items-center gap-1">
                  <AlertCircle className="w-3 h-3 shrink-0" />
                  {errors.confirmaNovaSenha}
                </p>
              )}
            </div>

            {/* Checklist de Validação da Nova Senha */}
            <div className="p-2.5 bg-gray-50 dark:bg-gray-800/60 rounded border border-gray-200 dark:border-gray-700 space-y-1 text-[11px]">
              <span className="font-semibold text-gray-800 dark:text-gray-200 block mb-0.5">
                Requisitos da Nova Senha:
              </span>
              <div className="grid grid-cols-2 gap-1 text-gray-500 dark:text-gray-400">
                <span className={`flex items-center gap-1 ${hasMinLength ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3 h-3 ${hasMinLength ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-300 dark:text-gray-600'}`} />
                  Mín. 10 chars
                </span>
                <span className={`flex items-center gap-1 ${hasUpper ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3 h-3 ${hasUpper ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-300 dark:text-gray-600'}`} />
                  Maiúscula
                </span>
                <span className={`flex items-center gap-1 ${hasLower ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3 h-3 ${hasLower ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-300 dark:text-gray-600'}`} />
                  Minúscula
                </span>
                <span className={`flex items-center gap-1 ${hasNumber ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3 h-3 ${hasNumber ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-300 dark:text-gray-600'}`} />
                  Número
                </span>
                <span className={`flex items-center gap-1 col-span-2 ${hasSpecial ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3 h-3 ${hasSpecial ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-300 dark:text-gray-600'}`} />
                  Caractere especial (!@#$*)
                </span>
              </div>
            </div>

            <div className="flex items-center justify-between text-xs pt-1">
              <button
                type="button"
                onClick={() => setViewMode('forgot_request')}
                className="flex items-center gap-1 text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                Alterar e-mail
              </button>
              <button
                type="button"
                disabled={resendingCode}
                onClick={handleResendRecoveryCode}
                className="flex items-center gap-1 font-semibold text-vertex-orange hover:text-vertex-orange-hover transition-colors disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${resendingCode ? 'animate-spin' : ''}`} />
                Reenviar código
              </button>
            </div>

            <button
              type="submit"
              disabled={loading || forgotCodigo.length !== 6 || !isNovaSenhaForte || novaSenha !== confirmaNovaSenha}
              className="w-full py-2.5 sm:py-3 flex items-center justify-center gap-2 rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs font-semibold transition-all shadow-sm disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Salvando nova senha...</span>
                </>
              ) : (
                <span>Redefinir Senha e Voltar ao Login</span>
              )}
            </button>
          </form>
        )}

        {/* Alternar para Cadastro */}
        {viewMode === 'login' && onSwitchToRegister && (
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
