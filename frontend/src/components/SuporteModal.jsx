import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { 
  X, 
  LifeBuoy, 
  Send, 
  Loader2, 
  CheckCircle2, 
  Mail, 
  User, 
  ShieldAlert
} from 'lucide-react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';

export const SuporteModal = ({ 
  isOpen, 
  onClose, 
  origem = 'Suporte Geral', 
  emailInicial = '', 
  nomeInicial = '',
  erroInicial = ''
}) => {
  const toast = useToast();

  const [email, setEmail] = useState('');
  const [nome, setNome] = useState('');
  const [descricaoErro, setDescricaoErro] = useState('');
  const [mensagemErroSistema, setMensagemErroSistema] = useState('');
  const [loading, setLoading] = useState(false);
  const [enviadoSucesso, setEnviadoSucesso] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (isOpen) {
      setEmail(emailInicial || '');
      setNome(nomeInicial || '');
      setMensagemErroSistema(erroInicial || '');
      setDescricaoErro('');
      setEnviadoSucesso(false);
      setErrors({});
    }
  }, [isOpen, emailInicial, nomeInicial, erroInicial]);

  // Trava o scroll da página enquanto o modal estiver aberto (especialmente em mobile)
  useEffect(() => {
    if (!isOpen) return;
    const scrollOriginal = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = scrollOriginal;
    };
  }, [isOpen]);

  // Fechar com a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen && !loading) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, loading, onClose]);

  if (!isOpen) return null;

  const validarFormulario = () => {
    const errs = {};
    const emailLimpo = email.trim();

    if (!emailLimpo) {
      errs.email = 'Informe seu e-mail para retorno';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpo)) {
      errs.email = 'Informe um endereço de e-mail válido';
    }

    if (!descricaoErro.trim()) {
      errs.descricaoErro = 'Descreva o erro que está ocorrendo no site';
    } else if (descricaoErro.trim().length < 5) {
      errs.descricaoErro = 'Por favor, detalhe melhor o erro (mínimo de 5 caracteres)';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validarFormulario()) return;

    setLoading(true);
    try {
      await api.suporte.enviarChamado({
        email: email.trim().toLowerCase(),
        nome: nome.trim() || undefined,
        origem: origem,
        descricaoErro: descricaoErro.trim(),
        mensagemErroSistema: mensagemErroSistema.trim() || undefined,
      });

      setEnviadoSucesso(true);
      toast.success('Chamado de suporte enviado com sucesso!');
    } catch (err) {
      toast.error(err.message || 'Não foi possível enviar o chamado no momento.');
    } finally {
      setLoading(false);
    }
  };

  const modalContent = (
    <div 
      className="fixed inset-0 z-[9999] flex items-center justify-center p-3 sm:p-5 md:p-6 bg-black/70 dark:bg-black/85 backdrop-blur-sm overflow-y-auto animate-in fade-in duration-200"
      role="dialog"
      aria-modal="true"
      aria-labelledby="suporte-modal-titulo"
      onClick={(e) => {
        if (e.target === e.currentTarget && !loading) onClose();
      }}
    >
      <div 
        className="relative bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-2xl rounded-2xl w-full max-w-lg my-auto max-h-[calc(100dvh-1.5rem)] sm:max-h-[90vh] flex flex-col overflow-hidden transition-all duration-200 animate-in zoom-in-95"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Responsivo */}
        <div className="px-4 sm:px-6 py-3.5 sm:py-4 flex items-center justify-between border-b border-vertex-border-light dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/50 shrink-0">
          <div className="flex items-center gap-2.5 sm:gap-3 min-w-0 pr-2">
            <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-vertex-orange/10 dark:bg-vertex-orange/20 flex items-center justify-center text-vertex-orange shrink-0">
              <LifeBuoy className="w-4 h-4 sm:w-5 sm:h-5" />
            </div>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
                <h2 id="suporte-modal-titulo" className="text-sm sm:text-base font-bold text-vertex-dark dark:text-vertex-dark-text truncate">
                  Chamado de Suporte
                </h2>
                <span className="text-[10px] sm:text-[11px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-vertex-orange/10 text-vertex-orange border border-vertex-orange/20 whitespace-nowrap">
                  {origem}
                </span>
              </div>
              <p className="text-[11px] sm:text-xs text-vertex-muted dark:text-vertex-dark-muted truncate mt-0.5">
                Envie o erro do site para nossa equipe técnica
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="p-1.5 sm:p-2 rounded-lg text-vertex-muted hover:text-vertex-dark dark:text-vertex-dark-muted dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors cursor-pointer shrink-0"
            aria-label="Fechar modal de suporte"
          >
            <X className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
        </div>

        {/* Corpo do Modal com Scroll Interno Otimizado */}
        <div className="p-4 sm:p-6 flex-1 overflow-y-auto space-y-4 overscroll-contain">
          {enviadoSucesso ? (
            <div className="py-6 sm:py-8 text-center space-y-4 animate-in fade-in">
              <div className="w-14 h-14 sm:w-16 sm:h-16 rounded-full bg-emerald-100 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400 mx-auto flex items-center justify-center shadow-subtle">
                <CheckCircle2 className="w-8 h-8 sm:w-9 sm:h-9" />
              </div>
              <div className="space-y-1.5 px-2">
                <h3 className="text-base sm:text-lg font-bold text-gray-900 dark:text-vertex-dark-text">
                  Chamado Enviado com Sucesso!
                </h3>
                <p className="text-xs sm:text-sm text-gray-600 dark:text-vertex-dark-muted max-w-md mx-auto leading-relaxed">
                  O chamado e os detalhes técnicos foram encaminhados para a equipe de desenvolvimento. Responderemos e te notificaremos na caixa de entrada do seu email (<strong className="text-vertex-dark dark:text-vertex-dark-text break-all">{email}</strong>).
                </p>
              </div>

              <div className="pt-3">
                <button
                  type="button"
                  onClick={onClose}
                  className="w-full sm:w-auto px-6 py-2.5 min-h-[44px] rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs sm:text-sm font-semibold transition-all shadow-subtle cursor-pointer"
                >
                  Concluir e Fechar
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              {mensagemErroSistema && (
                <div className="p-3 sm:p-3.5 rounded-lg border border-red-200 dark:border-red-900/50 bg-red-50/70 dark:bg-red-950/25 text-xs text-red-700 dark:text-red-300 flex items-start gap-2.5">
                  <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-red-600 dark:text-red-400" />
                  <div className="min-w-0 flex-1">
                    <span className="font-semibold block mb-0.5">Erro detectado no site:</span>
                    <span className="break-words font-mono text-[11px] leading-relaxed block">{mensagemErroSistema}</span>
                  </div>
                </div>
              )}

              {/* E-mail de Retorno */}
              <div>
                <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1.5">
                  Seu E-mail para Retorno <span className="text-vertex-orange">*</span>
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
                      if (errors.email) setErrors((prev) => ({ ...prev, email: '' }));
                    }}
                    placeholder="seuemail@empresa.com.br"
                    maxLength={255}
                    disabled={loading}
                    className={`w-full pl-10 pr-3.5 min-h-[44px] py-2.5 text-xs sm:text-sm rounded-lg border transition-colors ${
                      errors.email
                        ? 'border-red-400 dark:border-red-500 bg-red-50/20'
                        : 'bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700'
                    } text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange`}
                  />
                </div>
                {errors.email && (
                  <p className="text-[11px] text-red-500 font-medium mt-1 flex items-center gap-1">
                    <span>•</span> {errors.email}
                  </p>
                )}
              </div>

              {/* Nome do Usuário */}
              <div>
                <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text mb-1.5">
                  Seu Nome (opcional)
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-gray-400 dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <input
                    type="text"
                    value={nome}
                    onChange={(e) => setNome(e.target.value)}
                    placeholder="Como prefere ser chamado"
                    maxLength={150}
                    disabled={loading}
                    className="w-full pl-10 pr-3.5 min-h-[44px] py-2.5 text-xs sm:text-sm rounded-lg border bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange"
                  />
                </div>
              </div>

              {/* Caixa para Descrever o Erro */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-xs font-semibold text-gray-800 dark:text-vertex-dark-text">
                    Descreva o erro que ocorreu <span className="text-vertex-orange">*</span>
                  </label>
                  <span className="text-[11px] text-gray-400 dark:text-vertex-dark-muted font-mono">
                    {descricaoErro.length}/5000
                  </span>
                </div>
                <div className="relative">
                  <textarea
                    rows={4}
                    value={descricaoErro}
                    onChange={(e) => {
                      setDescricaoErro(e.target.value);
                      if (errors.descricaoErro) setErrors((prev) => ({ ...prev, descricaoErro: '' }));
                    }}
                    maxLength={5000}
                    disabled={loading}
                    className={`w-full p-3.5 text-xs sm:text-sm rounded-lg border transition-colors ${
                      errors.descricaoErro
                        ? 'border-red-400 dark:border-red-500 bg-red-50/20'
                        : 'bg-gray-50 border-gray-300 dark:bg-gray-800 dark:border-gray-700'
                    } text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange resize-y min-h-[110px] max-h-[240px]`}
                  />
                </div>
                {errors.descricaoErro && (
                  <p className="text-[11px] text-red-500 font-medium mt-1 flex items-center gap-1">
                    <span>•</span> {errors.descricaoErro}
                  </p>
                )}
              </div>

              {/* Barra de Ações Responsiva */}
              <div className="pt-2 flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={loading}
                  className="w-full sm:w-auto min-h-[42px] px-5 py-2.5 text-xs sm:text-sm font-medium text-gray-600 dark:text-vertex-dark-muted hover:text-gray-900 dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface rounded-lg transition-colors cursor-pointer text-center"
                >
                  Cancelar
                </button>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full sm:w-auto min-h-[44px] px-6 py-2.5 rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover active:scale-[0.98] text-white text-xs sm:text-sm font-semibold transition-all shadow-subtle flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Enviando chamado...</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4" />
                      <span>Enviar Chamado de Suporte</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );

  return typeof document !== 'undefined'
    ? createPortal(modalContent, document.body)
    : modalContent;
};
