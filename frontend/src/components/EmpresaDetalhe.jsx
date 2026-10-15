import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../services/api';
import { useToast } from '../context/ToastContext';
import { 
  X, 
  Building2, 
  MapPin, 
  Phone, 
  Mail, 
  Globe, 
  FileText, 
  Plus, 
  Trash2, 
  Clock, 
  ArrowUpRight,
  Send,
  MessageSquare,
  Copy,
  Check
} from 'lucide-react';
import { detectarCategoria } from '../utils/categoriaHelper';

export const EmpresaDetalhe = ({ empresa, onClose }) => {
  const [notas, setNotas] = useState([]);
  const [loadingNotas, setLoadingNotas] = useState(false);
  const [novaNota, setNovaNota] = useState('');
  const [salvandoNota, setSalvandoNota] = useState(false);
  const [copiadoTelefone, setCopiadoTelefone] = useState(false);
  const toast = useToast();

  const handleCopiarTelefone = (e) => {
    if (e) e.stopPropagation();
    if (!empresa?.telefone) return;
    const texto = empresa.telefone;

    const sucesso = () => {
      setCopiadoTelefone(true);
      toast.success(`Número ${texto} copiado para a área de transferência!`);
      setTimeout(() => setCopiadoTelefone(false), 2500);
    };

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(texto).then(sucesso).catch(() => fallbackCopiar(texto, sucesso));
    } else {
      fallbackCopiar(texto, sucesso);
    }
  };

  const fallbackCopiar = (texto, callback) => {
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
      callback();
    } catch {
      toast.error('Não foi possível copiar automaticamente');
    }
  };

  const gerarLinkGmail = (email) => {
    if (!email || !email.includes('@') || email.includes('empresa.com.br')) return null;
    const assunto = encodeURIComponent(`Contato Comercial - ${empresa?.nome || ''}`);
    return `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(email.trim())}&su=${assunto}`;
  };

  useEffect(() => {
    if (empresa?.idEmpresa) {
      carregarNotas();
    }
  }, [empresa?.idEmpresa]);

  // Bloquear scroll da página enquanto a janela de informações estiver aberta
  useEffect(() => {
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, []);

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

  const categoriaEfetiva = detectarCategoria(empresa.categoria, empresa.nome);
  const descricaoLimpa = empresa.descricao 
    ? empresa.descricao
        .replace(/Empresa prospectada via OpenStreetMap na região "[^"]*"\.\s*/gi, '')
        .replace(/Empresa prospectada na região "[^"]*"\.\s*/gi, '')
        .replace(/Comércio Local/gi, categoriaEfetiva)
    : `Atividade: ${categoriaEfetiva}. Sem website registrado.`;

  const formatarCnpj = (cnpj) => {
    if (!cnpj) return null;
    const limpo = String(cnpj).replace(/\D/g, '');
    if (limpo.length === 14) {
      return limpo.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5');
    }
    return cnpj;
  };

  const cnpjValor = empresa.cnpj || empresa.documento || empresa.dadosBrutos?.cnpj || null;

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

  return createPortal(
    <div 
      className="fixed inset-0 z-50 flex justify-end bg-black/50 dark:bg-black/80 backdrop-blur-sm animate-fade-in-overlay"
      onClick={onClose}
    >
      <div 
        className="bg-white dark:bg-vertex-dark-card border-l border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal w-full max-w-2xl h-screen max-h-screen flex flex-col overflow-hidden animate-slide-in-right transition-colors"
        onClick={(e) => e.stopPropagation()}
      >
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
                <span className="text-[11px] font-medium text-vertex-orange bg-vertex-orange-light dark:bg-vertex-orange/10 px-2 py-0.5 rounded">
                  {categoriaEfetiva}
                </span>
                <span className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
                  {empresa.idEmpresa}
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="p-1.5 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150 cursor-pointer"
              title="Fechar"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Scrollable Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* SEÇÃO 1: TIPO DA EMPRESA */}
          <div className="bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded-xl p-5 shadow-subtle space-y-3.5 transition-colors">
            <div className="flex items-center justify-between border-b border-vertex-border/70 dark:border-vertex-dark-border pb-2.5">
              <div className="flex items-center gap-2">
                <Building2 className="w-4 h-4 text-vertex-orange" />
                <h3 className="text-xs font-bold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
                  Tipo da Empresa & Atividade
                </h3>
              </div>
              <span className="text-[11px] font-semibold text-vertex-orange bg-vertex-orange/10 dark:bg-vertex-orange/20 px-2.5 py-1 rounded-full">
                {categoriaEfetiva}
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 text-xs">
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Segmento / Categoria:</span>
                <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text mt-0.5 block">
                  {categoriaEfetiva}
                </span>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Identificador:</span>
                <span className="font-mono text-[11px] text-vertex-dark dark:text-vertex-dark-text mt-0.5 block truncate">
                  {empresa.idEmpresa}
                </span>
              </div>

              <div className="sm:col-span-2 pt-1 border-t border-vertex-border/50 dark:border-vertex-dark-border/50">
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium mb-1">
                  Descrição & Atividades:
                </span>
                <p className="text-xs text-vertex-body dark:text-vertex-dark-text leading-relaxed whitespace-pre-wrap bg-slate-50/50 dark:bg-vertex-dark-card/50 p-3 rounded-lg border border-vertex-border/40 dark:border-vertex-dark-border/40">
                  {descricaoLimpa}
                </p>
              </div>
            </div>
          </div>

          {/* SEÇÃO 2: CONTATO & INFORMAÇÕES */}
          <div className="bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded-xl p-5 shadow-subtle space-y-3.5 transition-colors">
            <div className="flex items-center gap-2 border-b border-vertex-border/70 dark:border-vertex-dark-border pb-2.5">
              <Phone className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <h3 className="text-xs font-bold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
                Canais de Contato e Informações
              </h3>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 text-xs">
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Telefone:</span>
                <div className="mt-1">
                  {empresa.telefone && empresa.telefone.toLowerCase() !== 'não informado' ? (
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <button
                        type="button"
                        onClick={handleCopiarTelefone}
                        title="Clique para copiar o número de telefone"
                        className="inline-flex items-center gap-1.5 font-semibold text-vertex-dark dark:text-vertex-dark-text hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors group cursor-pointer"
                      >
                        <span className="w-5 h-5 rounded-full bg-emerald-50 dark:bg-emerald-950/60 flex items-center justify-center text-emerald-600 group-hover:bg-emerald-600 group-hover:text-white transition-all shrink-0">
                          <Phone className="w-3 h-3" />
                        </span>
                        <span className="font-mono underline decoration-emerald-500/30 group-hover:decoration-emerald-500">{empresa.telefone}</span>
                      </button>

                      {/* Caixa de confirmação que o número foi copiado */}
                      {copiadoTelefone && (
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/70 border border-emerald-300 dark:border-emerald-700/60 px-2.5 py-0.5 rounded-md shadow-xs animate-in fade-in zoom-in-95 duration-150">
                          <Check className="w-3 h-3 text-emerald-600 dark:text-emerald-400" />
                          <span>Número copiado!</span>
                        </span>
                      )}
                    </div>
                  ) : (
                    <span className="text-vertex-muted dark:text-vertex-dark-muted">Não informado</span>
                  )}
                </div>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">E-mail Comercial:</span>
                <div className="mt-1">
                  {empresa.email && 
                   empresa.email.toLowerCase() !== 'não informado' && 
                   empresa.email.includes('@') && 
                   !empresa.email.includes('empresa.com.br') ? (
                    <div className="flex items-center gap-2 flex-wrap">
                      <a
                        href={gerarLinkGmail(empresa.email)}
                        target="_blank"
                        rel="noopener noreferrer"
                        title="Abrir diretamente no Gmail para escrever um e-mail para a empresa"
                        className="inline-flex items-center gap-1.5 font-semibold text-vertex-dark dark:text-vertex-dark-text hover:text-red-600 dark:hover:text-red-400 transition-colors truncate max-w-full group cursor-pointer"
                      >
                        <span className="w-5 h-5 rounded-full bg-red-50 dark:bg-red-950/60 flex items-center justify-center text-red-500 group-hover:bg-red-600 group-hover:text-white transition-all shrink-0">
                          <Mail className="w-3 h-3" />
                        </span>
                        <span className="truncate underline decoration-red-500/30 group-hover:decoration-red-500">{empresa.email}</span>
                      </a>
                      <a
                        href={gerarLinkGmail(empresa.email)}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-[10px] font-semibold text-red-700 dark:text-red-300 bg-red-50 dark:bg-red-950/50 hover:bg-red-100 dark:hover:bg-red-900/60 px-2 py-0.5 rounded-full border border-red-200 dark:border-red-800/40 inline-flex items-center gap-1 transition-all shrink-0"
                        title="Abrir diretamente no Gmail"
                      >
                        <span>Abrir Gmail</span>
                        <ArrowUpRight className="w-2.5 h-2.5" />
                      </a>
                    </div>
                  ) : (
                    <span className="text-vertex-muted dark:text-vertex-dark-muted">Não informado</span>
                  )}
                </div>
              </div>

              <div className="sm:col-start-2 sm:row-start-2">
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">CNPJ:</span>
                <div className="mt-1">
                  {cnpjValor ? (
                    <span className="inline-flex items-center gap-1.5 font-semibold font-mono text-vertex-dark dark:text-vertex-dark-text">
                      <FileText className="w-3.5 h-3.5 text-purple-600 dark:text-purple-400 shrink-0" />
                      <span>{formatarCnpj(cnpjValor)}</span>
                    </span>
                  ) : (
                    <span className="text-vertex-muted dark:text-vertex-dark-muted">Não informado</span>
                  )}
                </div>
              </div>

              <div className="sm:col-start-1 sm:row-start-2">
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Website Oficial:</span>
                <div className="mt-1">
                  {empresa.site ? (
                    <a
                      href={empresa.site.startsWith('http') ? empresa.site : `https://${empresa.site}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 font-semibold text-vertex-orange hover:underline transition-colors"
                    >
                      <Globe className="w-3.5 h-3.5 shrink-0" />
                      <span className="truncate">{empresa.site}</span>
                      <ArrowUpRight className="w-3.5 h-3.5 shrink-0" />
                    </a>
                  ) : (
                    <span className="text-vertex-muted dark:text-vertex-dark-muted">Não informado</span>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* SEÇÃO 3: ÁREA & LOCALIZAÇÃO */}
          <div className="bg-white dark:bg-vertex-dark-surface border border-vertex-border dark:border-vertex-dark-border rounded-xl p-5 shadow-subtle space-y-3.5 transition-colors">
            <div className="flex items-center justify-between border-b border-vertex-border/70 dark:border-vertex-dark-border pb-2.5">
              <div className="flex items-center gap-2">
                <MapPin className="w-4 h-4 text-red-500" />
                <h3 className="text-xs font-bold text-vertex-dark dark:text-vertex-dark-text uppercase tracking-wider">
                  Área & Localização
                </h3>
              </div>

              {/* Botão Ver no Google Maps */}
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                  [empresa.nome, empresa.endereco, empresa.cidade, empresa.estado].filter(Boolean).join(' ')
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-semibold rounded-md bg-slate-100 hover:bg-slate-200 dark:bg-vertex-dark-card dark:hover:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text transition-colors"
              >
                <MapPin className="w-3 h-3 text-red-500" />
                <span>Ver no Google Maps</span>
                <ArrowUpRight className="w-3 h-3" />
              </a>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 text-xs">
              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Cidade / Estado:</span>
                <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text mt-0.5 block">
                  {[empresa.cidade, empresa.estado].filter(Boolean).join(' - ') || 'Não informado'}
                </span>
              </div>

              <div>
                <span className="text-vertex-muted dark:text-vertex-dark-muted block text-[11px] font-medium">Endereço Completo:</span>
                <span className="font-medium text-vertex-dark dark:text-vertex-dark-text mt-0.5 block bg-slate-50/50 dark:bg-vertex-dark-card/50 p-2.5 rounded border border-vertex-border/40 dark:border-vertex-dark-border/40">
                  {empresa.endereco ? empresa.endereco.replace(/\s*\([^)]*\)/g, '').trim() : 'Centro'}
                </span>
              </div>
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
    </div>,
    document.body
  );
};
