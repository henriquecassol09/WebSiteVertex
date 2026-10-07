import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  Plus, 
  ChevronLeft, 
  ChevronRight,
  X,
  Send
} from 'lucide-react';

export const PesquisasView = () => {
  const [pesquisas, setPesquisas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });
  const [modalOpen, setModalOpen] = useState(false);
  const [pesquisaDetalhe, setPesquisaDetalhe] = useState(null);

  // Form para nova pesquisa
  const [consulta, setConsulta] = useState('');
  const [regiao, setRegiao] = useState('');
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const toast = useToast();

  useEffect(() => {
    carregarPesquisas();
  }, [page]);

  // Fechar modais ao pressionar a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (modalOpen) setModalOpen(false);
        if (pesquisaDetalhe) setPesquisaDetalhe(null);
      }
    };
    if (modalOpen || pesquisaDetalhe) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [modalOpen, pesquisaDetalhe]);

  const carregarPesquisas = async () => {
    setLoading(true);
    try {
      const res = await api.pesquisas.listar(page, pageSize);
      setPesquisas(res?.content || []);
      setPageData({
        totalElements: res?.totalElements || 0,
        totalPages: res?.totalPages || 1,
      });
    } catch {
      setPesquisas([]);
    } finally {
      setLoading(false);
    }
  };

  const handleCriarPesquisa = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!consulta.trim()) {
      errs.consulta = 'Consulta é obrigatória';
    } else if (consulta.length > 500) {
      errs.consulta = 'Máximo de 500 caracteres';
    }

    if (regiao && regiao.length > 150) {
      errs.regiao = 'Máximo de 150 caracteres';
    }

    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setSubmitting(true);
    try {
      const nova = await api.pesquisas.criar({
        consulta: consulta.trim(),
        regiao: regiao.trim() || null,
      });
      setPesquisas([nova, ...pesquisas]);
      setPageData((prev) => ({ ...prev, totalElements: prev.totalElements + 1 }));
      setConsulta('');
      setRegiao('');
      setErrors({});
      setModalOpen(false);
      toast.success('Pesquisa criada com sucesso');
    } catch (err) {
      toast.error(err.message || 'Erro ao registrar pesquisa');
    } finally {
      setSubmitting(false);
    }
  };

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
    <div className="space-y-6">
      {/* Top Banner & Ação */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Pesquisas
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Consultas registradas retornadas pela API.
          </p>
        </div>

        <button
          onClick={() => setModalOpen(true)}
          className="flex items-center justify-center gap-2 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] transition-all duration-200 shadow-sm hover:shadow-glow-orange self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Nova Pesquisa</span>
        </button>
      </div>

      {/* Tabela de Pesquisas - Estritamente fiel a PesquisaResponse */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg shadow-subtle dark:shadow-dark-subtle overflow-hidden transition-colors duration-700 ease-in-out">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60 text-slate-500 dark:text-slate-400 font-semibold text-[11px] uppercase tracking-wider">
                <th className="py-3.5 px-5">Consulta</th>
                <th className="py-3.5 px-5">Região</th>
                <th className="py-3.5 px-5">Status</th>
                <th className="py-3.5 px-5">Criado em</th>
                <th className="py-3.5 px-5 text-right">ID Pesquisa</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-vertex-border dark:divide-vertex-dark-border">
              {loading ? (
                <tr>
                  <td colSpan="5" className="py-12 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Carregando pesquisas...
                  </td>
                </tr>
              ) : pesquisas.length === 0 ? (
                <tr>
                  <td colSpan="5" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Nenhuma pesquisa encontrada.
                  </td>
                </tr>
              ) : (
                pesquisas.map((p) => (
                  <tr
                    key={p.idPesquisa}
                    className="hover:bg-slate-50/70 dark:hover:bg-vertex-dark-surface/40 transition-colors duration-150 cursor-pointer"
                    onClick={() => setPesquisaDetalhe(p)}
                  >
                    {/* Consulta */}
                    <td className="py-4 px-5 font-semibold text-vertex-dark dark:text-vertex-dark-text">
                      {p.consulta}
                    </td>

                    {/* Região */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {p.regiao || '-'}
                    </td>

                    {/* Status */}
                    <td className="py-4 px-5">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40">
                        {p.status}
                      </span>
                    </td>

                    {/* Criado em */}
                    <td className="py-4 px-5 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                      {formatDate(p.criadoEm)}
                    </td>

                    {/* ID */}
                    <td className="py-4 px-5 text-right font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                      {p.idPesquisa}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Paginação */}
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 text-xs text-vertex-muted dark:text-vertex-dark-muted">
          <div>
            Total: <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text">{pageData.totalElements}</span> consultas
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px]">
              Página {page + 1} de {pageData.totalPages || 1}
            </span>
            <div className="flex items-center gap-1">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text disabled:opacity-30 active:scale-95 transition-all duration-150"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
              <button
                disabled={page + 1 >= pageData.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text disabled:opacity-30 active:scale-95 transition-all duration-150"
              >
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Modal: Nova Pesquisa */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 dark:bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal rounded-xl max-w-lg w-full p-6 space-y-4 transition-colors">
            <div className="flex items-center justify-between pb-3 border-b border-vertex-border dark:border-vertex-dark-border">
              <div>
                <h2 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                  Registrar Pesquisa
                </h2>
                <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted mt-0.5">
                  Informe os parâmetros para consulta
                </p>
              </div>
              <button
                onClick={() => setModalOpen(false)}
                className="p-1 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCriarPesquisa} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Consulta <span className="text-vertex-orange">*</span>
                </label>
                <input
                  type="text"
                  value={consulta}
                  onChange={(e) => setConsulta(e.target.value)}
                  placeholder="Termo de pesquisa"
                  maxLength={500}
                  className={`w-full h-9 px-3 text-xs rounded-md border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200 ${
                    errors.consulta ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                />
                {errors.consulta && (
                  <p className="text-[11px] text-red-500 mt-1">{errors.consulta}</p>
                )}
              </div>

              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Região
                </label>
                <input
                  type="text"
                  value={regiao}
                  onChange={(e) => setRegiao(e.target.value)}
                  placeholder="Localidade (opcional)"
                  maxLength={150}
                  className="w-full h-9 px-3 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-vertex-border dark:border-vertex-dark-border">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="px-3.5 py-2 text-xs font-medium text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface rounded-md transition-all duration-150"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex items-center gap-1.5 h-9 px-4 text-xs font-semibold rounded-md bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] shadow-sm hover:shadow-glow-orange disabled:opacity-50 transition-all duration-200"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{submitting ? 'Salvando...' : 'Salvar'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Detalhe da Pesquisa */}
      {pesquisaDetalhe && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 dark:bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal rounded-xl max-w-lg w-full p-6 space-y-4 transition-colors">
            <div className="flex items-center justify-between pb-3 border-b border-vertex-border dark:border-vertex-dark-border">
              <h2 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                Detalhes da Pesquisa
              </h2>
              <button
                onClick={() => setPesquisaDetalhe(null)}
                className="p-1 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3.5 text-xs">
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">ID da Pesquisa:</span>
                <span className="font-mono text-vertex-dark dark:text-vertex-dark-text font-medium">{pesquisaDetalhe.idPesquisa}</span>
              </div>
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Consulta:</span>
                <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text text-sm">{pesquisaDetalhe.consulta}</span>
              </div>
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Região:</span>
                <span className="text-vertex-body dark:text-vertex-dark-text">{pesquisaDetalhe.regiao || '-'}</span>
              </div>
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] mb-1">Status:</span>
                <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40">
                  {pesquisaDetalhe.status}
                </span>
              </div>
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Criado em:</span>
                <span className="font-mono text-vertex-muted dark:text-vertex-dark-muted">{formatDate(pesquisaDetalhe.criadoEm)}</span>
              </div>
            </div>

            <div className="flex justify-end pt-3 border-t border-vertex-border dark:border-vertex-dark-border">
              <button
                onClick={() => setPesquisaDetalhe(null)}
                className="px-4 py-2 text-xs font-medium rounded-md border border-vertex-border dark:border-vertex-dark-border text-vertex-dark dark:text-vertex-dark-text hover:bg-slate-50 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
