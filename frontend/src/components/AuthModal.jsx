import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { api } from '../services/api';
import { VertexLogo } from './Logo';
import { X, Lock, Mail, User, Check, AlertCircle, KeyRound, ArrowLeft, RefreshCw, Loader2 } from 'lucide-react';

export const AuthModal = ({ isOpen, onClose, initialMode = 'login', initialEmail = '' }) => {
  const [mode, setMode] = useState(initialMode); // 'login' | 'register' | 'verify'
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState(initialEmail);
  const [senha, setSenha] = useState('');
  const [confirmarSenha, setConfirmarSenha] = useState('');
  const [codigo, setCodigo] = useState('');
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);

  const { login, register, confirmRegister } = useAuth();
  const toast = useToast();

  // Sincroniza o modo e limpa os campos sempre que o modal abre ou initialMode muda
  useEffect(() => {
    if (isOpen) {
      setMode(initialMode);
      setErrors({});
      setNome('');
      setEmail(initialEmail || '');
      setSenha('');
      setConfirmarSenha('');
      setCodigo('');
    }
  }, [isOpen, initialMode, initialEmail]);

  if (!isOpen) return null;

  // Validações da anotação @SenhaForte
  const hasMinLength = senha.length >= 10 && senha.length <= 128;
  const hasUpper = /[A-Z]/.test(senha);
  const hasLower = /[a-z]/.test(senha);
  const hasNumber = /[0-9]/.test(senha);
  const hasSpecial = /[^A-Za-z0-9]/.test(senha);
  const isSenhaForteValida = hasMinLength && hasUpper && hasLower && hasNumber && hasSpecial;

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = {};

    if (mode === 'verify') {
      if (!codigo.trim()) errs.codigo = 'O código de verificação é obrigatório';
      else if (codigo.trim().length !== 6) errs.codigo = 'O código deve conter exatamente 6 dígitos';

      if (Object.keys(errs).length > 0) {
        setErrors(errs);
        return;
      }

      setLoading(true);
      try {
        await confirmRegister(email.trim().toLowerCase(), codigo.trim(), nome.trim());
        toast.success('E-mail verificado e cadastro concluído com sucesso!');
        onClose();
      } catch (err) {
        toast.error(err.message || 'Código inválido ou expirado');
      } finally {
        setLoading(false);
      }
      return;
    }

    if (!email.trim()) errs.email = 'E-mail é obrigatório';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errs.email = 'E-mail inválido';

    if (!senha) errs.senha = 'Senha é obrigatória';

    if (mode === 'register') {
      if (!nome.trim()) errs.nome = 'Nome é obrigatório';
      else if (nome.trim().length < 2 || nome.trim().length > 150) {
        errs.nome = 'Nome deve ter entre 2 e 150 caracteres';
      }

      if (!isSenhaForteValida) {
        errs.senha = 'A senha não atende a todos os requisitos de segurança';
      }

      if (!confirmarSenha.trim()) {
        errs.confirmarSenha = 'Confirme sua senha';
      } else if (senha !== confirmarSenha) {
        errs.confirmarSenha = 'As senhas não são iguais. Digite novamente para confirmar.';
        setConfirmarSenha('');
        toast.error('As senhas não são iguais! Por favor, redigite a senha.');
      }
    }

    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setLoading(true);
    try {
      if (mode === 'login') {
        await login(email, senha);
        toast.success('Login efetuado com sucesso');
        onClose();
      } else {
        const res = await register(nome, email, senha);
        toast.success(res?.mensagem || 'Código de verificação enviado para seu e-mail!');
        setCodigo('');
        setMode('verify');
      }
    } catch (err) {
      if (err.message && err.message.includes('EMAIL_NAO_VERIFICADO')) {
        toast.info('Seu e-mail ainda não foi verificado. Enviamos um código para ele.');
        setMode('verify');
      } else {
        toast.error(err.message || 'Erro na autenticação');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleReenviarCodigo = async () => {
    if (!email.trim()) return;
    setResending(true);
    try {
      const res = await api.auth.reenviarCodigoCadastro({ email: email.trim().toLowerCase() });
      toast.success(res?.mensagem || 'Novo código enviado para seu e-mail!');
      setCodigo('');
    } catch (err) {
      toast.error(err.message || 'Não foi possível reenviar o código');
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-vertex-dark/50 dark:bg-black/70 backdrop-blur-[2px] animate-in fade-in">
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal rounded-xl max-w-md w-full overflow-hidden transition-colors">
        {/* Header do Modal */}
        <div className="px-6 pt-5 pb-4 flex items-center justify-between border-b border-vertex-border-light dark:border-vertex-dark-border">
          <div>
            <VertexLogo className="h-6 w-auto mb-1" textClassName="text-sm font-semibold dark:text-white" />
            <h2 className="text-base font-bold text-vertex-dark dark:text-vertex-dark-text">
              {mode === 'verify' 
                ? 'Verificar E-mail'
                : mode === 'register' 
                  ? 'Criar Conta Vertex' 
                  : 'Trocar de Conta'}
            </h2>
            <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-0.5">
              {mode === 'verify'
                ? `Enviamos um código de 6 dígitos para ${email}`
                : mode === 'register'
                  ? 'Preencha os dados abaixo para cadastrar uma nova conta'
                  : 'Insira suas credenciais para acessar com outra conta'}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors self-start"
            title="Fechar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {mode === 'verify' ? (
            <div className="space-y-4">
              <div className="p-3 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/40 rounded-lg text-xs text-amber-800 dark:text-amber-300 flex items-start gap-2.5">
                <Mail className="w-4 h-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
                <div className="leading-relaxed">
                  Confira sua caixa de entrada no <strong>Gmail</strong> e digite o código de 6 dígitos para validar o email
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text mb-1.5 text-center">
                  Código de Verificação (6 dígitos)
                </label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    inputMode="numeric"
                    autoFocus
                    value={codigo}
                    onChange={(e) => {
                      const val = e.target.value.replace(/\D/g, '').slice(0, 6);
                      setCodigo(val);
                      if (errors.codigo) setErrors((prev) => ({ ...prev, codigo: '' }));
                    }}
                    placeholder="000000"
                    maxLength={6}
                    className={`w-full text-center text-xl tracking-[0.4em] font-mono py-3 rounded-lg border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                      errors.codigo ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                    }`}
                  />
                </div>
                {errors.codigo && <p className="text-[11px] text-red-500 mt-1 text-center">{errors.codigo}</p>}
              </div>

              <div className="flex items-center justify-between text-xs pt-1">
                <button
                  type="button"
                  onClick={() => setMode('register')}
                  className="flex items-center gap-1 text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text transition-colors"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  Corrigir e-mail
                </button>
                <button
                  type="button"
                  disabled={resending}
                  onClick={handleReenviarCodigo}
                  className="flex items-center gap-1 font-semibold text-vertex-orange hover:text-vertex-orange-hover transition-colors disabled:opacity-50"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${resending ? 'animate-spin' : ''}`} />
                  Reenviar código
                </button>
              </div>

              <button
                type="submit"
                disabled={loading || codigo.length !== 6}
                className="w-full py-2.5 rounded-lg bg-vertex-orange text-white text-xs font-semibold hover:bg-vertex-orange-hover transition-colors shadow-subtle disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Validando e criando conta...</span>
                  </>
                ) : (
                  <span>Confirmar Código e Concluir Cadastro</span>
                )}
              </button>
            </div>
          ) : (
            <>
              {mode === 'register' && (
                <div>
                  <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                    Nome Completo <span className="text-vertex-orange">*</span>
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      value={nome}
                      onChange={(e) => setNome(e.target.value)}
                      placeholder="Nome do operador ou gestor"
                      maxLength={150}
                      className={`w-full pl-9 pr-3 py-2 text-xs rounded border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                        errors.nome ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                      }`}
                    />
                  </div>
                  {errors.nome && <p className="text-[11px] text-red-500 mt-1">{errors.nome}</p>}
                </div>
              )}

              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                  E-mail Institucional <span className="text-vertex-orange">*</span>
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="seu.email@empresa.com"
                    maxLength={255}
                    className={`w-full pl-9 pr-3 py-2 text-xs rounded border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                      errors.email ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                    }`}
                  />
                </div>
                {errors.email && <p className="text-[11px] text-red-500 mt-1">{errors.email}</p>}
              </div>

              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                  Senha de Acesso <span className="text-vertex-orange">*</span>
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="password"
                    value={senha}
                    onChange={(e) => setSenha(e.target.value)}
                    placeholder="••••••••••••"
                    className={`w-full pl-9 pr-3 py-2 text-xs rounded border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                      errors.senha ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                    }`}
                  />
                </div>
                {errors.senha && <p className="text-[11px] text-red-500 mt-1">{errors.senha}</p>}
              </div>

              {mode === 'register' && (
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text">
                      Confirmar Senha <span className="text-vertex-orange">*</span>
                    </label>
                    {confirmarSenha && (
                      <span className={`text-[11px] font-medium flex items-center gap-1 ${
                        senha === confirmarSenha ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                      }`}>
                        {senha === confirmarSenha ? (
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
                    <Lock className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="password"
                      value={confirmarSenha}
                      onChange={(e) => {
                        setConfirmarSenha(e.target.value);
                        if (errors.confirmarSenha) setErrors((prev) => ({ ...prev, confirmarSenha: '' }));
                      }}
                      placeholder="••••••••••••"
                      className={`w-full pl-9 pr-8 py-2 text-xs rounded border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-colors ${
                        errors.confirmarSenha 
                          ? 'border-red-400 bg-red-50/20' 
                          : (confirmarSenha && senha === confirmarSenha)
                            ? 'border-emerald-500/80 bg-emerald-50/10'
                            : (confirmarSenha && senha !== confirmarSenha)
                              ? 'border-amber-400 bg-amber-50/10'
                              : 'border-vertex-border dark:border-vertex-dark-border'
                      }`}
                    />
                    {confirmarSenha && senha === confirmarSenha && (
                      <Check className="w-4 h-4 text-emerald-500 absolute right-3 top-1/2 -translate-y-1/2" />
                    )}
                  </div>
                  {errors.confirmarSenha && <p className="text-[11px] text-red-500 mt-1">{errors.confirmarSenha}</p>}
                </div>
              )}

              {/* Checklist de Validação Estrita de Senha Forte (@SenhaForte) */}
              {mode === 'register' && (
                <div className="p-3 bg-vertex-bg dark:bg-vertex-dark-surface rounded border border-vertex-border dark:border-vertex-dark-border space-y-1.5 text-[11px]">
                  <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text block mb-1">
                    Requisitos de Segurança da Senha:
                  </span>
                  <div className="grid grid-cols-2 gap-1 text-vertex-muted dark:text-vertex-dark-muted">
                    <span className={`flex items-center gap-1 ${hasMinLength ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                      <Check className={`w-3 h-3 ${hasMinLength ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-300 dark:text-slate-600'}`} />
                      Mín. 10 caracteres
                    </span>
                    <span className={`flex items-center gap-1 ${hasUpper ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                      <Check className={`w-3 h-3 ${hasUpper ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-300 dark:text-slate-600'}`} />
                      Letra maiúscula
                    </span>
                    <span className={`flex items-center gap-1 ${hasLower ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                      <Check className={`w-3 h-3 ${hasLower ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-300 dark:text-slate-600'}`} />
                      Letra minúscula
                    </span>
                    <span className={`flex items-center gap-1 ${hasNumber ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                      <Check className={`w-3 h-3 ${hasNumber ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-300 dark:text-slate-600'}`} />
                      Número
                    </span>
                    <span className={`flex items-center gap-1 col-span-2 ${hasSpecial ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                      <Check className={`w-3 h-3 ${hasSpecial ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-300 dark:text-slate-600'}`} />
                      Caractere especial (!@#$%&*)
                    </span>
                  </div>
                </div>
              )}

              <button
                type="submit"
                disabled={loading || (mode === 'register' && (!isSenhaForteValida || senha !== confirmarSenha))}
                className="w-full py-2.5 rounded bg-vertex-orange text-white text-xs font-semibold hover:bg-vertex-orange-hover transition-colors shadow-subtle disabled:opacity-50"
              >
                {loading ? 'Processando...' : mode === 'login' ? 'Entrar no VERTEX' : 'Criar Conta e Receber Código'}
              </button>
            </>
          )}
        </form>
      </div>
    </div>
  );
};

