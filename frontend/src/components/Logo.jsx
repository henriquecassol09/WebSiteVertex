import React from 'react';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';

/**
 * Componente oficial da marca VERTEX utilizando as imagens reais fornecidas pelo usuário:
 * - Tema Claro: vertexClaro.png
 * - Tema Escuro: vertexEscuro.png
 */
export const VertexLogo = ({ className = 'w-36 h-10', alt = 'VERTEX' }) => {
  return (
    <div className={`flex items-center justify-center select-none ${className}`}>
      {/* Imagem no Tema Claro (visível apenas quando não está em dark mode) */}
      <img
        src={logoClaro}
        alt={alt}
        className="w-full h-full object-contain block dark:hidden"
      />
      {/* Imagem no Tema Escuro (visível apenas em dark mode) */}
      <img
        src={logoEscuro}
        alt={alt}
        className="w-full h-full object-contain hidden dark:block"
      />
    </div>
  );
};
