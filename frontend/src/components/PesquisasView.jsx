import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  ChevronLeft, 
  ChevronRight, 
  X, 
  BookmarkPlus, 
  CheckCircle2, 
  MapPin, 
  Building2, 
  Search,
  Trash2,
  Loader2,
  Globe2,
  Sparkles,
  ExternalLink,
  Phone,
  Tag
} from 'lucide-react';

export const PesquisasView = () => {
  const [pesquisas, setPesquisas] = useState([]);
  const [empresasSalvasIds, setEmpresasSalvasIds] = useState(new Set());
  const [salvandoId, setSalvandoId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [buscandoOverpass, setBuscandoOverpass] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });
  const [pesquisaDetalhe, setPesquisaDetalhe] = useState(null);

  const toast = useToast();

  useEffect(() => {
    carregarPesquisas();
    carregarEmpresasSalvas();
  }, [page]);

  // Fechar modal de detalhes ao pressionar a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        if (pesquisaDetalhe) setPesquisaDetalhe(null);
      }
    };
    if (pesquisaDetalhe) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [pesquisaDetalhe]);

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
   * As buscas são consultadas na Overpass API e persistidas no banco Neon
   */
  const handleRealizarBusca = async () => {
    setBuscandoOverpass(true);
    toast.info('Buscando empresas sem website na região via backend...');

    try {
      const novosResultados = await api.pesquisas.prospectarRegiao();

      if (!novosResultados || novosResultados.length === 0) {
        toast.info('Nenhuma nova empresa sem site encontrada no momento.');
      } else {
        toast.success(`${novosResultados.length} empresas encontradas e registradas no banco Neon!`);
      }

      await carregarPesquisas();
    } catch (err) {
      toast.error(err.message || 'Erro ao realizar busca de empresas no backend');
    } finally {
      setBuscandoOverpass(false);
    }
  };

  /**
   * Salva uma empresa prospectada no banco Neon (aparece na aba Empresas).
   * Conforme regra: uma empresa salva NÃO deve aparecer em nenhuma busca!
   */
  const handleSalvarEmpresa = async (p) => {
    setSalvandoId(p.idPesquisa);
    try {
      let cidade = 'Laranjeiras do Sul';
      let estado = 'PR';
      let endereco = p.regiao || 'Centro';

      if (p.regiao) {
        const partes = p.regiao.split('-');
        if (partes.length > 1) {
          cidade = partes[0].trim();
          estado = partes[1].trim().slice(0, 2).toUpperCase();
        } else {
          cidade = p.regiao.trim();
        }
      }

      const payload = {
        nome: p.consulta,
        categoria: 'Comércio Local',
        cidade: cidade,
        estado: estado,
        endereco: endereco,
        telefone: 'Não informado',
        email: 'Não informado',
        site: '',
        descricao: `Empresa prospectada via OpenStreetMap na região "${p.regiao}". Identificada sem presença digital / website.`,
        idPesquisa: p.idPesquisa
      };

      await api.empresas.criar(payload);

      // Remove a empresa salva da lista da busca imediatamente
      setPesquisas((prev) => prev.filter((item) => item.idPesquisa !== p.idPesquisa && item.consulta.toLowerCase() !== p.consulta.toLowerCase()));
      setEmpresasSalvasIds((prev) => new Set([...prev, p.idPesquisa]));
      setPageData((prev) => ({ ...prev, totalElements: Math.max(0, prev.totalElements - 1) }));

      if (pesquisaDetalhe?.idPesquisa === p.idPesquisa) {
        setPesquisaDetalhe(null);
      }

      toast.success(`"${p.consulta}" salva com sucesso! Movida para a aba Empresas.`);
    } catch (err) {
      toast.error(err.message || 'Erro ao salvar empresa');
    } finally {
      setSalvandoId(null);
    }
  };

  /**
   * Exclui uma busca registrada no banco Neon
   * Caso excluída, só voltará a aparecer se o usuário buscar novamente
   */
  const handleExcluirPesquisa = async (idPesquisa) => {
    if (!confirm('Deseja realmente excluir esta busca do banco de dados?')) return;
    try {
      await api.pesquisas.excluir(idPesquisa);
      setPesquisas((prev) => prev.filter((p) => p.idPesquisa !== idPesquisa));
      setPageData((prev) => ({ ...prev, totalElements: Math.max(0, prev.totalElements - 1) }));
      if (pesquisaDetalhe?.idPesquisa === idPesquisa) {
        setPesquisaDetalhe(null);
      }
      toast.success('Busca excluída com sucesso do banco');
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
      {/* Top Banner com Botão Direto de Busca (SEM abrir outra tela) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Busca
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Estabelecimentos comerciais sem website em <strong>Laranjeiras do Sul, Rio Bonito do Iguaçu, Virmond, Cantagalo e Nova Laranjeiras</strong>.
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
              <span>Buscando no OpenStreetMap...</span>
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
                      className="hover:bg-slate-50/70 dark:hover:bg-vertex-dark-surface/40 transition-colors duration-150 cursor-pointer"
                      onClick={() => setPesquisaDetalhe(p)}
                    >
                      {/* Empresa */}
                      <td className="py-4 px-5 font-semibold text-vertex-dark dark:text-vertex-dark-text">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                          <span>{p.consulta}</span>
                        </div>
                      </td>

                      {/* Região & Endereço */}
                      <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                        <div className="flex items-center gap-1.5">
                          <MapPin className="w-3 h-3 text-red-500 shrink-0" />
                          <span className="truncate max-w-xs">{p.regiao || 'Região Local'}</span>
                        </div>
                      </td>

                      {/* Presença Web / Status */}
                      <td className="py-4 px-5">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800/40">
                          <Globe2 className="w-3 h-3" />
                          Sem Website
                        </span>
                      </td>

                      {/* Data */}
                      <td className="py-4 px-5 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                        {formatDate(p.criadoEm)}
                      </td>

                      {/* ID */}
                      <td className="py-4 px-5 text-right font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                        {p.idPesquisa}
                      </td>

                      {/* Ações: Salvar Empresa & Excluir Busca */}
                      <td className="py-4 px-5 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-1.5">
                          {jaSalva ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Salva</span>
                            </span>
                          ) : (
                            <button
                              onClick={() => handleSalvarEmpresa(p)}
                              disabled={isSalvando}
                              title="Salvar esta empresa na aba Empresas"
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-95 text-white shadow-sm hover:shadow-glow-orange transition-all duration-150 disabled:opacity-50"
                            >
                              <BookmarkPlus className="w-3.5 h-3.5" />
                              <span>{isSalvando ? 'Salvando...' : 'Salvar Empresa'}</span>
                            </button>
                          )}

                          <button
                            onClick={() => handleExcluirPesquisa(p.idPesquisa)}
                            title="Excluir esta busca"
                            className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-950/40 text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 dark:hover:text-red-400 active:scale-95 transition-all duration-150"
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

      {/* Modal de Detalhes da Pesquisa ao clicar na linha */}
      {pesquisaDetalhe && (() => {
        const osmDetalhe = osmCache[pesquisaDetalhe.idPesquisa] || pesquisaDetalhe;
        const jaSalva = empresasSalvasIds.has(pesquisaDetalhe.idPesquisa);
        const isSalvando = salvandoId === pesquisaDetalhe.idPesquisa;

        return (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 dark:bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
            <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal rounded-xl max-w-lg w-full p-6 space-y-4 transition-colors">
              <div className="flex items-center justify-between pb-3 border-b border-vertex-border dark:border-vertex-dark-border">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-md bg-orange-50 dark:bg-orange-950/40 text-vertex-orange">
                    <Building2 className="w-4 h-4" />
                  </div>
                  <div>
                    <h2 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                      Detalhes da Prospecção
                    </h2>
                    <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                      Dados mapeados via OpenStreetMap
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => setPesquisaDetalhe(null)}
                  className="p-1 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150 cursor-pointer"
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
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Empresa / Estabelecimento:</span>
                  <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text text-sm">{pesquisaDetalhe.consulta}</span>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Categoria:</span>
                    <span className="text-vertex-body dark:text-vertex-dark-text font-medium">
                      {osmDetalhe.categoria || 'Comércio Local'}
                    </span>
                  </div>
                  <div>
                    <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Telefone:</span>
                    <span className="text-vertex-body dark:text-vertex-dark-text font-medium">
                      {osmDetalhe.telefone || 'Não informado no mapa'}
                    </span>
                  </div>
                </div>

                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Região & Localização:</span>
                  <span className="text-vertex-body dark:text-vertex-dark-text">
                    {osmDetalhe.endereco || pesquisaDetalhe.regiao || 'Localidade não informada'}
                  </span>
                </div>

                <div className="flex items-center justify-between p-2.5 rounded-md bg-slate-50 dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border">
                  <div>
                    <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[10px] uppercase font-bold tracking-wider">Presença Digital</span>
                    <span className="inline-flex items-center gap-1 text-[11px] font-medium text-amber-600 dark:text-amber-400 mt-0.5">
                      <Globe2 className="w-3 h-3" />
                      Sem Website Registrado
                    </span>
                  </div>
                  {osmDetalhe.googleMapsUrl && (
                    <a
                      href={osmDetalhe.googleMapsUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-[11px] font-semibold bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border text-vertex-dark dark:text-vertex-dark-text hover:text-vertex-orange transition-colors"
                    >
                      <MapPin className="w-3 h-3 text-vertex-orange" />
                      <span>Google Maps</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </div>

                <div>
                  <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px]">Data da Prospecção:</span>
                  <span className="font-mono text-vertex-muted dark:text-vertex-dark-muted">{formatDate(pesquisaDetalhe.criadoEm)}</span>
                </div>
              </div>

              <div className="flex items-center justify-between pt-3 border-t border-vertex-border dark:border-vertex-dark-border">
                <button
                  onClick={() => handleExcluirPesquisa(pesquisaDetalhe.idPesquisa)}
                  className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-md text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 active:scale-95 transition-all duration-150 cursor-pointer"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Excluir Busca</span>
                </button>

                <div className="flex items-center gap-2">
                  {!jaSalva && (
                    <button
                      onClick={() => handleSalvarEmpresa(pesquisaDetalhe)}
                      disabled={isSalvando}
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md text-xs font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-95 text-white shadow-sm transition-all duration-150 disabled:opacity-50 cursor-pointer"
                    >
                      <BookmarkPlus className="w-3.5 h-3.5" />
                      <span>{isSalvando ? 'Salvando...' : 'Salvar Empresa'}</span>
                    </button>
                  )}

                  <button
                    onClick={() => setPesquisaDetalhe(null)}
                    className="px-4 py-2 text-xs font-medium rounded-md border border-vertex-border dark:border-vertex-dark-border text-vertex-dark dark:text-vertex-dark-text hover:bg-slate-50 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150 cursor-pointer"
                  >
                    Fechar
                  </button>
                </div>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
};
