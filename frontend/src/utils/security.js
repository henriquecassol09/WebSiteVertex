// Sanitização de URL contra XSS
export const sanitizarUrl = (url) => {
  if (!url || typeof url !== 'string') return '';
  const limpa = url.trim();
  if (!limpa) return '';

  const protocoloSuspeito = /^\s*(javascript|data|vbscript|file):/i;
  if (protocoloSuspeito.test(limpa)) {
    console.warn('[Segurança VERTEX] Tentativa de navegação com protocolo inseguro (XSS bloqueado):', limpa);
    return '#';
  }

  if (/[<>"']/.test(limpa)) {
    console.warn('[Segurança VERTEX] Caracteres perigosos detectados na URL (XSS bloqueado):', limpa);
    return '#';
  }

  if (/^https?:\/\//i.test(limpa)) {
    return limpa;
  }

  return `https://${limpa}`;
};

// Escape de caracteres HTML
export const escaparHtml = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
};
