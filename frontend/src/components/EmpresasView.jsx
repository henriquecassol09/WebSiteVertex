import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { EmpresaDetalhe } from './EmpresaDetalhe';
import { 
  Search, 
  Trash2, 
  Info,
  ChevronLeft, 
  ChevronRight,
  Filter,
  ExternalLink,
  AlertCircle,
  X,
  Phone,
  Mail,
  ArrowUpRight,
  Building2,
  MapPin,
  Globe2,
  Check,
  RotateCcw
} from 'lucide-react';
import { detectarCategoria } from '../utils/categoriaHelper';
import { sanitizarUrl } from '../utils/security';

export const EmpresasView = () => {
  const [empresas, setEmpresas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [pageData, setPageData] = useState({ totalElements: 0, totalPages: 1 });

  const toast = useToast();

  const handleCopiarTelefone = (e, telefone) => {
    e.stopPropagation();
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

  const gerarLinkGmail = (email, nomeEmpresa) => {
    if (!email || !email.includes('@') || email.includes('empresa.com.br')) return null;
    const assunto = encodeURIComponent(`Contato Comercial - ${nomeEmpresa || ''}`);
    return `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email.trim())}&su=${assunto}`;
  };
  const [mostrarFiltros, setMostrarFiltros] = useState(false);
  const [termoBusca, setTermoBusca] = useState('');
  const [filtroTexto, setFiltroTexto] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [filtroCidades, setFiltroCidades] = useState([]);
  const [filtroWebsite, setFiltroWebsite] = useState('todos');
  const [filtroTelefone, setFiltroTelefone] = useState('todos');
  const [filtroEmail, setFiltroEmail] = useState('todos');
  const [showToast, setShowToast] = useState(false);

  useEffect(() => {
    if (showToast) {
      const timer = setTimeout(() => {
        setShowToast(false);
      }, 3500);
      return () => clearTimeout(timer);
    }
  }, [showToast]);

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
    setFiltroCategoria('');
    setFiltroWebsite('todos');
    setFiltroTelefone('todos');
    setFiltroEmail('todos');
    setTermoBusca('');
    setFiltroTexto('');
  };

  const filtrosAtivosCount = 
    (filtroCidades.length > 0 ? 1 : 0) +
    (filtroCategoria !== '' ? 1 : 0) +
    (filtroWebsite !== 'todos' ? 1 : 0) +
    (filtroTelefone !== 'todos' ? 1 : 0) +
    (filtroEmail !== 'todos' ? 1 : 0) +
    (filtroTexto.trim() !== '' ? 1 : 0);

  const handleBuscar = () => {
    const texto = termoBusca.trim();
    setFiltroTexto(texto);

    if (texto !== '') {
      const temResultados = empresas.some((emp) => {
        const cat = detectarCategoria(emp.categoria, emp.nome);
        const matchTexto =
          emp.nome?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.cidade?.toLowerCase().includes(texto.toLowerCase()) ||
          cat?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.email?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.cnpj?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.telefone?.toLowerCase().includes(texto.toLowerCase()) ||
          emp.endereco?.toLowerCase().includes(texto.toLowerCase());

        const matchCategoria =
          filtroCategoria === '' || cat === filtroCategoria;

        return matchTexto && matchCategoria;
      });

      setShowToast(!temResultados);
    } else {
      setShowToast(false);
    }
  };

  // Detalhes da Empresa
  const [empresaSelecionada, setEmpresaSelecionada] = useState(null);

  useEffect(() => {
    carregarEmpresas();
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
    } catch {
      toast.error('Erro ao excluir empresa');
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

  const empresasFiltradas = empresas.filter((emp) => {
    const cat = detectarCategoria(emp.categoria, emp.nome);

    if (filtroTexto.trim() !== '') {
      const termo = filtroTexto.toLowerCase().trim();
      const matchTexto =
        emp.nome?.toLowerCase().includes(termo) ||
        emp.cidade?.toLowerCase().includes(termo) ||
        cat?.toLowerCase().includes(termo) ||
        emp.email?.toLowerCase().includes(termo) ||
        emp.cnpj?.toLowerCase().includes(termo) ||
        emp.telefone?.toLowerCase().includes(termo) ||
        emp.endereco?.toLowerCase().includes(termo);
      if (!matchTexto) return false;
    }

    if (filtroCategoria !== '' && cat !== filtroCategoria) {
      return false;
    }

    if (filtroCidades.length > 0) {
      const matchCidade = filtroCidades.some((c) => {
        const cLow = c.toLowerCase().trim();
        return (
          emp.cidade?.toLowerCase().includes(cLow) ||
          emp.endereco?.toLowerCase().includes(cLow)
        );
      });
      if (!matchCidade) return false;
    }

    const temSite = Boolean(emp.site && emp.site.trim() !== '');
    if (filtroWebsite === 'com' && !temSite) return false;
    if (filtroWebsite === 'sem' && temSite) return false;

    const temTelefone = Boolean(
      emp.telefone && 
      emp.telefone.toLowerCase() !== 'não informado' && 
      emp.telefone.trim() !== ''
    );
    if (filtroTelefone === 'com' && !temTelefone) return false;
    if (filtroTelefone === 'sem' && temTelefone) return false;

    const temEmail = Boolean(
      emp.email && 
      emp.email.toLowerCase() !== 'não informado' && 
      emp.email.includes('@') && 
      !emp.email.includes('empresa.com.br')
    );
    if (filtroEmail === 'com' && !temEmail) return false;
    if (filtroEmail === 'sem' && temEmail) return false;

    return true;
  });

  const categoriasUnicas = Array.from(
    new Set(empresas.map((e) => detectarCategoria(e.categoria, e.nome)).filter(Boolean))
  );

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-vertex-dark dark:text-vertex-dark-text">
            Empresas
          </h1>
          <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted mt-1 leading-relaxed">
            Informações das empresas salvas
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
              placeholder="Buscar por nome, CNPJ, cidade, telefone, e-mail..."
              className="w-full h-9 pl-9 pr-8 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text placeholder:text-slate-400 dark:placeholder:text-slate-500/70 focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            />
            {termoBusca && (
              <button
                type="button"
                onClick={() => {
                  setTermoBusca('');
                  setFiltroTexto('');
                }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text p-0.5 rounded cursor-pointer"
                title="Limpar busca"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          <button
            type="button"
            onClick={handleBuscar}
            className="flex items-center justify-center gap-1.5 h-9 px-4 rounded-md text-xs font-semibold bg-vertex-orange hover:bg-vertex-orange-hover active:scale-[0.98] text-white transition-all duration-200 shadow-sm hover:shadow-glow-orange shrink-0 cursor-pointer"
          >
            <Search className="w-3.5 h-3.5" />
            <span>Pesquisar</span>
          </button>

          {/* Botão de Filtros Retráteis */}
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

        {/* Categoria rápida na barra e resumo de exibição */}
        <div className="flex items-center gap-3">
          {categoriasUnicas.length > 0 && (
            <div className="flex items-center gap-2">
              <Building2 className="w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted shrink-0" />
              <select
                value={filtroCategoria}
                onChange={(e) => setFiltroCategoria(e.target.value)}
                className="h-9 px-3 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200 cursor-pointer"
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

          <div className="text-xs text-vertex-muted dark:text-vertex-dark-muted hidden md:block shrink-0">
            Exibindo <strong className="text-vertex-dark dark:text-vertex-dark-text">{empresasFiltradas.length}</strong> de <strong className="text-vertex-dark dark:text-vertex-dark-text">{empresas.length}</strong>
          </div>
        </div>
      </div>

      {/* Painel de Filtros Retrátil */}
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

          {/* Opção 1: Cidades (Multi-seleção) */}
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

          {/* Opções 2, 3 e 4: Website, Telefone e E-mail */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5 pt-2 border-t border-vertex-border/50 dark:border-vertex-dark-border/50">
            {/* Website */}
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

            {/* Telefone */}
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

            {/* E-mail */}
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
                <th className="py-3.5 px-5 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-vertex-border dark:divide-vertex-dark-border">
              {loading ? (
                <tr>
                  <td colSpan="7" className="py-12 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
                    Carregando empresas...
                  </td>
                </tr>
              ) : empresasFiltradas.length === 0 ? (
                <tr>
                  <td colSpan="7" className="py-16 text-center text-xs text-vertex-muted dark:text-vertex-dark-muted">
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
                      <div>{emp.nome}</div>
                      {emp.cnpj && (
                        <div className="font-mono text-[10px] text-purple-700 dark:text-purple-400 font-medium mt-0.5">
                          CNPJ: {formatarCnpj(emp.cnpj)}
                        </div>
                      )}
                    </td>

                    {/* Categoria */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text font-medium">
                      {detectarCategoria(emp.categoria, emp.nome)}
                    </td>

                    {/* Cidade e Estado */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {[emp.cidade, emp.estado].filter(Boolean).join(' - ') || '-'}
                    </td>

                    {/* Telefone */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text font-mono text-[11px] whitespace-nowrap">
                      {emp.telefone && emp.telefone.toLowerCase() !== 'não informado' ? (
                        <button
                          type="button"
                          onClick={(e) => handleCopiarTelefone(e, emp.telefone)}
                          title="Clique para copiar o número de telefone"
                          className="inline-flex items-center gap-1.5 text-vertex-dark dark:text-vertex-dark-text hover:text-emerald-600 dark:hover:text-emerald-400 font-semibold transition-colors group cursor-pointer"
                        >
                          <Phone className="w-3 h-3 text-emerald-600 dark:text-emerald-400 group-hover:scale-110 transition-transform shrink-0" />
                          <span className="underline decoration-emerald-500/30 group-hover:decoration-emerald-500">{emp.telefone}</span>
                        </button>
                      ) : (
                        <span className="text-vertex-muted dark:text-vertex-dark-muted">-</span>
                      )}
                    </td>

                    {/* E-mail */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {emp.email && emp.email.toLowerCase() !== 'não informado' && emp.email.includes('@') && !emp.email.includes('empresa.com.br') ? (
                        <a
                          href={gerarLinkGmail(emp.email, emp.nome)}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => e.stopPropagation()}
                          title="Clique para abrir diretamente no Gmail"
                          className="inline-flex items-center gap-1.5 text-red-600 dark:text-red-400 hover:text-red-500 dark:hover:text-red-300 hover:underline transition-colors truncate max-w-[200px] cursor-pointer"
                        >
                          <Mail className="w-3 h-3 shrink-0" />
                          <span className="truncate">{emp.email}</span>
                          <ArrowUpRight className="w-2.5 h-2.5 shrink-0" />
                        </a>
                      ) : (
                        <span className="text-vertex-muted dark:text-vertex-dark-muted">Não informado</span>
                      )}
                    </td>

                    {/* Site */}
                    <td className="py-4 px-5 text-vertex-body dark:text-vertex-dark-text">
                      {emp.site ? (
                        <a
                          href={sanitizarUrl(emp.site)}
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

                    {/* Ações */}
                    <td className="py-4 px-5 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => setEmpresaSelecionada(emp)}
                          title="Ver informações detalhadas da empresa"
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md bg-vertex-orange/10 dark:bg-vertex-orange/15 hover:bg-vertex-orange hover:text-white text-vertex-orange active:scale-95 text-[11px] font-semibold border border-vertex-orange/20 hover:border-vertex-orange transition-all duration-150 shadow-sm cursor-pointer"
                        >
                          <Info className="w-3.5 h-3.5 shrink-0" />
                          <span>Informações</span>
                        </button>
                        <button
                          onClick={() => handleExcluir(emp.idEmpresa)}
                          title="Excluir"
                          className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-950/40 text-vertex-muted dark:text-vertex-dark-muted hover:text-red-600 dark:hover:text-red-400 active:scale-95 transition-all duration-150 cursor-pointer"
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

      {/* Detalhe da Empresa (somente leitura de informações) */}
      {empresaSelecionada && (
        <EmpresaDetalhe
          empresa={empresaSelecionada}
          onClose={() => setEmpresaSelecionada(null)}
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
