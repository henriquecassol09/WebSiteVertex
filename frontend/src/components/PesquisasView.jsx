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
  Globe2,
  Phone,
  Mail,
  FileText
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

  const formatarCnpj = (cnpj) => {
    if (!cnpj) return null;
    const limpo = String(cnpj).replace(/\D/g, '');
    if (limpo.length === 14) {
      return limpo.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
    }
    return cnpj;
  };

  const handleCopiarTelefone = (e, telefone) => {
    if (e) e.stopPropagation();
    if (!telefone) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(telefone).then(() => {
        toast.success(`Telefone ${telefone} copiado com sucesso!`);
      }).catch(() => {
        copiarFallback(telefone);
      });
    } else {
      copiarFallback(telefone);
    }
  };

  const copiarFallback = (texto) => {
    try {
      const el = document.createElement('textarea');
      el.value = texto;
      el.setAttribute('readonly', '');
      el.style.position = 'absolute';
      el.style.left = '-9999px';
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      toast.success(`Telefone ${texto} copiado com sucesso!`);
    } catch {
      toast.error('Erro ao copiar telefone');
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

      const emailValido = p.email && p.email !== 'Não informado' && !p.email.includes('empresa.com.br')
        ? p.email
        : 'Não informado';
      const telefoneValido = p.telefone && p.telefone !== 'Não informado'
        ? p.telefone
        : 'Não informado';

      const payload = {
        nome: p.consulta,
        categoria: categoria,
        cidade: cidade,
        estado: estado,
        endereco: endereco,
        telefone: telefoneValido,
        email: emailValido,
        site: p.site || '',
        descricao: p.site 
          ? `Atividade: ${categoria}. Horário de funcionamento: ${horario}.`
          : `Atividade: ${categoria}. Horário de funcionamento: ${horario}. Sem website registrado.`,
        cnpj: p.cnpj || null,
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
            Realize buscas para encontrar estabelecimentos comerciais em <strong>Laranjeiras do Sul, Rio Bonito do Iguaçu, Porto Barreiro, Virmond, Cantagalo e Nova Laranjeiras</strong>.
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
        <div className="overflow-x-auto lg:overflow-x-visible">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60 text-slate-500 dark:text-slate-400 font-semibold text-[11px] uppercase tracking-wider">
                <th className="py-3 px-3.5 xl:px-4">Empresa / Estabelecimento</th>
                <th className="py-3 px-3.5 xl:px-4">Região & Endereço</th>
                <th className="py-3 px-3.5 xl:px-4 whitespace-nowrap">Contato & CNPJ</th>
                <th className="py-3 px-3.5 xl:px-4 whitespace-nowrap">Presença Web</th>
                <th className="py-3 px-3.5 xl:px-4 whitespace-nowrap">Data</th>
                <th className="py-3 px-3.5 xl:px-4 text-right whitespace-nowrap">Ação</th>
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
                      <td className="py-3.5 px-3.5 xl:px-4 font-semibold text-vertex-dark dark:text-vertex-dark-text select-text">
                        <div className="flex items-start gap-2">
                          <Building2 className="w-3.5 h-3.5 text-vertex-orange shrink-0 mt-0.5" />
                          <div>
                            <span className="block leading-snug">{p.consulta}</span>
                            <div className="flex items-center gap-1.5 mt-1">
                              <span className="text-[10px] font-medium text-vertex-orange bg-vertex-orange/10 px-1.5 py-0.5 rounded">
                                {detectarCategoria(p.categoria, p.consulta)}
                              </span>
                              <span className="text-[10px] font-mono text-vertex-muted dark:text-vertex-dark-muted px-1.5 py-0.5 rounded bg-slate-100 dark:bg-vertex-dark-surface" title={`ID da Consulta: ${p.idPesquisa}`}>
                                #{p.idPesquisa}
                              </span>
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Endereço & Cidade/Estado */}
                      <td className="py-3.5 px-3.5 xl:px-4 text-vertex-body dark:text-vertex-dark-text select-text">
                        <div className="flex items-start gap-1.5">
                          <MapPin className="w-3 h-3 text-red-500 shrink-0 mt-0.5" />
                          <div className="max-w-[200px] xl:max-w-xs">
                            <span className="block font-medium truncate">{p.endereco || 'Centro'}</span>
                            <span className="block text-[11px] text-vertex-muted dark:text-vertex-dark-muted truncate">
                              {[p.cidade, p.estado].filter(Boolean).join(' - ') || p.regiao || 'Paraná'}
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* Contato & CNPJ (Sem quebra de linha no CNPJ e Telefone no PC) */}
                      <td className="py-3.5 px-3.5 xl:px-4 text-vertex-body dark:text-vertex-dark-text select-text">
                        <div className="space-y-1">
                          {p.cnpj ? (
                            <div className="inline-flex items-center gap-1 font-mono text-[11px] font-semibold text-purple-700 dark:text-purple-300 bg-purple-50 dark:bg-purple-950/40 px-2 py-0.5 rounded border border-purple-200 dark:border-purple-800/40 whitespace-nowrap">
                              <FileText className="w-3 h-3 text-purple-600 dark:text-purple-400 shrink-0" />
                              <span className="whitespace-nowrap">{formatarCnpj(p.cnpj)}</span>
                            </div>
                          ) : (
                            <span className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted block whitespace-nowrap">
                              Não localizado
                            </span>
                          )}

                          {(p.telefone && p.telefone !== 'Não informado') || (p.email && p.email !== 'Não informado' && !p.email.includes('empresa.com.br')) ? (
                            <div className="flex flex-col gap-0.5 text-[11px]">
                              {p.telefone && p.telefone !== 'Não informado' && (
                                <button
                                  type="button"
                                  onClick={(e) => handleCopiarTelefone(e, p.telefone)}
                                  title="Clique para copiar o número de telefone"
                                  className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-400 font-medium whitespace-nowrap hover:text-vertex-orange dark:hover:text-vertex-orange hover:underline cursor-pointer text-left transition-colors"
                                >
                                  <Phone className="w-3 h-3 shrink-0" />
                                  <span className="whitespace-nowrap">{p.telefone}</span>
                                </button>
                              )}
                              {p.email && p.email !== 'Não informado' && !p.email.includes('empresa.com.br') && (
                                <a
                                  href={`https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(p.email.trim())}&su=${encodeURIComponent(`Contato Comercial - ${p.consulta || ''}`)}`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  title="Clique para abrir diretamente no Gmail"
                                  className="inline-flex items-center gap-1 text-red-600 dark:text-red-400 hover:text-red-500 hover:underline truncate max-w-[180px] xl:max-w-[220px] cursor-pointer transition-colors"
                                >
                                  <Mail className="w-3 h-3 shrink-0" />
                                  <span className="truncate">{p.email}</span>
                                </a>
                              )}
                            </div>
                          ) : null}
                        </div>
                      </td>

                      {/* Presença Web / Status */}
                      <td className="py-3.5 px-3.5 xl:px-4">
                        {p.site ? (
                          <a
                            href={p.site.startsWith('http') ? p.site : `https://${p.site}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40 hover:underline max-w-[140px] truncate whitespace-nowrap"
                            title={p.site}
                          >
                            <Globe2 className="w-3 h-3 shrink-0" />
                            <span className="truncate">{p.site.replace(/^https?:\/\/(www\.)?/, '')}</span>
                          </a>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800/40 select-none whitespace-nowrap">
                            <Globe2 className="w-3 h-3" />
                            Sem Website
                          </span>
                        )}
                      </td>

                      {/* Data */}
                      <td className="py-3.5 px-3.5 xl:px-4 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted select-text whitespace-nowrap">
                        {formatDate(p.criadoEm)}
                      </td>

                      {/* Ações ÚNICAS clicáveis: Salvar Empresa (ícone BookmarkPlus) & Excluir Busca (ícone Trash2) */}
                      <td className="py-3.5 px-3.5 xl:px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5 whitespace-nowrap">
                          {jaSalva ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[11px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40 select-none whitespace-nowrap">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Salva</span>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleSalvarEmpresa(p)}
                              disabled={isSalvando}
                              title="Salvar esta empresa na aba Empresas"
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-95 text-white shadow-sm hover:shadow-glow-orange transition-all duration-150 disabled:opacity-50 cursor-pointer whitespace-nowrap"
                            >
                              <BookmarkPlus className="w-3.5 h-3.5" />
                              <span>{isSalvando ? 'Salvando...' : 'Salvar'}</span>
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
