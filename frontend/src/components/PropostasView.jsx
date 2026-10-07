import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  FileText, 
  Plus, 
  Building2, 
  Calendar, 
  Clock, 
  Layers, 
  ChevronLeft, 
  ChevronRight, 
  X, 
  Save, 
  Code, 
  Eye,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';

export const PropostasView = ({ preselectedEmpresa, onClearPreselectedEmpresa }) => {
  const [propostas, setPropostas] = useState([]);
  const [empresas, setEmpresas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });

  // Modais e Detalhe
  const [modalNovaOpen, setModalNovaOpen] = useState(false);
  const [propostaDetalhe, setPropostaDetalhe] = useState(null);
  const [modalNovaVersaoOpen, setModalNovaVersaoOpen] = useState(false);

  // Form Nova Proposta
  const [formData, setFormData] = useState({
    idEmpresa: '',
    titulo: '',
    resumo: '',
    escopoJson: '',
  });
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Form Nova Versão
  const [versaoJson, setVersaoJson] = useState('');
  const [submittingVersao, setSubmittingVersao] = useState(false);

  const toast = useToast();

  useEffect(() => {
    carregarPropostas();
    carregarEmpresas();
  }, [page]);

  useEffect(() => {
    if (preselectedEmpresa) {
      setFormData((prev) => ({
        ...prev,
        idEmpresa: preselectedEmpresa.idEmpresa,
        titulo: `Proposta Comercial - ${preselectedEmpresa.nome}`,
      }));
      setModalNovaOpen(true);
      if (onClearPreselectedEmpresa) onClearPreselectedEmpresa();
    }
  }, [preselectedEmpresa]);

  // Fechar modais ao pressionar a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (modalNovaVersaoOpen) {
          setModalNovaVersaoOpen(false);
        } else if (modalNovaOpen) {
          setModalNovaOpen(false);
        } else if (propostaDetalhe) {
          setPropostaDetalhe(null);
        }
      }
    };
    if (modalNovaOpen || propostaDetalhe || modalNovaVersaoOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [modalNovaOpen, propostaDetalhe, modalNovaVersaoOpen]);

  const carregarPropostas = async () => {
    setLoading(true);
    try {
      const res = await api.propostas.listar(page, pageSize);
      setPropostas(res?.content || []);
      setPageData({
        totalElements: res?.totalElements || 0,
        totalPages: res?.totalPages || 1,
      });
    } catch (err) {
      toast.error('Erro ao carregar propostas');
    } finally {
      setLoading(false);
    }
  };

  const carregarEmpresas = async () => {
    try {
      const res = await api.empresas.listar(0, 100);
      setEmpresas(res?.content || []);
    } catch {
      // silencioso
    }
  };

  const getNomeEmpresa = (idEmpresa) => {
    const emp = empresas.find((e) => e.idEmpresa === idEmpresa);
    return emp ? emp.nome : idEmpresa;
  };

  const formatarJson = (valor) => {
    if (!valor) return '';
    if (typeof valor === 'object') {
      try {
        return JSON.stringify(valor, null, 2);
      } catch {
        return String(valor);
      }
    }
    if (typeof valor === 'string') {
      const comQuebras = valor.replace(/\\n/g, '\n');
      try {
        const obj = JSON.parse(comQuebras);
        return JSON.stringify(obj, null, 2);
      } catch {
        return comQuebras;
      }
    }
    return String(valor);
  };

  const handleCriarProposta = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!formData.idEmpresa) errs.idEmpresa = 'Selecione a empresa vinculada';
    if (!formData.titulo.trim()) errs.titulo = 'Título é obrigatório';
    else if (formData.titulo.length > 255) errs.titulo = 'Máximo de 255 caracteres';

    if (formData.resumo && formData.resumo.length > 4000) {
      errs.resumo = 'Máximo de 4000 caracteres';
    }


    if (Object.keys(errs).length > 0) {
      setFormErrors(errs);
      return;
    }

    setSubmitting(true);
    try {
      const nova = await api.propostas.criar({
        idEmpresa: formData.idEmpresa,
        titulo: formData.titulo.trim(),
        resumo: formData.resumo.trim() || null,
        escopoJson: formData.escopoJson || null,
      });
      setPropostas([nova, ...propostas]);
      setPageData((prev) => ({ ...prev, totalElements: prev.totalElements + 1 }));
      setModalNovaOpen(false);
      setFormData({
        idEmpresa: '',
        titulo: '',
        resumo: '',
        escopoJson: '',
      });
      setFormErrors({});
      toast.success('Proposta criada com sucesso');
    } catch (err) {
      toast.error(err.message || 'Erro ao criar proposta');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCriarNovaVersao = async (e) => {
    e.preventDefault();
    if (!versaoJson.trim()) return;

    try {
      JSON.parse(versaoJson);
    } catch {
      toast.error('O conteúdo da nova versão deve ser um JSON válido');
      return;
    }

    setSubmittingVersao(true);
    try {
      const atualizada = await api.propostas.novaVersao(propostaDetalhe.idProposta, versaoJson);
      setPropostas(propostas.map((p) => (p.idProposta === atualizada.idProposta ? atualizada : p)));
      setPropostaDetalhe(atualizada);
      setModalNovaVersaoOpen(false);
      setVersaoJson('');
      toast.success('Nova versão da proposta registrada no backend');
    } catch (err) {
      toast.error(err.message || 'Erro ao criar nova versão');
    } finally {
      setSubmittingVersao(false);
    }
  };

  const formatDate = (isoString) => {
    if (!isoString) return '-';
    try {
      return new Date(isoString).toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
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
            Propostas Comerciais
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Elaboração e versionamento de propostas vinculadas a empresas.
          </p>
        </div>

        <button
          onClick={() => {
            setFormData({
              idEmpresa: empresas[0]?.idEmpresa || '',
              titulo: '',
              resumo: '',
              escopoJson: '',
            });
            setModalNovaOpen(true);
          }}
          className="flex items-center justify-center gap-2 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] transition-all duration-200 shadow-sm hover:shadow-glow-orange self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Nova Proposta</span>
        </button>
      </div>

      {/* Tabela de Propostas */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg shadow-subtle dark:shadow-dark-subtle overflow-hidden transition-colors duration-700 ease-in-out">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60 text-slate-500 dark:text-slate-400 font-semibold text-[11px] uppercase tracking-wider">
                <th className="py-3.5 px-5">Título da Proposta</th>
                <th className="py-3.5 px-5">Empresa Vinculada</th>
                <th className="py-3.5 px-5">Status</th>
                <th className="py-3.5 px-5">Válido Até</th>
                <th className="py-3.5 px-5">Criado Em</th>
                <th className="py-3.5 px-5 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-vertex-border dark:divide-vertex-dark-border">
              {loading ? (
                <tr>
                  <td colSpan="6" className="py-12 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Carregando propostas...
                  </td>
                </tr>
              ) : propostas.length === 0 ? (
                <tr>
                  <td colSpan="6" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Nenhuma proposta encontrada.
                  </td>
                </tr>
              ) : (
                propostas.map((prop) => (
                  <tr
                    key={prop.idProposta}
                    className="hover:bg-slate-50/70 dark:hover:bg-vertex-dark-surface/40 transition-colors duration-150 cursor-pointer group"
                    onClick={() => setPropostaDetalhe(prop)}
                  >
                    {/* Título e ID */}
                    <td className="py-4 px-5">
                      <div className="flex items-center gap-3">
                        <div className="w-7.5 h-7.5 rounded bg-vertex-orange-light dark:bg-vertex-orange/10 text-vertex-orange flex items-center justify-center font-bold text-xs shrink-0">
                          <FileText className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text group-hover:text-vertex-orange transition-colors">
                            {prop.titulo}
                          </span>
                          <span className="block font-mono text-[10px] text-vertex-muted dark:text-vertex-dark-muted mt-0.5">
                            {prop.idProposta}
                          </span>
                        </div>
                      </div>
                    </td>

                    {/* Empresa */}
                    <td className="py-4 px-5 font-medium text-vertex-dark dark:text-vertex-dark-text">
                      <div className="flex items-center gap-1.5">
                        <Building2 className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
                        <span>{getNomeEmpresa(prop.idEmpresa)}</span>
                      </div>
                    </td>

                    {/* Status */}
                    <td className="py-4 px-5">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-800 dark:text-amber-400 border border-amber-200 dark:border-amber-800/40">
                        {prop.status}
                      </span>
                    </td>

                    {/* Válido Até */}
                    <td className="py-4 px-5 font-mono text-[11px] text-vertex-body dark:text-vertex-dark-muted">
                      {formatDate(prop.validoAte)}
                    </td>

                    {/* Criado Em */}
                    <td className="py-4 px-5 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                      {formatDate(prop.criadoEm)}
                    </td>

                    {/* Ações */}
                    <td className="py-4 px-5 text-right" onClick={(e) => e.stopPropagation()}>
                      <button
                        onClick={() => setPropostaDetalhe(prop)}
                        className="h-7.5 px-3 text-xs font-medium rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-surface hover:bg-slate-50 dark:hover:bg-vertex-dark-card text-vertex-dark dark:text-vertex-dark-text active:scale-95 transition-all duration-150"
                      >
                        Visualizar
                      </button>
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
            Total: <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text">{pageData.totalElements}</span> propostas
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
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page + 1 >= pageData.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text disabled:opacity-30 active:scale-95 transition-all duration-150"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Modal: Nova Proposta */}
      {modalNovaOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-vertex-dark/50 dark:bg-black/70 backdrop-blur-[2px] animate-in fade-in">
          <div className="bg-white dark:bg-vertex-dark-card border border-gray-100 dark:border-vertex-dark-border shadow-2xl rounded-2xl max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden transition-colors">
            <div className="flex items-center justify-between px-6 sm:px-8 py-5 border-b border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-vertex-orange-light dark:bg-vertex-orange/10 flex items-center justify-center text-vertex-orange">
                  <FileText className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                    Criar Nova Proposta Comercial
                  </h2>
                  <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                    Preencha os termos e escopo técnico da proposta
                  </p>
                </div>
              </div>
              <button
                onClick={() => setModalNovaOpen(false)}
                className="p-1.5 rounded-lg text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCriarProposta} className="flex-1 overflow-y-auto p-6 sm:p-8 space-y-5">
              {/* Seleção de Empresa */}
              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Empresa Beneficiária <span className="text-vertex-orange">*</span>
                </label>
                <select
                  value={formData.idEmpresa}
                  onChange={(e) => setFormData({ ...formData, idEmpresa: e.target.value })}
                  className={`w-full px-3.5 py-2.5 text-xs rounded-xl border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all ${
                    formErrors.idEmpresa ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                >
                  <option value="">Selecione uma empresa...</option>
                  {empresas.map((emp) => (
                    <option key={emp.idEmpresa} value={emp.idEmpresa}>
                      {emp.nome} ({emp.cidade || 'Sem cidade'} - {emp.estado || 'UF'})
                    </option>
                  ))}
                </select>
                {formErrors.idEmpresa && (
                  <p className="text-[11px] text-red-500 mt-1">{formErrors.idEmpresa}</p>
                )}
              </div>

              {/* Título */}
              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Título da Proposta <span className="text-vertex-orange">*</span>
                </label>
                <input
                  type="text"
                  value={formData.titulo}
                  onChange={(e) => setFormData({ ...formData, titulo: e.target.value })}
                  placeholder="Ex: Consultoria em Microsserviços e Observabilidade"
                  maxLength={255}
                  className={`w-full px-3.5 py-2.5 text-xs rounded-xl border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all ${
                    formErrors.titulo ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                />
                {formErrors.titulo && (
                  <p className="text-[11px] text-red-500 mt-1">{formErrors.titulo}</p>
                )}
              </div>

              {/* Resumo */}
              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Resumo Executivo (resumo)
                </label>
                <textarea
                  className="w-full px-3.5 py-2.5 text-xs rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange resize-none transition-all"
                  style={{ resize: 'none' }}
                  rows={4}
                  value={formData.resumo}
                  onChange={(e) => setFormData({ ...formData, resumo: e.target.value })}
                  placeholder="Síntese dos objetivos, premissas e direcionamentos..."
                  maxLength={4000}
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-5 border-t border-vertex-border dark:border-vertex-dark-border">
                <button
                  type="button"
                  onClick={() => setModalNovaOpen(false)}
                  className="px-4 py-2.5 text-xs font-medium text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text rounded-xl hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex items-center gap-2 px-5 py-2.5 text-xs font-medium rounded-xl bg-vertex-orange text-white hover:bg-vertex-orange-hover shadow-subtle disabled:opacity-50 transition-all duration-150"
                >
                  <Save className="w-3.5 h-3.5" />
                  <span>{submitting ? 'Salvando...' : 'Criar Proposta'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Detalhe da Proposta & Criação de Nova Versão */}
      {propostaDetalhe && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-vertex-dark/50 dark:bg-black/70 backdrop-blur-[2px] animate-in fade-in">
          <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal rounded max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden transition-colors">
            <div className="flex items-center justify-between px-6 py-4 border-b border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card">
              <div>
                <h2 className="text-base font-semibold text-vertex-dark dark:text-vertex-dark-text">
                  {propostaDetalhe.titulo}
                </h2>
                <p className="text-[11px] font-mono text-vertex-muted dark:text-vertex-dark-muted">
                  ID: {propostaDetalhe.idProposta} • Status: {propostaDetalhe.status}
                </p>
              </div>
              <button
                onClick={() => setPropostaDetalhe(null)}
                className="p-1 rounded text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6 space-y-5">
              {/* Informações Gerais */}
              <div className="grid grid-cols-2 gap-4 p-4 bg-vertex-bg/50 dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded text-xs">
                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Empresa Associada:</span>
                  <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text mt-0.5 block">
                    {getNomeEmpresa(propostaDetalhe.idEmpresa)}
                  </span>
                  <span className="font-mono text-[10px] text-vertex-muted dark:text-vertex-dark-muted">
                    {propostaDetalhe.idEmpresa}
                  </span>
                </div>

                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Vigência (validoAte):</span>
                  <span className="font-mono text-vertex-dark dark:text-vertex-dark-text mt-0.5 block">
                    {formatDate(propostaDetalhe.validoAte)}
                  </span>
                </div>

                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Criado Em:</span>
                  <span className="font-mono text-vertex-muted dark:text-vertex-dark-muted">
                    {formatDate(propostaDetalhe.criadoEm)}
                  </span>
                </div>

                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Atualizado Em:</span>
                  <span className="font-mono text-vertex-muted dark:text-vertex-dark-muted">
                    {formatDate(propostaDetalhe.atualizadoEm)}
                  </span>
                </div>
              </div>

              {/* Resumo */}
              {propostaDetalhe.resumo && (
                <div>
                  <h3 className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text mb-1">
                    Resumo da Proposta
                  </h3>
                  <p className="text-xs text-vertex-body dark:text-vertex-dark-muted leading-relaxed p-3 bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded">
                    {propostaDetalhe.resumo}
                  </p>
                </div>
              )}

              {/* Escopo Atual */}
              {propostaDetalhe.escopo && (
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <h3 className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text flex items-center gap-1.5">
                      <Code className="w-3.5 h-3.5 text-vertex-orange" />
                      <span>Conteúdo do Escopo (JSON Atual)</span>
                    </h3>
                  </div>
                  <pre className="text-xs font-mono bg-vertex-dark dark:bg-black text-slate-100 p-3.5 rounded overflow-x-auto border border-vertex-border dark:border-vertex-dark-border whitespace-pre-wrap">
                    {formatarJson(propostaDetalhe.escopo)}
                  </pre>
                </div>
              )}

              {/* Ação para Nova Versão (/versoes) */}
              <div className="p-4 border border-dashed border-vertex-border dark:border-vertex-dark-border rounded bg-white dark:bg-vertex-dark-surface flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text">
                    Versionamento da Proposta
                  </h4>
                  <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                    Gere uma nova revisão contratual mantendo o histórico de alterações
                  </p>
                </div>
                <button
                  onClick={() => {
                    setVersaoJson(formatarJson(propostaDetalhe.escopo || { versao: 2 }));
                    setModalNovaVersaoOpen(true);
                  }}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded bg-vertex-charcoal dark:bg-vertex-orange text-white hover:bg-vertex-dark dark:hover:bg-vertex-orange-hover transition-colors shadow-subtle shrink-0"
                >
                  <Layers className="w-3.5 h-3.5" />
                  <span>Publicar Nova Versão</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Publicar Nova Versão */}
      {modalNovaVersaoOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-vertex-dark/50 dark:bg-black/70 backdrop-blur-[2px] animate-in fade-in">
          <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal rounded max-w-lg w-full p-6 space-y-4 transition-colors">
            <div className="flex items-center justify-between pb-3 border-b border-vertex-border dark:border-vertex-dark-border">
              <div>
                <h3 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                  Publicar Nova Versão da Proposta
                </h3>
                <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                  Atualização e registro de aditivo contratual
                </p>
              </div>
              <button
                onClick={() => setModalNovaVersaoOpen(false)}
                className="p-1 rounded text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCriarNovaVersao} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                  Conteúdo JSON da Nova Versão <span className="text-vertex-orange">*</span>
                </label>
                <textarea
                  className="w-full px-3 py-2 font-mono text-xs rounded border border-vertex-border dark:border-vertex-dark-border bg-vertex-bg/50 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange resize-none"
                  style={{ resize: 'none' }}
                  rows={8}
                  value={versaoJson}
                  onChange={(e) => setVersaoJson(e.target.value)}
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-vertex-border dark:border-vertex-dark-border">
                <button
                  type="button"
                  onClick={() => setModalNovaVersaoOpen(false)}
                  className="px-3.5 py-1.5 text-xs font-medium text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={submittingVersao}
                  className="flex items-center gap-1.5 px-4 py-1.5 text-xs font-medium rounded bg-vertex-orange text-white hover:bg-vertex-orange-hover shadow-subtle disabled:opacity-50"
                >
                  <Save className="w-3.5 h-3.5" />
                  <span>{submittingVersao ? 'Salvando Versão...' : 'Confirmar e Publicar'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
