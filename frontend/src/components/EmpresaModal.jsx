import React, { useState, useEffect } from 'react';
import { X, Building2, Save } from 'lucide-react';

export const EmpresaModal = ({ isOpen, onClose, onSave, empresa, pesquisas = [] }) => {
  const [formData, setFormData] = useState({
    nome: '',
    categoria: '',
    cidade: '',
    estado: '',
    endereco: '',
    telefone: '',
    email: '',
    site: '',
    descricao: '',
    idPesquisa: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (empresa) {
      setFormData({
        nome: empresa.nome || '',
        categoria: empresa.categoria || '',
        cidade: empresa.cidade || '',
        estado: empresa.estado || '',
        endereco: empresa.endereco || '',
        telefone: empresa.telefone || '',
        email: empresa.email || '',
        site: empresa.site || '',
        descricao: empresa.descricao || '',
        idPesquisa: empresa.idPesquisa || '',
      });
    } else {
      setFormData({
        nome: '',
        categoria: '',
        cidade: '',
        estado: '',
        endereco: '',
        telefone: '',
        email: '',
        site: '',
        descricao: '',
        idPesquisa: '',
      });
    }
    setErrors({});
  }, [empresa, isOpen]);

  // Fechar o modal com a tecla ESC
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const validate = () => {
    const errs = {};
    if (!formData.nome.trim()) {
      errs.nome = 'Nome é obrigatório';
    } else if (formData.nome.length > 255) {
      errs.nome = 'Máximo de 255 caracteres';
    }

    if (formData.categoria && formData.categoria.length > 100) {
      errs.categoria = 'Máximo de 100 caracteres';
    }
    if (formData.cidade && formData.cidade.length > 100) {
      errs.cidade = 'Máximo de 100 caracteres';
    }
    if (formData.estado && formData.estado.length > 100) {
      errs.estado = 'Máximo de 100 caracteres';
    }
    if (formData.endereco && formData.endereco.length > 255) {
      errs.endereco = 'Máximo de 255 caracteres';
    }
    if (formData.telefone && formData.telefone.length > 30) {
      errs.telefone = 'Máximo de 30 caracteres';
    }
    if (formData.email) {
      if (formData.email.length > 255) {
        errs.email = 'Máximo de 255 caracteres';
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
        errs.email = 'E-mail inválido';
      }
    }
    if (formData.site && formData.site.length > 255) {
      errs.site = 'Máximo de 255 caracteres';
    }
    if (formData.descricao && formData.descricao.length > 4000) {
      errs.descricao = 'Máximo de 4000 caracteres';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    setSubmitting(true);
    try {
      await onSave(formData);
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 dark:bg-black/80 backdrop-blur-sm overflow-y-auto animate-in fade-in duration-200">
      <div className="bg-white dark:bg-vertex-dark-card border border-vertex-border dark:border-vertex-dark-border shadow-modal dark:shadow-dark-modal rounded-2xl overflow-hidden max-w-2xl w-full flex flex-col transition-colors duration-300">
        {/* Header do Modal Compactado */}
        <div className="flex items-center justify-between px-6 py-3 border-b border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-card">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-vertex-orange-light dark:bg-vertex-orange/10 flex items-center justify-center text-vertex-orange">
              <Building2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-vertex-dark dark:text-vertex-dark-text leading-tight">
                {empresa ? 'Editar Empresa' : 'Nova Empresa'}
              </h2>
              <p className="text-[11px] text-vertex-muted dark:text-vertex-dark-muted">
                {empresa ? 'Atualize as informações cadastrais' : 'Preencha os dados da organização'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-md text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface active:scale-95 transition-all duration-150"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Formulário com espaçamentos enxutos */}
        <form onSubmit={handleSubmit} className="px-6 py-3.5 space-y-2.5">
          {/* Nome */}
          <div>
            <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
              Nome da Empresa <span className="text-vertex-orange">*</span>
            </label>
            <input
              type="text"
              value={formData.nome}
              onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
              placeholder="Razão Social ou Nome Fantasia"
              maxLength={255}
              className={`w-full px-3 py-1.5 text-xs rounded-md border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200 ${
                errors.nome ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
              }`}
            />
            {errors.nome && <p className="text-[11px] text-red-500 mt-0.5">{errors.nome}</p>}
          </div>

          {/* Categoria & Pesquisa Vinculada */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                Categoria
              </label>
              <input
                type="text"
                value={formData.categoria}
                onChange={(e) => setFormData({ ...formData, categoria: e.target.value })}
                placeholder="Ex: Tecnologia da Informação"
                maxLength={100}
                className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                Pesquisa de Origem (Opcional)
              </label>
              <select
                value={formData.idPesquisa}
                onChange={(e) => setFormData({ ...formData, idPesquisa: e.target.value })}
                className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
              >
                <option value="">Nenhuma pesquisa vinculada</option>
                {pesquisas.map((p) => (
                  <option key={p.idPesquisa} value={p.idPesquisa}>
                    {p.consulta} {p.regiao ? `(${p.regiao})` : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Cidade & Estado */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                Cidade
              </label>
              <input
                type="text"
                value={formData.cidade}
                onChange={(e) => setFormData({ ...formData, cidade: e.target.value })}
                placeholder="Ex: São Paulo"
                maxLength={100}
                className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                Estado (UF)
              </label>
              <input
                type="text"
                value={formData.estado}
                onChange={(e) => setFormData({ ...formData, estado: e.target.value })}
                placeholder="Ex: SP"
                maxLength={100}
                className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
              />
            </div>
          </div>

          {/* Endereço */}
          <div>
            <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
              Endereço Completo
            </label>
            <input
              type="text"
              value={formData.endereco}
              onChange={(e) => setFormData({ ...formData, endereco: e.target.value })}
              placeholder="Logradouro, número, complemento, bairro"
              maxLength={255}
              className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            />
          </div>

          {/* Telefone & E-mail */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                Telefone
              </label>
              <input
                type="text"
                value={formData.telefone}
                onChange={(e) => setFormData({ ...formData, telefone: e.target.value })}
                placeholder="(XX) XXXXX-XXXX"
                maxLength={30}
                className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
                E-mail
              </label>
              <input
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder="contato@empresa.com"
                maxLength={255}
                className={`w-full px-3 py-1.5 text-xs rounded-md border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200 ${
                  errors.email ? 'border-red-400 bg-red-50/20' : 'border-vertex-border dark:border-vertex-dark-border'
                }`}
              />
              {errors.email && <p className="text-[11px] text-red-500 mt-0.5">{errors.email}</p>}
            </div>
          </div>

          {/* Site */}
          <div>
            <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text mb-1">
              Website
            </label>
            <input
              type="url"
              value={formData.site}
              onChange={(e) => setFormData({ ...formData, site: e.target.value })}
              placeholder="https://empresa.com"
              maxLength={255}
              className="w-full px-3 py-1.5 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200"
            />
          </div>

          {/* Descrição */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-medium text-vertex-dark dark:text-vertex-dark-text">
                Descrição da Empresa
              </label>
              <span className="text-[10px] text-vertex-muted dark:text-vertex-dark-muted font-mono">
                {formData.descricao.length} / 4000
              </span>
            </div>
            <textarea
              rows={3}
              value={formData.descricao}
              onChange={(e) => setFormData({ ...formData, descricao: e.target.value })}
              placeholder="Atuação, diferenciais, perfil de operação..."
              maxLength={4000}
              style={{ resize: 'none' }}
              className="w-full px-3 py-2 text-xs rounded-md border border-vertex-border dark:border-vertex-dark-border bg-slate-50/60 dark:bg-vertex-dark-surface text-vertex-dark dark:text-vertex-dark-text focus:bg-white dark:focus:bg-vertex-dark-surface focus:outline-none focus:ring-2 focus:ring-vertex-orange/20 focus:border-vertex-orange transition-all duration-200 resize-none h-20"
            />
          </div>
        </form>

        {/* Rodapé de Ações Compactado */}
        <div className="flex items-center justify-end gap-2.5 px-6 py-3 border-t border-vertex-border dark:border-vertex-dark-border bg-slate-50/50 dark:bg-vertex-dark-surface/50">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface rounded-md transition-all duration-150"
          >
            Cancelar
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="flex items-center gap-1.5 px-5 py-2 text-xs font-semibold rounded-md bg-vertex-orange text-white hover:bg-vertex-orange-hover active:scale-[0.98] transition-all duration-200 shadow-sm hover:shadow-glow-orange disabled:opacity-50"
          >
            <Save className="w-3.5 h-3.5" />
            <span>{submitting ? 'Salvando...' : empresa ? 'Salvar Alterações' : 'Cadastrar Empresa'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
