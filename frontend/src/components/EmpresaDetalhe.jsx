import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  X, 
  Building2, 
  MapPin, 
  Phone, 
  Mail, 
  Globe, 
  Calendar, 
  FileText, 
  Plus, 
  Trash2, 
  Clock, 
  ArrowUpRight,
  Send,
  MessageSquare
} from 'lucide-react';

export const EmpresaDetalhe = ({ empresa, onClose, onEdit, onNovaProposta }) => {
  const [notas, setNotas] = useState([]);
  const [loadingNotas, setLoadingNotas] = useState(false);
  const [novaNota, setNovaNota] = useState('');
  const [salvandoNota, setSalvandoNota] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (empresa?.idEmpresa) {
      carregarNotas();
    }
  }, [empresa?.idEmpresa]);

  // Fechar modal ao pressionar a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const carregarNotas = async () => {
    setLoadingNotas(true);
    try {
      const data = await api.notas.listar(empresa.idEmpresa);
      setNotas(data || []);
    } catch (err) {
      toast.error('Erro ao carregar notas da empresa');
    } finally {
      setLoadingNotas(false);
    }
  };

  const handleCriarNota = async (e) => {
    e.preventDefault();
    if (!novaNota.trim()) return;

    if (novaNota.length > 4000) {
      toast.error('Nota não pode exceder 4000 caracteres');
      return;
    }

    setSalvandoNota(true);
    try {
      const notaCriada = await api.notas.criar(empresa.idEmpresa, { conteudo: novaNota.trim() });
      setNotas([notaCriada, ...notas]);
      setNovaNota('');
      toast.success('Nota registrada com sucesso');
    } catch (err) {
      toast.error('Erro ao salvar nota');
    } finally {
      setSalvandoNota(false);
    }
  };

  const handleExcluirNota = async (idNota) => {
    if (!confirm('Deseja realmente excluir esta nota?')) return;

    try {
      await api.notas.excluir(empresa.idEmpresa, idNota);
      setNotas(notas.filter((n) => n.idNotaEmpresa !== idNota));
      toast.success('Nota removida');
    } catch (err) {
      toast.error('Erro ao remover nota');
    }
  };

  if (!empresa) return null;

  const formatDate = (isoString) => {
    if (!isoString) return '-';
    try {
      return new Date(isoString).toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-end bg-black/50 dark:bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-white dark:bg-vertex-dark-card border-l border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal w-full max-w-2xl h-full flex flex-col overflow-hidden animate-in slide-in-from-right duration-250 transition-colors">
        {/* Top Header */}
        <div className="flex items-center justify-between px-6 py-4.5 border-b border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-vertex-charcoal text-white flex items-center justify-center font-bold text-sm shadow-subtle">
              {empresa.nome.charAt(0).toUpperCase()}
            </div>
            <div>
              <h2 className="text-base font-semibold text-vertex-dark dark:text-vertex-dark-text leading-tight">
                {empresa.nome}
              </h2>
              <div className="flex items-center gap-2 mt-0.5">
                {empresa.categoria && (
                  <span className="text-[11px] font-medium text-vertex-orange bg-vertex-orange-light dark:bg-vertex-orange/10 px-2 py-0.5 rounded">
                    {empresa.categoria}
                  </span>
                )}
                <span className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
                  {empresa.idEmpresa}
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => onEdit(empresa)}
              className="h-8 px-3 text-xs font-medium rounded-md border border-vertex-border dark:border-vertex-dark-border text-vertex-dark dark:text-vertex-dark-text hover:bg-slate-50 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150"
            >
              Editar
            </button>
            <button
              onClick={() => onNovaProposta(empresa)}
              className="flex items-center gap-1.5 h-8 px-3.5 text-xs font-semibold rounded-md bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-95 transition-all duration-200 shadow-sm hover:shadow-glow-orange"
            >
              <FileText className="w-3.5 h-3.5" />
              <span>Gerar Proposta</span>
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150 ml-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Scrollable Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Seção 1: Dados Cadastrais */}
          <div className="bg-vertex-bg/50 dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded p-4 space-y-3">
            <h3 className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
              Dados da Empresa
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Localização:</span>
                <span className="font-medium text-vertex-dark dark:text-vertex-dark-text flex items-center gap-1.5 mt-0.5">
                  <MapPin className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
                  {[empresa.cidade, empresa.estado].filter(Boolean).join(' - ') || 'Não informado'}
                </span>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Telefone:</span>
                <span className="font-medium text-vertex-dark dark:text-vertex-dark-text flex items-center gap-1.5 mt-0.5">
                  <Phone className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
                  {empresa.telefone ? (
                    <a href={`tel:${empresa.telefone}`} className="hover:text-vertex-orange transition-colors">
                      {empresa.telefone}
                    </a>
                  ) : (
                    'Não informado'
                  )}
                </span>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">E-mail:</span>
                <span className="font-medium text-vertex-dark dark:text-vertex-dark-text flex items-center gap-1.5 mt-0.5 truncate">
                  <Mail className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
                  {empresa.email ? (
                    <a href={`mailto:${empresa.email}`} className="hover:text-vertex-orange transition-colors truncate">
                      {empresa.email}
                    </a>
                  ) : (
                    'Não informado'
                  )}
                </span>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Website:</span>
                <span className="font-medium text-vertex-dark dark:text-vertex-dark-text flex items-center gap-1.5 mt-0.5 truncate">
                  <Globe className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
                  {empresa.site ? (
                    <a
                      href={empresa.site.startsWith('http') ? empresa.site : `https://${empresa.site}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-vertex-orange hover:underline flex items-center gap-1 truncate"
                    >
                      <span>{empresa.site}</span>
                      <ArrowUpRight className="w-3 h-3 shrink-0" />
                    </a>
                  ) : (
                    'Não informado'
                  )}
                </span>
              </div>

              {empresa.endereco && (
                <div className="sm:col-span-2">
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Endereço Completo:</span>
                  <span className="font-medium text-vertex-dark dark:text-vertex-dark-text mt-0.5 block">
                    {empresa.endereco}
                  </span>
                </div>
              )}

              {empresa.idPesquisa && (
                <div className="sm:col-span-2">
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">ID Pesquisa Relacionada:</span>
                  <span className="font-mono text-[11px] text-vertex-body dark:text-vertex-dark-muted mt-0.5 block">
                    {empresa.idPesquisa}
                  </span>
                </div>
              )}
            </div>

            {/* Descrição */}
            {empresa.descricao && (
              <div className="pt-2 border-t border-vertex-border/60 dark:border-vertex-dark-border">
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] mb-1">Descrição:</span>
                <p className="text-xs text-vertex-body dark:text-vertex-dark-text leading-relaxed whitespace-pre-wrap">
                  {empresa.descricao}
                </p>
              </div>
            )}

            {/* Timestamps */}
            <div className="pt-2 border-t border-vertex-border/60 dark:border-vertex-dark-border flex flex-wrap gap-4 text-[11px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
              <span className="flex items-center gap-1">
                <Calendar className="w-3 h-3" /> Criado em: {formatDate(empresa.criadoEm)}
              </span>
              <span className="flex items-center gap-1">
                <Clock className="w-3 h-3" /> Atualizado em: {formatDate(empresa.atualizadoEm)}
              </span>
            </div>
          </div>

          {/* Seção 2: NOTAS DA EMPRESA (/api/empresas/{idEmpresa}/notas) */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MessageSquare className="w-4 h-4 text-vertex-orange" />
                <h3 className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
                  Notas e Histórico de Acompanhamento
                </h3>
              </div>
              <span className="text-[11px] font-mono text-vertex-muted dark:text-vertex-dark-muted">
                {notas.length} {notas.length === 1 ? 'nota' : 'notas'}
              </span>
            </div>

            {/* Formulário para registrar nova nota */}
            <form onSubmit={handleCriarNota} className="bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded p-3 space-y-2">
              <textarea
                rows={3}
                value={novaNota}
                onChange={(e) => setNovaNota(e.target.value)}
                placeholder="Adicionar nota comercial, alinhamento técnico ou ata de reunião..."
                maxLength={4000}
                style={{ resize: 'none' }}
                className="w-full px-3 py-2 text-xs rounded border border-vertex-border dark:border-vertex-dark-border bg-vertex-bg/30 dark:bg-vertex-dark-card text-vertex-dark dark:text-vertex-dark-text placeholder:text-vertex-muted dark:placeholder:text-vertex-dark-muted/60 focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-colors resize-none"
              />
              <div className="flex items-center justify-between">
                <span className="text-[10px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
                  {novaNota.length} / 4000 caracteres
                </span>
                <button
                  type="submit"
                  disabled={salvandoNota || !novaNota.trim()}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded bg-vertex-charcoal text-white hover:bg-vertex-dark dark:bg-vertex-orange dark:hover:bg-vertex-orange-hover transition-colors disabled:opacity-40"
                >
                  <Send className="w-3 h-3" />
                  <span>{salvandoNota ? 'Salvando...' : 'Adicionar Nota'}</span>
                </button>
              </div>
            </form>

            {/* Listagem de notas */}
            <div className="space-y-2.5">
              {loadingNotas ? (
                <div className="p-4 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">Carregando notas...</div>
              ) : notas.length === 0 ? (
                <div className="p-6 text-center border border-dashed border-vertex-border dark:border-vertex-dark-border rounded bg-white dark:bg-vertex-dark-surface text-xs text-vertex-muted dark:text-vertex-dark-muted">
                  Nenhuma nota registrada para esta empresa.
                </div>
              ) : (
                notas.map((nota) => (
                  <div
                    key={nota.idNotaEmpresa}
                    className="p-3.5 bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded shadow-subtle space-y-2 group hover:border-vertex-border/90 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-mono text-vertex-muted dark:text-vertex-dark-muted flex items-center gap-1">
                        <Clock className="w-3 h-3" /> {formatDate(nota.criadoEm)}
                      </span>
                      <button
                        onClick={() => handleExcluirNota(nota.idNotaEmpresa)}
                        title="Excluir nota"
                        className="opacity-0 group-hover:opacity-100 p-1 text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 transition-opacity"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                    <p className="text-xs text-vertex-dark dark:text-vertex-dark-text leading-relaxed whitespace-pre-wrap">
                      {nota.conteudo}
                    </p>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
