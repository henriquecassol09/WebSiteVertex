import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { VertexLogo } from './Logo';
import { X, Lock, Mail, User, Check, AlertCircle } from 'lucide-react';

export const AuthModal = ({ isOpen, onClose, initialMode = 'login' }) => {
  const [mode, setMode] = useState(initialMode); // 'login' | 'register'
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const { login, register } = useAuth();
  const toast = useToast();

  // Sincroniza o modo e limpa os campos sempre que o modal abre ou initialMode muda
  useEffect(() => {
    if (isOpen) {
      setMode(initialMode);
      setErrors({});
      setNome('');
      setEmail('');
      setSenha('');
    }
  }, [isOpen, initialMode]);

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
      } else {
        await register(nome, email, senha);
        toast.success('Conta criada com sucesso');
      }
      onClose();
    } catch (err) {
      toast.error(err.message || 'Erro na autenticação');
    } finally {
      setLoading(false);
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
              {mode === 'register' ? 'Criar Conta Vertex' : 'Trocar de Conta'}
            </h2>
            <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-0.5">
              {mode === 'register'
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
            disabled={loading}
            className="w-full py-2.5 rounded bg-vertex-orange text-white text-xs font-semibold hover:bg-vertex-orange-hover transition-colors shadow-subtle disabled:opacity-50"
          >
            {loading ? 'Processando...' : mode === 'login' ? 'Entrar no VERTEX' : 'Criar Conta Vertex'}
          </button>
        </form>
      </div>
    </div>
  );
};
