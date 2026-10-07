import React from 'react';
import { useTheme } from '../context/ThemeContext';
import { Moon, Sun } from 'lucide-react';

/**
 * Botão interruptor (Toggle Switch) idêntico ao solicitado pelo usuário:
 * - Estado Desligado (Tema Claro): Base cinza-escuro com botão interno cinza-médio à esquerda.
 * - Estado Ligado (Tema Escuro Ativado): Base verde vibrante com botão interno branco à direita.
 */
export const ThemeToggle = ({ showLabel = true, className = '' }) => {
  const { isDark, toggleTheme } = useTheme();

  return (
    <div className={`flex items-center gap-2 select-none ${className}`}>
      {showLabel && (
        <span className="text-[11px] font-medium text-vertex-muted dark:text-vertex-dark-muted hidden sm:inline flex items-center gap-1">
          {isDark ? (
            <>
              <Moon className="w-3 h-3 text-emerald-400" />
              <span>Tema Escuro</span>
            </>
          ) : (
            <>
              <Sun className="w-3 h-3 text-amber-500" />
              <span>Tema Claro</span>
            </>
          )}
        </span>
      )}

      <button
        type="button"
        role="switch"
        aria-checked={isDark}
        onClick={toggleTheme}
        title={isDark ? 'Desativar Tema Escuro' : 'Ativar Tema Escuro'}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-all duration-300 ease-in-out focus:outline-none focus:ring-2 focus:ring-emerald-500/30 active:scale-95 shadow-inner ${
          isDark ? 'bg-[#22C55E]' : 'bg-[#2C3036]'
        }`}
      >
        <span className="sr-only">Alternar tema escuro</span>
        {/* Thumb circular com animação fluida */}
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] shadow-md ${
            isDark
              ? 'translate-x-[22px] bg-white'
              : 'translate-x-[2px] bg-[#8E95A2]'
          }`}
        />
      </button>
    </div>
  );
};
