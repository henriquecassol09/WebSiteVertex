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
  FileText,
  Filter,
  X,
  Check,
  RotateCcw,
  Clock
} from 'lucide-react';
import { detectarCategoria } from '../utils/categoriaHelper';
import { sanitizarUrl } from '../utils/security';

export const PesquisasView = () => {
  const [pesquisas, setPesquisas] = useState([]);
  const [empresasSalvasIds, setEmpresasSalvasIds] = useState(new Set());
  const [salvandoId, setSalvandoId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [buscandoOverpass, setBuscandoOverpass] = useState(false);
  const [segundosDecorridos, setSegundosDecorridos] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 50;
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });

  const [mostrarFiltros, setMostrarFiltros] = useState(false);
  const [termoBusca, setTermoBusca] = useState('');
  const [filtroCidades, setFiltroCidades] = useState([]);
  const [filtroWebsite, setFiltroWebsite] = useState('todos');
  const [filtroTelefone, setFiltroTelefone] = useState('todos');
  const [filtroEmail, setFiltroEmail] = useState('todos');

  const toast = useToast();

  useEffect(() => {
    if (!buscandoOverpass) return;
    const interval = setInterval(() => {
      setSegundosDecorridos((s) => s + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [buscandoOverpass]);

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
    }
  };

  const handleRealizarBusca = async () => {
    setSegundosDecorridos(0);
    setBuscandoOverpass(true);
    toast.info('Busca iniciada. Estimativa de tempo: de 1 a 3 minutos.', 10000);

    try {
      await api.pesquisas.prospectarRegiao();
      toast.success('Busca concluída com sucesso!');
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

  const CIDADES_REGIAO = [
    'Laranjeiras do Sul',
    'Rio Bonito do Iguaçu',
    'Porto Barreiro',
    'Virmond',
    'Cantagalo',
    'Nova Laranjeiras'
  ];

  const cidadesDisponiveis = React.useMemo(() => {
    return CIDADES_REGIAO;
  }, []);

  const toggleCidade = (cidade) => {
    setFiltroCidades((prev) =>
      prev.includes(cidade) ? prev.filter((c) => c !== cidade) : [...prev, cidade]
    );
  };

  const selecionarTodasCidades = () => {
    if (filtroCidades.length === cidadesDisponiveis.length) {
      setFiltroCidades([]);
    } else {
      setFiltroCidades([...cidadesDisponiveis]);
    }
  };

  const limparFiltros = () => {
    setFiltroCidades([]);
    setFiltroWebsite('todos');
    setFiltroTelefone('todos');
    setFiltroEmail('todos');
    setTermoBusca('');
  };

  const filtrosAtivosCount = 
    (filtroCidades.length > 0 ? 1 : 0) +
    (filtroWebsite !== 'todos' ? 1 : 0) +
    (filtroTelefone !== 'todos' ? 1 : 0) +
    (filtroEmail !== 'todos' ? 1 : 0) +
    (termoBusca.trim() !== '' ? 1 : 0);

  const pesquisasFiltradas = pesquisas.filter((p) => {
    const cidadeNorm = (p.cidade || '').toLowerCase().trim();
    const pertenceCidadesCitadas = CIDADES_REGIAO.some((c) =>
      cidadeNorm.includes(c.toLowerCase()) || c.toLowerCase().includes(cidadeNorm)
    );
    if (!pertenceCidadesCitadas) return false;

    if (termoBusca.trim() !== '') {
      const termo = termoBusca.toLowerCase().trim();
      const matchTexto =
        p.consulta?.toLowerCase().includes(termo) ||
        p.cidade?.toLowerCase().includes(termo) ||
        p.regiao?.toLowerCase().includes(termo) ||
        p.endereco?.toLowerCase().includes(termo) ||
        p.cnpj?.toLowerCase().includes(termo) ||
        p.telefone?.toLowerCase().includes(termo) ||
        p.email?.toLowerCase().includes(termo);
      if (!matchTexto) return false;
    }

    if (filtroCidades.length > 0) {
      const matchCidade = filtroCidades.some((c) => {
        const cLow = c.toLowerCase().trim();
        return (
          p.cidade?.toLowerCase().includes(cLow) ||
          p.regiao?.toLowerCase().includes(cLow) ||
          p.endereco?.toLowerCase().includes(cLow)
        );
      });
      if (!matchCidade) return false;
    }

    const temSite = Boolean(p.site && p.site.trim() !== '');
    if (filtroWebsite === 'com' && !temSite) return false;
    if (filtroWebsite === 'sem' && temSite) return false;

    const temTelefone = Boolean(
      p.telefone && 
      p.telefone.toLowerCase() !== 'não informado' && 
      p.telefone.trim() !== ''
    );
    if (filtroTelefone === 'com' && !temTelefone) return false;
    if (filtroTelefone === 'sem' && temTelefone) return false;

    const temEmail = Boolean(
      p.email && 
      p.email.toLowerCase() !== 'não informado' && 
      p.email.includes('@') && 
      !p.email.includes('empresa.com.br')
    );
    if (filtroEmail === 'com' && !temEmail) return false;
    if (filtroEmail === 'sem' && temEmail) return false;

    return true;
  }).sort((a, b) => {
    const aIsPb = (a.cidade || a.regiao || '').toLowerCase().includes('porto barreiro');
    const bIsPb = (b.cidade || b.regiao || '').toLowerCase().includes('porto barreiro');
    if (aIsPb && !bIsPb) return 1;
    if (!aIsPb && bIsPb) return -1;
    return 0;
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Busca
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Realize buscas para encontrar estabelecimentos comerciais em <strong>Laranjeiras do Sul, Rio Bonito do Iguaçu, Porto Barreiro, Virmond, Cantagalo e Nova Laranjeiras</strong>.
          </p>
        </div>

        <button
          type="button"
          onClick={handleRealizarBusca}
          disabled={buscandoOverpass}
          className="flex items-center justify-center gap-2 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] transition-all duration-200 shadow-sm hover:shadow-glow-orange disabled:opacity-60 self-start sm:self-auto cursor-pointer shrink-0"
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

      {buscandoOverpass && (
        <div className="bg-orange-50/90 dark:bg-vertex-orange/10 border border-vertex-orange/30 dark:border-vertex-orange/40 rounded-lg p-4 sm:p-5 shadow-subtle dark:shadow-dark-subtle transition-all duration-300">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-md bg-vertex-orange/20 dark:bg-vertex-orange/30 flex items-center justify-center shrink-0">
                <Loader2 className="w-5 h-5 text-vertex-orange animate-spin" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                  Buscando novos estabelecimentos na região...
                </h3>
                <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-0.5 leading-relaxed">
                  Consultando dados geográficos e cadastrais em 6 cidades (Laranjeiras do Sul, Rio Bonito do Iguaçu, Porto Barreiro, Virmond, Cantagalo e Nova Laranjeiras).
                </p>
              </div>
            </div>
            <div className="flex items-center gap-1.5 text-xs font-mono font-medium text-vertex-orange self-end sm:self-center shrink-0 bg-vertex-orange/10 dark:bg-vertex-orange/20 px-2.5 py-1 rounded">
              <Clock className="w-3.5 h-3.5" />
              <span>Tempo de busca: {segundosDecorridos}s</span>
            </div>
          </div>

          <div className="w-full bg-vertex-orange/20 dark:bg-vertex-orange/20 h-1.5 rounded-full overflow-hidden mt-3.5">
            <div 
              className="bg-vertex-orange h-full rounded-full transition-all duration-1000 ease-out"
              style={{ width: `${Math.min(95, Math.max(5, (segundosDecorridos / 120) * 100))}%` }}
            />
          </div>
        </div>
      )}

      {/* Barra de Filtros e Busca Rápida */}
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg p-3 sm:p-3.5 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between shadow-subtle dark:shadow-dark-subtle transition-colors duration-700 ease-in-out">
        <div className="flex items-center gap-2 flex-1">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={termoBusca}
              onChange={(e) => setTermoBusca(e.target.value)}
              placeholder="Filtrar por nome, cidade, endereço ou CNPJ..."
              className="w-full h-9 pl-9 pr-8 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text placeholder:text-slate-400 dark:placeholder:text-slate-500/70 focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            />
            {termoBusca && (
              <button
                type="button"
                onClick={() => setTermoBusca('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text p-0.5 rounded cursor-pointer"
                title="Limpar busca"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={() => setMostrarFiltros((prev) => !prev)}
            className={`flex items-center gap-2 h-9 px-3.5 rounded-md text-xs font-semibold border transition-all duration-200 cursor-pointer shrink-0 ${
              mostrarFiltros || filtrosAtivosCount > 0
                ? 'bg-vertex-orange/10 dark:bg-vertex-orange/20 text-vertex-orange border-vertex-orange/40 dark:border-vertex-orange/50 shadow-xs'
                : 'bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text border-vertex-border dark:border-vertex-dark-border hover:bg-slate-50 dark:hover:bg-vertex-dark-card'
            }`}
          >
            <Filter className={`w-3.5 h-3.5 transition-transform ${mostrarFiltros ? 'rotate-180 text-vertex-orange' : ''}`} />
            <span>Filtros</span>
            {filtrosAtivosCount > 0 && (
              <span className="w-4 h-4 rounded-full bg-vertex-orange text-white text-[10px] font-bold flex items-center justify-center">
                {filtrosAtivosCount}
              </span>
            )}
          </button>
        </div>

        <div className="text-xs text-vertex-muted dark:text-vertex-dark-muted hidden md:block">
          Exibindo <strong className="text-vertex-dark dark:text-vertex-dark-text">{pesquisasFiltradas.length}</strong> de <strong className="text-vertex-dark dark:text-vertex-dark-text">{pesquisas.length}</strong>
        </div>
      </div>

      {/* Painel de Filtros */}
      {mostrarFiltros && (
        <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-lg p-4 sm:p-5 shadow-subtle dark:shadow-dark-subtle space-y-4 animate-in fade-in slide-in-from-top-2 duration-200 transition-colors">
          <div className="flex items-center justify-between border-b border-vertex-border/70 dark:border-vertex-dark-border/80 pb-3">
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-vertex-orange" />
              <h2 className="text-xs font-bold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
                Filtros Personalizados
              </h2>
              {filtrosAtivosCount > 0 && (
                <span className="text-[10px] bg-vertex-orange/10 dark:bg-vertex-orange/20 text-vertex-orange font-semibold px-2 py-0.5 rounded-full">
                  {filtrosAtivosCount} ativo{filtrosAtivosCount > 1 ? 's' : ''}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              {filtrosAtivosCount > 0 && (
                <button
                  type="button"
                  onClick={limparFiltros}
                  className="text-[11px] font-medium text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 dark:hover:text-red-400 transition-colors inline-flex items-center gap-1 cursor-pointer"
                >
                  <RotateCcw className="w-3 h-3" />
                  <span>Limpar todos os filtros</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => setMostrarFiltros(false)}
                className="p-1 rounded-md text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors cursor-pointer"
                title="Fechar filtros"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text inline-flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-vertex-orange" />
                <span>Cidades ({filtroCidades.length === 0 ? 'Todas as cidades selecionadas' : `${filtroCidades.length} selecionada(s)`})</span>
              </span>
              <button
                type="button"
                onClick={selecionarTodasCidades}
                className="text-[11px] font-medium text-vertex-orange hover:underline cursor-pointer"
              >
                {filtroCidades.length === cidadesDisponiveis.length ? 'Desmarcar todas' : 'Selecionar todas'}
              </button>
            </div>

            <div className="flex flex-wrap gap-2 pt-1">
              {cidadesDisponiveis.map((cidade) => {
                const isSelected = filtroCidades.includes(cidade);
                return (
                  <button
                    key={cidade}
                    type="button"
                    onClick={() => toggleCidade(cidade)}
                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-150 cursor-pointer border ${
                      isSelected
                        ? 'bg-vertex-orange text-white border-vertex-orange shadow-xs font-semibold'
                        : 'bg-slate-50 dark:bg-vertex-dark-surface text-vertex-body dark:text-vertex-dark-text border-vertex-border dark:border-vertex-dark-border hover:border-vertex-orange/50 dark:hover:border-vertex-orange/50'
                    }`}
                  >
                    <span className={`w-3.5 h-3.5 rounded flex items-center justify-center border text-[9px] transition-colors ${
                      isSelected ? 'border-white bg-white/20 text-white' : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-vertex-dark-card'
                    }`}>
                      {isSelected && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                    </span>
                    <span>{cidade}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5 pt-2 border-t border-vertex-border/50 dark:border-vertex-dark-border/50">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text inline-flex items-center gap-1.5">
                <Globe2 className="w-3.5 h-3.5 text-blue-500" />
                <span>Website</span>
              </label>
              <div className="grid grid-cols-3 gap-1 bg-slate-50/80 dark:bg-vertex-dark-surface p-1 rounded-md border border-vertex-border dark:border-vertex-dark-border">
                {[
                  { id: 'todos', label: 'Todos' },
                  { id: 'com', label: 'Com site' },
                  { id: 'sem', label: 'Sem site' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setFiltroWebsite(opt.id)}
                    className={`py-1 text-[11px] font-medium rounded transition-all cursor-pointer ${
                      filtroWebsite === opt.id
                        ? 'bg-vertex-orange text-white font-semibold shadow-xs'
                        : 'text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text inline-flex items-center gap-1.5">
                <Phone className="w-3.5 h-3.5 text-emerald-600" />
                <span>Telefone</span>
              </label>
              <div className="grid grid-cols-3 gap-1 bg-slate-50/80 dark:bg-vertex-dark-surface p-1 rounded-md border border-vertex-border dark:border-vertex-dark-border">
                {[
                  { id: 'todos', label: 'Todos' },
                  { id: 'com', label: 'Com tel' },
                  { id: 'sem', label: 'Sem tel' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setFiltroTelefone(opt.id)}
                    className={`py-1 text-[11px] font-medium rounded transition-all cursor-pointer ${
                      filtroTelefone === opt.id
                        ? 'bg-vertex-orange text-white font-semibold shadow-xs'
                        : 'text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text inline-flex items-center gap-1.5">
                <Mail className="w-3.5 h-3.5 text-red-500" />
                <span>E-mail</span>
              </label>
              <div className="grid grid-cols-3 gap-1 bg-slate-50/80 dark:bg-vertex-dark-surface p-1 rounded-md border border-vertex-border dark:border-vertex-dark-border">
                {[
                  { id: 'todos', label: 'Todos' },
                  { id: 'com', label: 'Com email' },
                  { id: 'sem', label: 'Sem email' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setFiltroEmail(opt.id)}
                    className={`py-1 text-[11px] font-medium rounded transition-all cursor-pointer ${
                      filtroEmail === opt.id
                        ? 'bg-vertex-orange text-white font-semibold shadow-xs'
                        : 'text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tabela de Empresas Encontradas */}
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
                    Nenhuma busca realizada ainda. Clique no botão <strong>"Realizar Busca"</strong> acima para carregar as empresas da sua região.
                  </td>
                </tr>
              ) : pesquisasFiltradas.length === 0 ? (
                <tr>
                  <td colSpan="6" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted space-y-2">
                    <div>Nenhuma empresa encontrada com os filtros selecionados.</div>
                    {filtrosAtivosCount > 0 && (
                      <button
                        type="button"
                        onClick={limparFiltros}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold bg-slate-100 dark:bg-vertex-dark-surface text-vertex-orange hover:bg-slate-200 transition-colors cursor-pointer"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        <span>Limpar Filtros</span>
                      </button>
                    )}
                  </td>
                </tr>
              ) : (
                pesquisasFiltradas.map((p) => {
                  const jaSalva = empresasSalvasIds.has(p.idPesquisa);
                  const isSalvando = salvandoId === p.idPesquisa;

                  return (
                    <tr
                      key={p.idPesquisa}
                      className="hover:bg-slate-50/50 dark:hover:bg-vertex-dark-surface/30 transition-colors duration-150"
                    >
                      <td className="py-3.5 px-3.5 xl:px-4 font-semibold text-vertex-dark dark:text-vertex-dark-text select-text">
                        <div className="flex items-start gap-2">
                          <Building2 className="w-3.5 h-3.5 text-vertex-orange shrink-0 mt-0.5" />
                          <div>
                            <span className="block leading-snug">{p.consulta}</span>
                            <div className="flex items-center gap-1.5 mt-1">
                              <span className="text-[10px] font-medium text-vertex-orange bg-vertex-orange/10 px-1.5 py-0.5 rounded">
                                {detectarCategoria(p.categoria, p.consulta)}
                              </span>
                            </div>
                          </div>
                        </div>
                      </td>

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

                      <td className="py-3.5 px-3.5 xl:px-4">
                        {p.site ? (
                          <a
                            href={sanitizarUrl(p.site)}
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

                      <td className="py-3.5 px-3.5 xl:px-4 font-mono text-[11px] text-vertex-muted dark:text-vertex-dark-muted select-text whitespace-nowrap">
                        {formatDate(p.criadoEm)}
                      </td>

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

        <div className="flex items-center justify-between px-5 py-3.5 border-t border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 text-xs text-vertex-muted dark:text-vertex-dark-muted">
          <div>
            Total: <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text">{pesquisasFiltradas.length}</span> empresas {filtrosAtivosCount > 0 && `(filtradas de ${pesquisas.length})`}
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
