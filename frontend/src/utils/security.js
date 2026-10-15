/**
 * Utilitários de Segurança e Sanitização contra Cross-Site Scripting (XSS).
 */

/**
 * Valida e sanitiza uma URL para uso seguro em tags <a href="...">.
 * Bloqueia estritamente pseudo-protocolos perigosos (javascript:, data:, vbscript:)
 * e garante que apenas protocolos seguros (http://, https://) sejam executados.
 *
 * @param {string} url - URL a ser validada
 * @returns {string} - URL segura sanitizada ou '#' se for insegura
 */
export const sanitizarUrl = (url) => {
  if (!url || typeof url !== 'string') return '';
  const limpa = url.trim();
  if (!limpa) return '';

  // Bloqueio rigoroso de injeção de script via protocolo (ex: javascript:alert(1), data:text/html, vbscript:)
  const protocoloSuspeito = /^\s*(javascript|data|vbscript|file):/i;
  if (protocoloSuspeito.test(limpa)) {
    console.warn('[Segurança VERTEX] Tentativa de navegação com protocolo inseguro (XSS bloqueado):', limpa);
    return '#';
  }

  // Remove caracteres que tentem quebrar atributos HTML ou injetar tags
  if (/[<>"']/.test(limpa)) {
    console.warn('[Segurança VERTEX] Caracteres perigosos detectados na URL (XSS bloqueado):', limpa);
    return '#';
  }

  // Se já começar com http:// ou https://, retorna diretamente
  if (/^https?:\/\//i.test(limpa)) {
    return limpa;
  }

  // Se for um domínio simples (ex: "empresa.com.br"), prefixa com https://
  return `https://${limpa}`;
};

/**
 * Escapa caracteres HTML perigosos para exibição segura de texto puro.
 *
 * @param {string} str - Texto a ser escapado
 * @returns {string} - Texto seguro com entidades HTML
 */
export const escaparHtml = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
};
