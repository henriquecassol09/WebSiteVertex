import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { EmpresaModal } from './EmpresaModal';
import { EmpresaDetalhe } from './EmpresaDetalhe';
import { 
  Search, 
  Edit2, 
  Trash2, 
  Eye, 
  Info,
  ChevronLeft, 
  ChevronRight,
  Filter,
  ExternalLink,
  AlertCircle,
  X
} from 'lucide-react';

export const EmpresasView = () => {
  const [empresas, setEmpresas] = useState([]);
  const [pesquisas, setPesquisas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });
  const [termoBusca, setTermoBusca] = useState('');
  const [filtroTexto, setFiltroTexto] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [showToast, setShowToast] = useState(false);

  // Auto-dispensar o toast após 3.5 segundos
  useEffect(() => {
    if (showToast) {
      const timer = setTimeout(() => {
        setShowToast(false);
      }, 3500);
      return () => clearTimeout(timer);
    }
  }, [showToast]);

  const handleBuscar = () => {
    const texto = termoBusca.trim();
    setFiltroTexto(texto);

    if (texto !== '') {
      const temResultados = empresas.some((emp) => {
        const matchTexto =
          emp.nome?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.cidade?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.categoria?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.email?.toLowerCase().includes(texto.toLowerCase());

        const matchCategoria =
          filtroCategoria === '' || emp.categoria === filtroCategoria;

        return matchTexto && matchCategoria;
      });

      if (!temResultados) {
        setShowToast(true);
      } else {
        setShowToast(false);
      }
    } else {
      setShowToast(false);
    }
  };

  // Modais e Detalhes
  const [modalOpen, setModalOpen] = useState(false);
  const [empresaEmEdicao, setEmpresaEmEdicao] = useState(null);
  const [empresaSelecionada, setEmpresaSelecionada] = useState(null);

  const toast = useToast();

  useEffect(() => {
    carregarEmpresas();
    carregarPesquisas();
  }, [page]);

  const carregarEmpresas = async () => {
    setLoading(true);
    try {
      const res = await api.empresas.listar(page, pageSize);
      setEmpresas(res?.content || []);
      setPageData({
        totalElements: res?.totalElements || 0,
        totalPages: res?.totalPages || 1,
      });
    } catch {
      setEmpresas([]);
    } finally {
      setLoading(false);
    }
  };

  const carregarPesquisas = async () => {
    try {
      const res = await api.pesquisas.listar(0, 50);
      setPesquisas(res?.content || []);
    } catch {
      setPesquisas([]);
    }
  };

  const handleSalvarEmpresa = async (formData) => {
    try {
      if (empresaEmEdicao) {
        const atualizada = await api.empresas.atualizar(empresaEmEdicao.idEmpresa, formData);
        setEmpresas(empresas.map((e) => (e.idEmpresa === atualizada.idEmpresa ? atualizada : e)));
        if (empresaSelecionada?.idEmpresa === atualizada.idEmpresa) {
          setEmpresaSelecionada(atualizada);
        }
        toast.success('Empresa atualizada com sucesso');
      } else {
        const criada = await api.empresas.criar(formData);
        setEmpresas([criada, ...empresas]);
        setPageData((prev) => ({ ...prev, totalElements: prev.totalElements + 1 }));
        toast.success('Empresa cadastrada com sucesso');
      }
      setEmpresaEmEdicao(null);
    } catch (err) {
      toast.error(err.message || 'Erro ao salvar empresa');
      throw err;
    }
  };

  const handleExcluir = async (idEmpresa) => {
    if (!confirm('Deseja realmente excluir esta empresa?')) return;

    try {
      await api.empresas.excluir(idEmpresa);
      setEmpresas(empresas.filter((e) => e.idEmpresa !== idEmpresa));
      setPageData((prev) => ({ ...prev, totalElements: Math.max(0, prev.totalElements - 1) }));
      if (empresaSelecionada?.idEmpresa === idEmpresa) {
        setEmpresaSelecionada(null);
      }
      toast.success('Empresa excluída com sucesso');
    } catch (err) {
      toast.error('Erro ao excluir empresa');
    }
  };

  const empresasFiltradas = empresas.filter((emp) => {
    const matchTexto =
      filtroTexto === '' ||
      emp.nome?.toLowerCase().includes(filtroTexto.toLowerCase()) ||
      emp.cidade?.toLowerCase().includes(filtroTexto.toLowerCase()) ||
      emp.categoria?.toLowerCase().includes(filtroTexto.toLowerCase()) ||
      emp.email?.toLowerCase().includes(filtroTexto.toLowerCase());

    const matchCategoria =
      filtroCategoria === '' || emp.categoria === filtroCategoria;

    return matchTexto && matchCategoria;
  });

  const categoriasUnicas = Array.from(
    new Set(empresas.map((e) => e.categoria).filter(Boolean))
  );

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
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Empresas
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Registros de organizações e contatos retornados pela API.
          </p>
        </div>
      </div>

      {/* Barra de Filtros & Busca */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg p-3 sm:p-3.5 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between shadow-subtle dark:shadow-dark-subtle transition-colors duration-700 ease-in-out">
        <div className="flex items-center gap-2 flex-1">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={termoBusca}
              onChange={(e) => {
                setTermoBusca(e.target.value);
                if (e.target.value === '') {
                  setFiltroTexto('');
                }
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleBuscar();
                }
              }}
              placeholder="Buscar por nome, cidade ou e-mail..."
              className="w-full h-9 pl-9 pr-3.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text placeholder:text-slate-400 dark:placeholder:text-slate-500/70 focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            />
          </div>
          <button
            type="button"
            onClick={handleBuscar}
            className="flex items-center justify-center gap-1.5 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-[0.98] text-white transition-all duration-200 shadow-sm hover:shadow-glow-orange shrink-0"
          >
            <Search className="w-3.5 h-3.5" />
            <span>Pesquisar</span>
          </button>
        </div>

        {categoriasUnicas.length > 0 && (
          <div className="flex items-center gap-2">
            <Filter className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
            <select
              value={filtroCategoria}
              onChange={(e) => setFiltroCategoria(e.target.value)}
              className="h-9 px-3 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            >
              <option value="">Todas as categorias</option>
              {categoriasUnicas.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Tabela de Empresas - Estritamente fiel às propriedades de EmpresaResponse */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg shadow-subtle dark:shadow-dark-subtle overflow-hidden transition-colors duration-700 ease-in-out">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60 text-slate-500 dark:text-slate-400 font-semibold text-[11px] uppercase tracking-wider">
                <th className="py-3.5 px-5">Nome</th>
                <th className="py-3.5 px-5">Categoria</th>
                <th className="py-3.5 px-5">Cidade / UF</th>
                <th className="py-3.5 px-5">Telefone</th>
                <th className="py-3.5 px-5">E-mail</th>
                <th className="py-3.5 px-5">Site</th>
                <th className="py-3.5 px-5">Cadastrado em</th>
                <th className="py-3.5 px-5 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-vertex-border dark:divide-vertex-dark-border">
              {loading ? (
                <tr>
                  <td colSpan="8" className="py-12 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Carregando empresas...
                  </td>
                </tr>
              ) : empresasFiltradas.length === 0 ? (
                <tr>
                  <td colSpan="8" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Nenhuma empresa encontrada.
                  </td>
                </tr>
              ) : (
                empresasFiltradas.map((emp) => (
                  <tr
                    key={emp.idEmpresa}
                    className="hover:bg-slate-50/70 dark:hover:bg-vertex-dark-surface/40 transition-colors duration-150 cursor-pointer"
                    onClick={() => setEmpresaSelecionada(emp)}
                  >
                    {/* Nome */}
                    <td className="py-4 px-5 font-semibold text-vertex-dark dark:text-vertex-dark-text">
                      {emp.nome}
                    </td>

                    {/* Categoria */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {emp.categoria || '-'}
                    </td>

                    {/* Cidade e Estado */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {[emp.cidade, emp.estado].filter(Boolean).join(' - ') || '-'}
                    </td>

                    {/* Telefone */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text font-mono text-[11px]">
                      {emp.telefone || '-'}
                    </td>

                    {/* E-mail */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {emp.email || '-'}
                    </td>

                    {/* Site */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {emp.site ? (
                        <a
                          href={emp.site.startsWith('http') ? emp.site : `https://${emp.site}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => e.stopPropagation()}
                          className="text-vertex-orange hover:text-vertex-orange-hover hover:underline inline-flex items-center gap-1 transition-colors"
                        >
                          <span className="truncate max-w-[150px]">{emp.site}</span>
                          <ExternalLink className="w-3 h-3 shrink-0" />
                        </a>
                      ) : (
                        '-'
                      )}
                    </td>

                    {/* Criado em */}
                    <td className="py-4 px-5 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                      {formatDate(emp.criadoEm)}
                    </td>

                    {/* Ações */}
                    <td className="py-4 px-5 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => setEmpresaSelecionada(emp)}
                          title="Ver informações detalhadas da empresa"
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md bg-vertex-orange/10 dark:bg-vertex-orange/15 hover:bg-vertex-orange hover:text-white text-vertex-orange active:scale-95 text-[11px] font-semibold border border-vertex-orange/20 hover:border-vertex-orange transition-all duration-150 shadow-sm"
                        >
                          <Info className="w-3.5 h-3.5 shrink-0" />
                          <span>Informações</span>
                        </button>
                        <button
                          onClick={() => {
                            setEmpresaEmEdicao(emp);
                            setModalOpen(true);
                          }}
                          title="Editar"
                          className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-vertex-dark-surface text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text active:scale-95 transition-all duration-150"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleExcluir(emp.idEmpresa)}
                          title="Excluir"
                          className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-950/40 text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 dark:hover:text-red-400 active:scale-95 transition-all duration-150"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
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
            Total: <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text">{pageData.totalElements}</span> empresas
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

      {/* Modal de Criação / Edição */}
      <EmpresaModal
        isOpen={modalOpen}
        onClose={() => {
          setModalOpen(false);
          setEmpresaEmEdicao(null);
        }}
        onSave={handleSalvarEmpresa}
        empresa={empresaEmEdicao}
        pesquisas={pesquisas}
      />

      {/* Detalhe da Empresa */}
      {empresaSelecionada && (
        <EmpresaDetalhe
          empresa={empresaSelecionada}
          onClose={() => setEmpresaSelecionada(null)}
          onEdit={(emp) => {
            setEmpresaEmEdicao(emp);
            setModalOpen(true);
          }}
          onNovaProposta={(emp) => {
            setEmpresaSelecionada(null);
          }}
        />
      )}

      {/* Toast Flutuante Inferior Direito com Animação Slide-Up */}
      <div
        className={`fixed bottom-6 right-6 z-50 transform transition-all duration-300 ease-out ${
          showToast
            ? 'translate-y-0 opacity-100 pointer-events-auto'
            : 'translate-y-4 opacity-0 pointer-events-none'
        }`}
      >
        <div className="flex items-center gap-3 px-4 py-3 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-white border-2 border-gray-400 dark:border-gray-700 shadow-xl dark:shadow-2xl backdrop-blur-md text-xs font-medium">
          <div className="w-5 h-5 rounded-full bg-vertex-orange/20 flex items-center justify-center shrink-0">
            <AlertCircle className="w-3.5 h-3.5 text-vertex-orange" />
          </div>
          <span className="font-medium whitespace-nowrap text-gray-900 dark:text-white">
            Nenhuma empresa encontrada.
          </span>
          <button
            type="button"
            onClick={() => setShowToast(false)}
            className="ml-2 p-1 rounded hover:bg-gray-100 dark:hover:bg-white/10 text-gray-500 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
};
