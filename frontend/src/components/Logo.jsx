import React, { useState, useEffect } from 'react';
import logoClaro from '../assets/vertexClaro.png';
import logoEscuro from '../assets/vertexEscuro.png';
import { useTheme } from '../context/ThemeContext';

export const VertexLogo = ({
  className = 'h-9 sm:h-10 w-auto',
  imgClassName = '',
  alt = 'VERTEX'
}) => {
  let isDarkTheme = false;
  try {
    const themeContext = useTheme();
    isDarkTheme = themeContext?.isDark ?? false;
  } catch {
    isDarkTheme = typeof document !== 'undefined' && document.documentElement.classList.contains('dark');
  }

  const [, setTick] = useState(0);
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const observer = new MutationObserver(() => {
      setTick((t) => t + 1);
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    return () => observer.disconnect();
  }, []);

  const isDark = typeof document !== 'undefined'
    ? document.documentElement.classList.contains('dark') || isDarkTheme
    : isDarkTheme;

  return (
    <img
      src={isDark ? logoEscuro : logoClaro}
      alt={alt}
      width={934}
      height={512}
      className={`object-contain select-none shrink-0 ${className} ${imgClassName}`}
    />
  );
};
