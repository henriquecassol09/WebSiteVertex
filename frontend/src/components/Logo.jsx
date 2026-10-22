import React from 'react';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';

export const VertexLogo = ({ className = 'w-36 h-10', alt = 'VERTEX' }) => {
  return (
    <div className={`flex items-center justify-center select-none ${className}`}>
      <img
        src={logoClaro}
        alt={alt}
        className="w-full h-full object-contain block dark:hidden"
      />
      <img
        src={logoEscuro}
        alt={alt}
        className="w-full h-full object-contain hidden dark:block"
      />
    </div>
  );
};
