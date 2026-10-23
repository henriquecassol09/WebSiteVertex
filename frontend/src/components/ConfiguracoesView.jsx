import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { 
  ArrowLeft,
  User, 
  Lock, 
  Trash2, 
  Eye, 
  EyeOff, 
  Check, 
  AlertCircle, 
  Loader2, 
  Calendar, 
  Mail, 
  KeyRound,
  AlertTriangle,
  Fingerprint,
  Camera,
  Upload,
  X
} from 'lucide-react';
import { sanitizarUrlFoto } from '../utils/security';

export const ConfiguracoesView = ({ onVoltar, onAccountDeleted }) => {
  const { user, carregarUsuario, alterarSenha, deletarConta, uploadFoto, removerFoto } = useAuth();
  const toast = useToast();

  // Foto de Perfil Upload State (Limite 5 MB)
  const fileInputRef = useRef(null);
  const [arquivoSelecionado, setArquivoSelecionado] = useState(null);
  const [fotoPreview, setFotoPreview] = useState(null);
  const [painelUploadAberto, setPainelUploadAberto] = useState(false);
  const [loadingFoto, setLoadingFoto] = useState(false);
  const [uploadError, setUploadError] = useState('');

  // Trocar Senha State
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmarNovaSenha, setConfirmarNovaSenha] = useState('');
  const [showSenhaAtual, setShowSenhaAtual] = useState(false);
  const [showNovaSenha, setShowNovaSenha] = useState(false);
  const [showConfirmarSenha, setShowConfirmarSenha] = useState(false);
  const [loadingSenha, setLoadingSenha] = useState(false);
  const [senhaErrors, setSenhaErrors] = useState({});

  // Deletar Conta State
  const [senhaConfirmacaoDeletar, setSenhaConfirmacaoDeletar] = useState('');
  const [showConfirmacaoDeletar, setShowConfirmacaoDeletar] = useState(false);
  const [loadingDeletar, setLoadingDeletar] = useState(false);
  const [deletarError, setDeletarError] = useState('');

  useEffect(() => {
    carregarUsuario();
  }, [carregarUsuario]);

  const handleAbrirSeletor = () => {
    setUploadError('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      fileInputRef.current.click();
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Limite estrito de 5 MB (5 * 1024 * 1024 bytes)
    const limiteBytes = 5 * 1024 * 1024;
    if (file.size > limiteBytes) {
      toast.error('A imagem excede o tamanho máximo de 5 MB.');
      setUploadError('O arquivo excede o limite máximo permitido de 5 MB.');
      e.target.value = '';
      return;
    }

    // Validação de tipo de imagem (bloqueio de SVG e scripts)
    const tiposPermitidos = ['image/jpeg', 'image/png', 'image/webp', 'image/jpg'];
    if (!tiposPermitidos.includes(file.type.toLowerCase()) || file.name.toLowerCase().endsWith('.svg')) {
      toast.error('Formato não suportado. Por favor, envie uma foto JPEG, PNG ou WebP.');
      setUploadError('Formato não suportado. Apenas imagens JPEG, PNG ou WebP são aceitas.');
      e.target.value = '';
      return;
    }

    setArquivoSelecionado(file);
    setUploadError('');

    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        try {
          const dim = 256;
          const canvas = document.createElement('canvas');
          canvas.width = dim;
          canvas.height = dim;
          const ctx = canvas.getContext('2d');
          if (ctx) {
            ctx.fillStyle = '#FFFFFF';
            ctx.fillRect(0, 0, dim, dim);
            const minDim = Math.min(img.width, img.height);
            const sx = (img.width - minDim) / 2;
            const sy = (img.height - minDim) / 2;
            ctx.drawImage(img, sx, sy, minDim, minDim, 0, 0, dim, dim);
            const dataUrl = canvas.toDataURL('image/jpeg', 0.88);
            setFotoPreview(dataUrl);
            setPainelUploadAberto(true);
            return;
          }
        } catch {
          // fallback silencioso para dataUrl original
        }
        setFotoPreview(event.target.result);
        setPainelUploadAberto(true);
      };
      img.onerror = () => {
        setFotoPreview(event.target.result);
        setPainelUploadAberto(true);
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(file);
  };

  const handleSalvarFotoUpload = async () => {
    if (!arquivoSelecionado) {
      toast.error('Nenhum arquivo de imagem selecionado.');
      return;
    }

    setLoadingFoto(true);
    setUploadError('');

    try {
      await uploadFoto(arquivoSelecionado, fotoPreview);
      toast.success('Foto de perfil atualizada com sucesso!');
      setPainelUploadAberto(false);
      setArquivoSelecionado(null);
      setFotoPreview(null);
    } catch (err) {
      setUploadError(err.message || 'Erro ao enviar foto de perfil');
      toast.error(err.message || 'Erro ao enviar foto');
    } finally {
      setLoadingFoto(false);
    }
  };

  const handleRemoverFoto = async () => {
    setLoadingFoto(true);
    setUploadError('');
    try {
      await removerFoto();
      toast.success('Foto de perfil removida com sucesso');
      setPainelUploadAberto(false);
      setArquivoSelecionado(null);
      setFotoPreview(null);
    } catch (err) {
      toast.error(err.message || 'Erro ao remover foto');
    } finally {
      setLoadingFoto(false);
    }
  };

  const formatarTamanho = (bytes) => {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  // Validação de Senha Forte
  const hasMinLength = novaSenha.length >= 10 && novaSenha.length <= 128;
  const hasUpper = /[A-Z]/.test(novaSenha);
  const hasLower = /[a-z]/.test(novaSenha);
  const hasNumber = /[0-9]/.test(novaSenha);
  const hasSpecial = /[^A-Za-z0-9]/.test(novaSenha);
  const isNovaSenhaValida = hasMinLength && hasUpper && hasLower && hasNumber && hasSpecial;

  const handleSalvarSenha = async (e) => {
    e.preventDefault();
    const errors = {};

    if (!senhaAtual) {
      errors.senhaAtual = 'A senha atual é obrigatória';
    }

    if (!novaSenha) {
      errors.novaSenha = 'A nova senha é obrigatória';
    } else if (!isNovaSenhaValida) {
      errors.novaSenha = 'A nova senha não atende a todos os requisitos de segurança';
    } else if (senhaAtual === novaSenha) {
      errors.novaSenha = 'A nova senha deve ser diferente da senha atual';
    }

    if (!confirmarNovaSenha) {
      errors.confirmarNovaSenha = 'Confirme a nova senha';
    } else if (novaSenha !== confirmarNovaSenha) {
      errors.confirmarNovaSenha = 'As senhas não coincidem';
    }

    if (Object.keys(errors).length > 0) {
      setSenhaErrors(errors);
      return;
    }

    setLoadingSenha(true);
    setSenhaErrors({});

    try {
      const res = await alterarSenha(senhaAtual, novaSenha);
      toast.success(res?.mensagem || 'Senha alterada com sucesso!');
      setSenhaAtual('');
      setNovaSenha('');
      setConfirmarNovaSenha('');
    } catch (err) {
      toast.error(err.message || 'Erro ao alterar a senha');
      setSenhaErrors({ general: err.message || 'Erro ao alterar a senha' });
    } finally {
      setLoadingSenha(false);
    }
  };

  const handleConfirmarDelecao = async (e) => {
    e.preventDefault();
    if (!senhaConfirmacaoDeletar.trim()) {
      setDeletarError('Digite sua senha atual para confirmar a exclusão');
      return;
    }

    setLoadingDeletar(true);
    setDeletarError('');

    try {
      await deletarConta(senhaConfirmacaoDeletar.trim());
      toast.success('Sua conta foi permanentemente excluída');
      if (onAccountDeleted) {
        onAccountDeleted();
      }
    } catch (err) {
      setDeletarError(err.message || 'Não foi possível excluir a conta. Verifique sua senha.');
    } finally {
      setLoadingDeletar(false);
    }
  };

  const formatarData = (dataStr) => {
    if (!dataStr) return 'Recente';
    try {
      const d = new Date(dataStr);
      return d.toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      });
    } catch {
      return dataStr;
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto space-y-8 pb-16 animate-in fade-in duration-200">
      
      {/* Top Header com Botão Voltar */}
      <div className="flex items-center justify-between gap-3 pb-3 border-b border-vertex-border dark:border-vertex-dark-border">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onVoltar}
            className="flex items-center justify-center w-10 h-10 rounded-lg border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-50 dark:hover:bg-vertex-dark-surface transition-all shadow-subtle shrink-0 cursor-pointer active:scale-95"
            title="Voltar"
            aria-label="Voltar para a página anterior"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h1 className="text-xl sm:text-2xl font-bold text-vertex-dark dark:text-vertex-dark-text tracking-tight truncate">
              Configurações
            </h1>
            <p className="text-xs sm:text-sm text-vertex-muted dark:text-vertex-dark-muted truncate">
              Gerencie suas informações, credenciais de acesso e segurança da conta
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={onVoltar}
          className="hidden sm:flex items-center gap-2 px-4 py-2 rounded-lg border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text transition-all shadow-subtle shrink-0 cursor-pointer"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Voltar para Busca</span>
        </button>
      </div>

      {/* Fluxo Único Contínuo de Rolagem (Scroll) com seções bem definidas */}
      <div className="space-y-8">
        
        {/* PARTE 1: Informações da Conta */}
        <section className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-xl p-5 sm:p-7 shadow-card space-y-6">
          <div className="flex items-center gap-2.5 pb-3 border-b border-vertex-border-light dark:border-vertex-dark-border">
            <div className="w-8 h-8 rounded-lg bg-vertex-orange/10 dark:bg-vertex-orange/20 flex items-center justify-center text-vertex-orange shrink-0">
              <User className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-vertex-dark dark:text-vertex-dark-text">
                Informações da Conta
              </h2>
              <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted">
                Dados cadastrais e identificação do perfil de usuário
              </p>
            </div>
          </div>

          {/* Input Oculto de Arquivo para Upload Direto */}
          <input
            type="file"
            ref={fileInputRef}
            accept="image/png, image/jpeg, image/jpg, image/webp"
            onChange={handleFileChange}
            className="hidden"
          />

          {/* Perfil Header Card com Upload de Foto (Limite 5 MB) */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4 sm:p-5 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-slate-50/70 dark:bg-vertex-dark-surface/60">
            <div className="flex items-center gap-4 min-w-0">
              <div className="relative group shrink-0">
                {sanitizarUrlFoto(user?.imagem) ? (
                  <img
                    src={sanitizarUrlFoto(user.imagem)}
                    alt={user?.nome || 'Usuário'}
                    className="w-16 h-16 rounded-2xl object-cover shadow-subtle border-2 border-vertex-orange/40 shrink-0 bg-slate-100 dark:bg-vertex-dark-surface"
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                ) : (
                  <div className="w-16 h-16 rounded-2xl bg-vertex-orange text-white flex items-center justify-center font-bold text-2xl shadow-subtle shrink-0">
                    {user?.nome ? user.nome.charAt(0).toUpperCase() : 'V'}
                  </div>
                )}
                <button
                  type="button"
                  onClick={handleAbrirSeletor}
                  className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border text-vertex-dark dark:text-vertex-dark-text flex items-center justify-center shadow-xs hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors cursor-pointer"
                  title="Upload de foto de perfil (até 5 MB)"
                  aria-label="Upload de foto de perfil"
                >
                  <Camera className="w-3.5 h-3.5 text-vertex-orange" />
                </button>
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="font-bold text-base text-vertex-dark dark:text-vertex-dark-text truncate">
                    {user?.nome || 'Usuário VERTEX'}
                  </h3>
                  <span className="text-[11px] font-medium px-2.5 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                    Ativo
                  </span>
                </div>
                <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted font-mono mt-0.5 truncate">
                  {user?.email || 'email@vertex.com'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto">
              <button
                type="button"
                onClick={handleAbrirSeletor}
                className="flex-1 sm:flex-initial px-4 py-2 rounded-lg border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card hover:bg-slate-50 dark:hover:bg-vertex-dark-surface text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text transition-colors shadow-subtle flex items-center justify-center gap-2 cursor-pointer"
              >
                <Upload className="w-4 h-4 text-vertex-orange" />
                <span>{user?.imagem ? 'Alterar Foto' : 'Adicionar Foto'}</span>
              </button>

              {user?.imagem && (
                <button
                  type="button"
                  onClick={handleRemoverFoto}
                  disabled={loadingFoto}
                  className="px-3 py-2 rounded-lg border border-red-200 dark:border-red-900/60 bg-red-50/50 dark:bg-red-950/20 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-950/40 text-xs font-semibold transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                  title="Remover foto de perfil"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">Remover</span>
                </button>
              )}
            </div>
          </div>

          {/* Painel de Confirmação do Upload Selecionado (Limite 5 MB) */}
          {painelUploadAberto && arquivoSelecionado && (
            <div className="p-4 sm:p-5 rounded-xl border border-vertex-orange/30 dark:border-vertex-orange/40 bg-orange-50/20 dark:bg-vertex-dark-surface/80 space-y-4 animate-in fade-in duration-150">
              <div className="flex items-center justify-between pb-2 border-b border-vertex-border dark:border-vertex-dark-border">
                <div className="flex items-center gap-2">
                  <Upload className="w-4 h-4 text-vertex-orange" />
                  <h3 className="text-sm font-bold text-vertex-dark dark:text-vertex-dark-text">
                    Nova Foto de Perfil
                  </h3>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setPainelUploadAberto(false);
                    setArquivoSelecionado(null);
                    setFotoPreview(null);
                  }}
                  className="p-1 rounded text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text cursor-pointer"
                  title="Fechar"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {uploadError && (
                <div className="p-3 rounded-lg border border-red-300 dark:border-red-900 bg-red-50 dark:bg-red-950/30 text-red-700 dark:text-red-300 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{uploadError}</span>
                </div>
              )}

              {/* Box com Imagem e Detalhes do Arquivo */}
              <div className="flex flex-col sm:flex-row items-center gap-4 p-4 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card shadow-xs">
                {fotoPreview && (
                  <img
                    src={fotoPreview}
                    alt="Pré-visualização"
                    className="w-20 h-20 rounded-2xl object-cover border-2 border-vertex-orange/40 shadow-subtle shrink-0"
                  />
                )}
                <div className="min-w-0 flex-1 text-center sm:text-left space-y-0.5">
                  <p className="text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text truncate">
                    {arquivoSelecionado.name}
                  </p>
                  <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
                    Tamanho: {formatarTamanho(arquivoSelecionado.size)} (Limite máximo: 5 MB)
                  </p>
                  <p className="text-[11px] text-emerald-600 dark:text-emerald-400 font-medium pt-1">
                    ✓ Imagem pronta para envio
                  </p>
                </div>
              </div>

              <div className="flex flex-col sm:flex-row items-center justify-between gap-2.5 pt-1">
                <div className="flex items-center gap-2 w-full sm:w-auto">
                  <button
                    type="button"
                    onClick={handleSalvarFotoUpload}
                    disabled={loadingFoto}
                    className="w-full sm:w-auto px-5 py-2 min-h-[42px] rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs sm:text-sm font-semibold shadow-subtle disabled:opacity-50 flex items-center justify-center gap-2 transition-colors cursor-pointer"
                  >
                    {loadingFoto ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>Enviando foto...</span>
                      </>
                    ) : (
                      <>
                        <Check className="w-4 h-4" />
                        <span>Salvar Foto</span>
                      </>
                    )}
                  </button>

                  <button
                    type="button"
                    onClick={handleAbrirSeletor}
                    disabled={loadingFoto}
                    className="w-full sm:w-auto px-4 py-2 min-h-[42px] rounded-lg border border-vertex-border dark:border-vertex-dark-border text-xs sm:text-sm font-medium text-vertex-dark dark:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors text-center cursor-pointer"
                  >
                    Trocar Arquivo
                  </button>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setPainelUploadAberto(false);
                    setArquivoSelecionado(null);
                    setFotoPreview(null);
                  }}
                  className="w-full sm:w-auto text-xs text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text py-1 px-2 cursor-pointer"
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}

          {/* Grid de Informações Cadastrais */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            <div className="p-4 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 space-y-1">
              <div className="flex items-center gap-2 text-vertex-muted dark:text-vertex-dark-muted text-xs">
                <User className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                <span>Nome Completo</span>
              </div>
              <p className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text break-words">
                {user?.nome || 'Não informado'}
              </p>
            </div>

            <div className="p-4 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 space-y-1">
              <div className="flex items-center gap-2 text-vertex-muted dark:text-vertex-dark-muted text-xs">
                <Mail className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                <span>Endereço de E-mail</span>
              </div>
              <p className="text-xs sm:text-sm font-semibold font-mono text-vertex-dark dark:text-vertex-dark-text break-all">
                {user?.email || 'Não informado'}
              </p>
            </div>

            <div className="p-4 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 space-y-1">
              <div className="flex items-center gap-2 text-vertex-muted dark:text-vertex-dark-muted text-xs">
                <Calendar className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                <span>Data de Cadastro</span>
              </div>
              <p className="text-xs sm:text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text">
                {formatarData(user?.criadoEm)}
              </p>
            </div>

            {user?.idUsuario && (
              <div className="p-4 rounded-xl border border-vertex-border dark:border-vertex-dark-border bg-slate-50/40 dark:bg-vertex-dark-surface/40 space-y-1">
                <div className="flex items-center gap-2 text-vertex-muted dark:text-vertex-dark-muted text-xs">
                  <Fingerprint className="w-3.5 h-3.5 text-vertex-orange shrink-0" />
                  <span>Identificador da Conta</span>
                </div>
                <p className="text-[11px] font-mono text-vertex-muted dark:text-vertex-dark-muted break-all">
                  {user.idUsuario}
                </p>
              </div>
            )}
          </div>
        </section>

        {/* PARTE 2: Segurança & Trocar Senha */}
        <section className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border rounded-xl p-5 sm:p-7 shadow-card space-y-6">
          <div className="flex items-center gap-2.5 pb-3 border-b border-vertex-border-light dark:border-vertex-dark-border">
            <div className="w-8 h-8 rounded-lg bg-vertex-orange/10 dark:bg-vertex-orange/20 flex items-center justify-center text-vertex-orange shrink-0">
              <Lock className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-vertex-dark dark:text-vertex-dark-text">
                Segurança & Trocar Senha
              </h2>
              <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted">
                Altere suas credenciais de acesso informando sua senha atual para validação
              </p>
            </div>
          </div>

          <form onSubmit={handleSalvarSenha} className="space-y-4">
            {senhaErrors.general && (
              <div className="p-3.5 rounded-lg border border-red-300 dark:border-red-900 bg-red-50 dark:bg-red-950/30 text-red-700 dark:text-red-300 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{senhaErrors.general}</span>
              </div>
            )}

            {/* Senha Atual */}
            <div>
              <label className="block text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                Senha Atual <span className="text-vertex-orange">*</span>
              </label>
              <div className="relative">
                <KeyRound className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type={showSenhaAtual ? 'text' : 'password'}
                  value={senhaAtual}
                  onChange={(e) => {
                    setSenhaAtual(e.target.value);
                    if (senhaErrors.senhaAtual) setSenhaErrors(prev => ({ ...prev, senhaAtual: '' }));
                  }}
                  placeholder="Digite sua senha atual"
                  className={`w-full pl-10 pr-10 min-h-[46px] py-2.5 text-xs sm:text-sm rounded-lg border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                    senhaErrors.senhaAtual ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() => setShowSenhaAtual(!showSenhaAtual)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text p-1 cursor-pointer"
                  aria-label={showSenhaAtual ? 'Ocultar senha' : 'Ver senha'}
                >
                  {showSenhaAtual ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {senhaErrors.senhaAtual && <p className="text-[11px] text-red-500 font-medium mt-1">{senhaErrors.senhaAtual}</p>}
            </div>

            {/* Nova Senha */}
            <div>
              <label className="block text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                Nova Senha <span className="text-vertex-orange">*</span>
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type={showNovaSenha ? 'text' : 'password'}
                  value={novaSenha}
                  onChange={(e) => {
                    setNovaSenha(e.target.value);
                    if (senhaErrors.novaSenha) setSenhaErrors(prev => ({ ...prev, novaSenha: '' }));
                  }}
                  placeholder="Mínimo de 10 caracteres"
                  maxLength={128}
                  className={`w-full pl-10 pr-10 min-h-[46px] py-2.5 text-xs sm:text-sm rounded-lg border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange ${
                    senhaErrors.novaSenha ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() => setShowNovaSenha(!showNovaSenha)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text p-1 cursor-pointer"
                  aria-label={showNovaSenha ? 'Ocultar senha' : 'Ver senha'}
                >
                  {showNovaSenha ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {senhaErrors.novaSenha && <p className="text-[11px] text-red-500 font-medium mt-1">{senhaErrors.novaSenha}</p>}
            </div>

            {/* Confirmar Nova Senha */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text">
                  Confirmar Nova Senha <span className="text-vertex-orange">*</span>
                </label>
                {confirmarNovaSenha && (
                  <span className={`text-[11px] font-medium flex items-center gap-1 ${
                    novaSenha === confirmarNovaSenha ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                  }`}>
                    {novaSenha === confirmarNovaSenha ? (
                      <>
                        <Check className="w-3 h-3" />
                        Senhas iguais
                      </>
                    ) : (
                      <>
                        <AlertCircle className="w-3 h-3" />
                        Senhas diferentes
                      </>
                    )}
                  </span>
                )}
              </div>
              <div className="relative">
                <Lock className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type={showConfirmarSenha ? 'text' : 'password'}
                  value={confirmarNovaSenha}
                  onChange={(e) => {
                    setConfirmarNovaSenha(e.target.value);
                    if (senhaErrors.confirmarNovaSenha) setSenhaErrors(prev => ({ ...prev, confirmarNovaSenha: '' }));
                  }}
                  placeholder="Redigite a nova senha para confirmação"
                  maxLength={128}
                  className={`w-full pl-10 pr-10 min-h-[46px] py-2.5 text-xs sm:text-sm rounded-lg border bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-colors ${
                    senhaErrors.confirmarNovaSenha 
                      ? 'border-red-400 bg-red-50/20' 
                      : (confirmarNovaSenha && novaSenha === confirmarNovaSenha)
                        ? 'border-emerald-500/80 bg-emerald-50/10'
                        : 'border-vertex-border dark:border-vertex-dark-border'
                  }`}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={() => setShowConfirmarSenha(!showConfirmarSenha)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-vertex-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text p-1 cursor-pointer"
                  aria-label={showConfirmarSenha ? 'Ocultar senha' : 'Ver senha'}
                >
                  {showConfirmarSenha ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {senhaErrors.confirmarNovaSenha && <p className="text-[11px] text-red-500 font-medium mt-1">{senhaErrors.confirmarNovaSenha}</p>}
            </div>

            {/* Checklist de Validação Estrita de Senha Forte */}
            <div className="p-4 bg-slate-50 dark:bg-vertex-dark-surface rounded-xl border border-vertex-border dark:border-vertex-dark-border space-y-2 text-xs">
              <span className="font-semibold text-vertex-dark dark:text-vertex-dark-text block mb-1">
                Requisitos de Segurança da Nova Senha:
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-vertex-muted dark:text-vertex-dark-muted">
                <span className={`flex items-center gap-1.5 ${hasMinLength ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3.5 h-3.5 shrink-0 ${hasMinLength ? 'text-emerald-500' : 'text-slate-300 dark:text-slate-600'}`} />
                  Mínimo de 10 caracteres
                </span>
                <span className={`flex items-center gap-1.5 ${hasUpper ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3.5 h-3.5 shrink-0 ${hasUpper ? 'text-emerald-500' : 'text-slate-300 dark:text-slate-600'}`} />
                  Pelo menos uma letra maiúscula
                </span>
                <span className={`flex items-center gap-1.5 ${hasLower ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3.5 h-3.5 shrink-0 ${hasLower ? 'text-emerald-500' : 'text-slate-300 dark:text-slate-600'}`} />
                  Pelo menos uma letra minúscula
                </span>
                <span className={`flex items-center gap-1.5 ${hasNumber ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3.5 h-3.5 shrink-0 ${hasNumber ? 'text-emerald-500' : 'text-slate-300 dark:text-slate-600'}`} />
                  Pelo menos um número
                </span>
                <span className={`flex items-center gap-1.5 sm:col-span-2 ${hasSpecial ? 'text-emerald-600 dark:text-emerald-400 font-medium' : ''}`}>
                  <Check className={`w-3.5 h-3.5 shrink-0 ${hasSpecial ? 'text-emerald-500' : 'text-slate-300 dark:text-slate-600'}`} />
                  Caractere especial (!@#$%&*)
                </span>
              </div>
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={loadingSenha || !senhaAtual || !isNovaSenhaValida || novaSenha !== confirmarNovaSenha}
                className="w-full sm:w-auto px-6 py-2.5 min-h-[46px] rounded-lg bg-vertex-orange hover:bg-vertex-orange-hover text-white text-xs sm:text-sm font-semibold shadow-subtle disabled:opacity-50 flex items-center justify-center gap-2 transition-all cursor-pointer"
              >
                {loadingSenha ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Atualizando senha no sistema...</span>
                  </>
                ) : (
                  <span>Salvar Nova Senha</span>
                )}
              </button>
            </div>
          </form>
        </section>

        {/* PARTE 3: Zona de Perigo / Excluir Conta */}
        <section className="bg-white dark:bg-vertex-dark-card border border-red-200 dark:border-red-900/60 rounded-xl p-5 sm:p-7 shadow-card space-y-6">
          <div className="flex items-center gap-2.5 pb-3 border-b border-red-200 dark:border-red-900/60">
            <div className="w-8 h-8 rounded-lg bg-red-100 dark:bg-red-950/40 flex items-center justify-center text-red-600 dark:text-red-400 shrink-0">
              <Trash2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-red-600 dark:text-red-400">
                Zona de Perigo: Excluir Conta
              </h2>
              <p className="text-xs text-vertex-muted dark:text-vertex-dark-muted">
                Ação irreversível de encerramento da sua conta e remoção dos seus dados
              </p>
            </div>
          </div>

          <div className="p-4 sm:p-5 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50/50 dark:bg-red-950/20 text-red-900 dark:text-red-200 space-y-2.5">
            <div className="flex items-center gap-2 font-semibold text-red-700 dark:text-red-400">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>Atenção: Esta ação não poderá ser desfeita</span>
            </div>
            <p className="text-xs text-red-800 dark:text-red-300 leading-relaxed">
              Ao confirmar a exclusão, todos os seus dados cadastrais, empresas salvas, pesquisas de mercado e notas registradas serão permanentemente removidos de nossos servidores.
            </p>
          </div>

          {!showConfirmacaoDeletar ? (
            <div className="pt-1">
              <button
                type="button"
                onClick={() => setShowConfirmacaoDeletar(true)}
                className="w-full sm:w-auto px-6 py-2.5 min-h-[46px] rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs sm:text-sm font-semibold transition-colors flex items-center justify-center gap-2 shadow-sm cursor-pointer"
              >
                <Trash2 className="w-4 h-4" />
                <span>Quero Excluir Minha Conta</span>
              </button>
            </div>
          ) : (
            <form onSubmit={handleConfirmarDelecao} className="space-y-4 pt-4 border-t border-vertex-border dark:border-vertex-dark-border">
              <div>
                <label className="block text-xs font-semibold text-vertex-dark dark:text-vertex-dark-text mb-1.5">
                  Digite sua senha atual para autorizar a exclusão:
                </label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-vertex-muted dark:text-vertex-dark-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <input
                    type="password"
                    autoFocus
                    value={senhaConfirmacaoDeletar}
                    onChange={(e) => {
                      setSenhaConfirmacaoDeletar(e.target.value);
                      if (deletarError) setDeletarError('');
                    }}
                    placeholder="Digite sua senha de acesso"
                    className="w-full pl-10 pr-3.5 min-h-[46px] text-xs sm:text-sm rounded-lg border border-red-300 dark:border-red-900/80 bg-white dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:outline-none focus:ring-2 focus:ring-red-500/20 focus:border-red-500"
                  />
                </div>
                {deletarError && (
                  <p className="text-[11px] text-red-500 font-medium mt-1.5 flex items-center gap-1">
                    <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                    {deletarError}
                  </p>
                )}
              </div>

              <div className="flex flex-col sm:flex-row items-center gap-2.5 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    setShowConfirmacaoDeletar(false);
                    setSenhaConfirmacaoDeletar('');
                    setDeletarError('');
                  }}
                  className="w-full sm:w-auto px-5 py-2.5 min-h-[46px] rounded-lg border border-vertex-border dark:border-vertex-dark-border text-xs sm:text-sm font-medium text-vertex-dark dark:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface transition-colors text-center cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={loadingDeletar || !senhaConfirmacaoDeletar}
                  className="w-full sm:w-auto px-6 py-2.5 min-h-[46px] rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs sm:text-sm font-semibold disabled:opacity-50 flex items-center justify-center gap-2 transition-colors shadow-sm cursor-pointer"
                >
                  {loadingDeletar ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Excluindo conta...</span>
                    </>
                  ) : (
                    <span>Confirmar Exclusão Definitiva</span>
                  )}
                </button>
              </div>
            </form>
          )}
        </section>

      </div>
    </div>
  );
};
