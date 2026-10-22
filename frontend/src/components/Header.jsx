import React, { useState, useRef, useEffect } from 'react';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';
import { ThemeToggle } from './ThemeToggle';
import { useAuth } from '../context/AuthContext';
import { 
  Building2, 
  Search, 
  User, 
  LogOut, 
  ChevronDown
} from 'lucide-react';

export const Header = ({ currentView, onViewChange, onOpenAuth, onOpenLoginScreen }) => {
  const { user, logout } = useAuth();
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setUserMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const navItems = [
    { id: 'pesquisas', label: 'Busca', icon: Search, path: '/api/pesquisas' },
    { id: 'empresas', label: 'Empresas', icon: Building2, path: '/api/empresas' },
  ];

  return (
    <header className="sticky top-0 z-40 bg-white/90 dark:bg-vertex-dark-card/90 backdrop-blur-md border-b border-vertex-border dark:border-vertex-dark-border transition-colors duration-700 ease-in-out">
      <div className="max-w-[1600px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center gap-8">
            <div 
              className="cursor-pointer flex items-center transition-opacity hover:opacity-90 active:scale-[0.99] duration-150" 
              onClick={() => onViewChange('pesquisas')}
            >
              <div className="flex items-center">
                <img
                  src={logoClaro}
                  alt="VERTEX"
                  className="h-8 md:h-10 w-auto object-contain max-w-[160px] block dark:hidden"
                />
                <img
                  src={logoEscuro}
                  alt="VERTEX"
                  className="h-8 md:h-10 w-auto object-contain max-w-[160px] hidden dark:block"
                />
              </div>
            </div>

            <nav className="hidden md:flex items-center gap-1.5">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive = currentView === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => onViewChange(item.id)}
                    className={`flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-medium transition-all duration-200 ease-in-out ${
                      isActive
                        ? 'bg-vertex-orange text-white shadow-subtle shadow-vertex-orange/20 font-semibold'
                        : 'text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-slate-100 dark:hover:bg-vertex-dark-surface'
                    }`}
                  >
                    <Icon className={`w-3.5 h-3.5 ${isActive ? 'text-white' : 'text-vertex-muted dark:text-vertex-dark-muted'}`} />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-2.5 sm:gap-3">
            <ThemeToggle />

            <div className="h-5 w-[1px] bg-vertex-border dark:bg-vertex-dark-border hidden sm:block" />

            <div className="relative" ref={menuRef}>
              <button
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-2 px-2.5 py-1.5 rounded-md border border-vertex-border dark:border-vertex-dark-border bg-white dark:bg-vertex-dark-surface hover:bg-slate-50 dark:hover:bg-vertex-dark-card transition-all duration-200 text-xs font-medium text-vertex-dark dark:text-vertex-dark-text shadow-subtle"
              >
                <div className="w-6 h-6 rounded bg-vertex-charcoal text-white flex items-center justify-center font-bold text-[10px] tracking-tight">
                  {user?.nome ? user.nome.charAt(0).toUpperCase() : 'V'}
                </div>
                <span className="hidden sm:inline max-w-[130px] truncate">{user?.nome || 'Minha Conta'}</span>
                <ChevronDown className={`w-3.5 h-3.5 text-vertex-muted dark:text-vertex-dark-muted transition-transform duration-200 ${userMenuOpen ? 'rotate-180' : ''}`} />
              </button>

              {userMenuOpen && (
                <div className="absolute right-0 mt-2 w-64 bg-white dark:bg-vertex-dark-card rounded-lg border border-vertex-border dark:border-vertex-dark-border shadow-elevated dark:shadow-dark-card py-1.5 text-xs animate-in fade-in-50 zoom-in-95 duration-150">
                  <div className="px-3.5 py-2.5 border-b border-vertex-border-light dark:border-vertex-dark-border bg-slate-50/50 dark:bg-vertex-dark-surface/50">
                    <p className="font-semibold text-vertex-dark dark:text-vertex-dark-text truncate">{user?.nome || 'Usuário'}</p>
                    <p className="text-vertex-muted dark:text-vertex-dark-muted text-[11px] truncate font-mono">{user?.email || 'email@vertex.com'}</p>
                  </div>

                  <div className="py-1">
                    <button
                      onClick={() => {
                        setUserMenuOpen(false);
                        onOpenAuth('login');
                      }}
                      className="w-full text-left px-3.5 py-2 hover:bg-slate-50 dark:hover:bg-vertex-dark-surface flex items-center gap-2 text-vertex-dark dark:text-vertex-dark-text transition-colors duration-150"
                    >
                      <User className="w-3.5 h-3.5 text-vertex-orange" />
                      <span>Trocar de Conta</span>
                    </button>

                    <button
                      onClick={async () => {
                        setUserMenuOpen(false);
                        await logout();
                        if (onOpenLoginScreen) onOpenLoginScreen();
                      }}
                      className="w-full text-left px-3.5 py-2 hover:bg-red-50 dark:hover:bg-red-950/20 flex items-center gap-2 text-red-600 dark:text-red-400 transition-colors duration-150"
                    >
                      <LogOut className="w-3.5 h-3.5 text-red-500" />
                      <span>Encerrar Sessão</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="md:hidden flex items-center gap-1 py-2 border-t border-vertex-border-light dark:border-vertex-dark-border overflow-x-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentView === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onViewChange(item.id)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium shrink-0 transition-colors ${
                  isActive
                    ? 'bg-vertex-orange text-white'
                    : 'text-vertex-muted dark:text-vertex-dark-muted hover:text-vertex-dark dark:hover:text-vertex-dark-text hover:bg-vertex-border-light dark:hover:bg-vertex-dark-surface'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>
      </div>
    </header>
  );
};
