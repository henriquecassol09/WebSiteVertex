import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  ChevronLeft, 
  ChevronRight, 
  BookmarkPlus, 
  CheckCircle2, 
  MapPin, 
  Building2, 
  Search,
  Trash2,
  Loader2,
  Globe2
} from 'lucide-react';
import { detectarCategoria } from '../utils/categoriaHelper';

export const PesquisasView = () => {
  const [pesquisas, setPesquisas] = useState([]);
  const [empresasSalvasIds, setEmpresasSalvasIds] = useState(new Set());
  const [salvandoId, setSalvandoId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [buscandoOverpass, setBuscandoOverpass] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });

  const toast = useToast();

  useEffect(() => {
    carregarPesquisas();
    carregarEmpresasSalvas();
  }, [page]);

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

  const carregarEmpresasSalvas = async () => {
    try {
      const res = await api.empresas.listar(0, 100);
      const salvas = new Set();
      (res?.content || []).forEach((emp) => {
        if (emp.idPesquisa) salvas.add(emp.idPesquisa);
      });
      setEmpresasSalvasIds(salvas);
    } catch {
      // silencioso
    }
  };

  /**
   * Dispara a busca via Backend Java (Spring Boot)
   */
  const handleRealizarBusca = async () => {
    setBuscandoOverpass(true);

    try {
      await api.pesquisas.prospectarRegiao();
      toast.success('Busca concluída');
      await carregarPesquisas();
    } catch (err) {
      toast.error(err.message || 'Erro ao realizar busca');
    } finally {
      setBuscandoOverpass(false);
    }
  };

  /**
   * Salva uma empresa prospectada no banco (aparece na aba Empresas).
   * Uma empresa salva NÃO deve aparecer em nenhuma busca.
   */
  const handleSalvarEmpresa = async (p) => {
    setSalvandoId(p.idPesquisa);
    try {
      const cidade = p.cidade || 'Laranjeiras do Sul';
      const estado = p.estado || 'PR';
      const endereco = p.endereco || 'Centro';
      const categoria = detectarCategoria(p.categoria, p.consulta);
      const horario = p.horario || 'Não informado';

      const payload = {
        nome: p.consulta,
        categoria: categoria,
        cidade: cidade,
        estado: estado,
        endereco: endereco,
        telefone: p.telefone || 'Não informado',
        email: 'Não informado',
        site: '',
        descricao: `Atividade: ${categoria}. Horário de funcionamento: ${horario}. Sem website registrado.`,
        idPesquisa: p.idPesquisa
      };

      await api.empresas.criar(payload);

      // Remove a empresa salva da lista da busca imediatamente
      setPesquisas((prev) => prev.filter((item) => item.idPesquisa !== p.idPesquisa && item.consulta.toLowerCase() !== p.consulta.toLowerCase()));
      setEmpresasSalvasIds((prev) => new Set([...prev, p.idPesquisa]));
      setPageData((prev) => ({ ...prev, totalElements: Math.max(0, prev.totalElements - 1) }));

      toast.success(`"${p.consulta}" salva com sucesso! Movida para a aba Empresas.`);
    } catch (err) {
      toast.error(err.message || 'Erro ao salvar empresa');
    } finally {
      setSalvandoId(null);
    }
  };

  /**
   * Exclui uma busca registrada no banco
   */
  const handleExcluirPesquisa = async (idPesquisa) => {
    if (!confirm('Deseja realmente excluir esta busca do banco de dados?')) return;
    try {
      await api.pesquisas.excluir(idPesquisa);
      setPesquisas((prev) => prev.filter((p) => p.idPesquisa !== idPesquisa));
      setPageData((prev) => ({ ...prev, totalElements: Math.max(0, prev.totalElements - 1) }));
      toast.success('Busca excluída com sucesso');
    } catch (err) {
      toast.error(err.message || 'Erro ao excluir busca');
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
      {/* Top Banner com Botão Direto de Busca */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Busca
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Estabelecimentos comerciais sem website em <strong>Laranjeiras do Sul, Rio Bonito do Iguaçu, Porto Barreiro, Virmond, Cantagalo e Nova Laranjeiras</strong>.
          </p>
        </div>

        {/* Botão Clicável de Execução Direta */}
        <button
          type="button"
          onClick={handleRealizarBusca}
          disabled={buscandoOverpass}
          className="flex items-center justify-center gap-2 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] transition-all duration-200 shadow-sm hover:shadow-glow-orange disabled:opacity-60 self-start sm:self-auto cursor-pointer"
        >
          {buscandoOverpass ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>Buscando...</span>
            </>
          ) : (
            <>
              <Search className="w-4 h-4" />
              <span>Realizar Busca</span>
            </>
          )}
        </button>
      </div>

      {/* Tabela de Empresas Encontradas na Busca */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg shadow-subtle dark:shadow-dark-subtle overflow-hidden transition-colors duration-700 ease-in-out">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60 text-slate-500 dark:text-slate-400 font-semibold text-[11px] uppercase tracking-wider">
                <th className="py-3.5 px-5">Empresa / Estabelecimento</th>
                <th className="py-3.5 px-5">Região & Endereço</th>
                <th className="py-3.5 px-5">Presença Web</th>
                <th className="py-3.5 px-5">Data da Busca</th>
                <th className="py-3.5 px-5 text-right">ID Pesquisa</th>
                <th className="py-3.5 px-5 text-right">Ação</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-vertex-border dark:divide-vertex-dark-border">
              {loading ? (
                <tr>
                  <td colSpan="6" className="py-12 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Carregando resultados da busca...
                  </td>
                </tr>
              ) : pesquisas.length === 0 ? (
                <tr>
                  <td colSpan="6" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Nenhuma busca realizada ainda. Clique no botão <strong>"Realizar Busca"</strong> acima para carregar as empresas sem site da sua região.
                  </td>
                </tr>
              ) : (
                pesquisas.map((p) => {
                  const jaSalva = empresasSalvasIds.has(p.idPesquisa);
                  const isSalvando = salvandoId === p.idPesquisa;

                  return (
                    <tr
                      key={p.idPesquisa}
                      className="hover:bg-slate-50/50 dark:hover:bg-vertex-dark-surface/30 transition-colors duration-150"
                    >
                      {/* Empresa & Atividade */}
                      <td className="py-4 px-5 font-semibold text-vertex-dark dark:text-vertex-dark-text select-text">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                          <div>
                            <span className="block">{p.consulta}</span>
                            <span className="text-[10px] font-medium text-vertex-orange bg-vertex-orange/10 px-1.5 py-0.5 rounded mt-0.5 inline-block">
                              {detectarCategoria(p.categoria, p.consulta)}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Endereço & Cidade/Estado */}
                      <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text select-text">
                        <div className="flex items-start gap-1.5">
                          <MapPin className="w-3 h-3 text-red-500 shrink-0 mt-0.5" />
                          <div className="truncate max-w-xs">
                            <span className="block font-medium">{p.endereco || 'Centro'}</span>
                            <span className="block text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                              {[p.cidade, p.estado].filter(Boolean).join(' - ') || p.regiao || 'Paraná'}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Presença Web / Status (apenas tag informativa) */}
                      <td className="py-4 px-5">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800/40 select-none">
                          <Globe2 className="w-3 h-3" />
                          Sem Website
                        </span>
                      </td>

                      {/* Data */}
                      <td className="py-4 px-5 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted select-text">
                        {formatDate(p.criadoEm)}
                      </td>

                      {/* ID */}
                      <td className="py-4 px-5 text-right font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted select-text">
                        {p.idPesquisa}
                      </td>

                      {/* Ações ÚNICAS clicáveis: Salvar Empresa (ícone BookmarkPlus) & Excluir Busca (ícone Trash2) */}
                      <td className="py-4 px-5 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {jaSalva ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40 select-none">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Salva</span>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleSalvarEmpresa(p)}
                              disabled={isSalvando}
                              title="Salvar esta empresa na aba Empresas"
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-95 text-white shadow-sm hover:shadow-glow-orange transition-all duration-150 disabled:opacity-50 cursor-pointer"
                            >
                              <BookmarkPlus className="w-3.5 h-3.5" />
                              <span>{isSalvando ? 'Salvando...' : 'Salvar Empresa'}</span>
                            </button>
                          )}

                          <button
                            type="button"
                            onClick={() => handleExcluirPesquisa(p.idPesquisa)}
                            title="Excluir esta empresa da busca"
                            className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-950/40 text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 dark:hover:text-red-400 active:scale-95 transition-all duration-150 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
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
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text disabled:opacity-30 active:scale-95 transition-all duration-150 cursor-pointer disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                disabled={page + 1 >= pageData.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text disabled:opacity-30 active:scale-95 transition-all duration-150 cursor-pointer disabled:cursor-not-allowed"
              >
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
