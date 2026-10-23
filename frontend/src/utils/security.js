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

// Validação e sanitização estrita para foto de perfil (Anti-XSS e Anti-SSRF)
export const sanitizarUrlFoto = (url) => {
  if (!url || typeof url !== 'string') return null;
  const limpa = url.trim();
  if (!limpa) return null;

  // 1. Suporte seguro a Data URIs de imagens rasterizadas (JPEG, PNG, WebP)
  const prefixoDataUri = /^data:image\/(jpeg|png|webp|jpg);base64,/i;
  if (prefixoDataUri.test(limpa)) {
    // Bloqueia qualquer tentativa de inclusão de scripts ou tags SVG/HTML
    const inicio = limpa.substring(0, 150).toLowerCase();
    if (inicio.includes('svg') || inicio.includes('xml') || inicio.includes('script') || inicio.includes('html')) {
      return null;
    }
    // Protege contra quebra de atributos HTML (XSS)
    if (/[<>"']/.test(limpa)) {
      return null;
    }
    return limpa;
  }

  // 2. URLs externas: Apenas HTTPS é permitido
  if (!limpa.toLowerCase().startsWith('https://')) {
    return null;
  }

  // Bloqueio de injeção de caracteres perigosos
  if (/[<>"'\r\n\t\s]/.test(limpa)) {
    return null;
  }

  // Bloqueio de SVG (Prevenção contra Stored XSS)
  if (/\.(svg|svgz)($|\?|#)/i.test(limpa)) {
    return null;
  }

  // Bloqueio de endereços locais
  const hostMatch = limpa.match(/^https:\/\/([^\/?#:]+)/i);
  if (hostMatch) {
    const host = hostMatch[1].toLowerCase();
    if (host === 'localhost' || host.startsWith('127.') || host.startsWith('192.168.') || host.startsWith('10.') || host.startsWith('169.254.') || host === '0.0.0.0') {
      return null;
    }
  }

  return limpa;
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
