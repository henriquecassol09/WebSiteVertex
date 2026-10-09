/**
 * Helper para detecção e refinamento inteligente de categorias e segmentos no frontend.
 * Garante que estabelecimentos não fiquem como o genérico "Comércio Local".
 */
export function detectarCategoria(categoria, nome) {
  if (categoria && 
      categoria.trim() !== '' && 
      categoria.toLowerCase() !== 'comércio local' && 
      categoria.toLowerCase() !== 'comercio local' && 
      categoria.toLowerCase() !== 'não informado') {
    return categoria;
  }

  if (!nome) return categoria || 'Comércio e Serviços';

  const n = nome
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');

  // Pizzaria
  if (n.includes('pizza') || n.includes('pizzaria') || n.includes('forneria')) return 'Pizzaria';

  // Farmácia
  if (n.includes('farma') || n.includes('droga') || n.includes('medic') || n.includes('manipulacao')) return 'Farmácia';

  // Roupas e Calçados
  if (n.includes('calcado') || n.includes('sapat') || n.includes('tenis')) return 'Loja de Calçados';
  if (n.includes('roupa') || n.includes('moda') || n.includes('confec') || n.includes('boutique') || 
      n.includes('vestu') || n.includes('looks') || n.includes('estilo') || n.includes('fashion') || 
      n.includes('jeans') || n.includes('lingerie') || n.includes('malhas') || n.includes('intim')) {
    return 'Loja de Roupas';
  }

  // Alimentação e Bebidas
  if (n.includes('padaria') || n.includes('panific') || n.includes('pao') || n.includes('confeit') || n.includes('bolos')) return 'Panificadora / Confeitaria';
  if (n.includes('acougue') || n.includes('carnes') || n.includes('frigorifico')) return 'Açougue / Casa de Carnes';
  if (n.includes('sorvet') || n.includes('gelato') || n.includes('acai')) return 'Sorveteria / Açaiteria';
  if (n.includes('lanche') || n.includes('burger') || n.includes('hamburg') || n.includes('pastel') || n.includes('hotdog')) return 'Lanchonete / Hamburgueria';
  if (n.includes('restaurante') || n.includes('churrasc') || n.includes('gourmet') || n.includes('buffet') || n.includes('grill')) return 'Restaurante';
  if (n.includes('cafe') || n.includes('bistro') || n.includes('doceria')) return 'Cafeteria / Bistrô';
  if (n.includes('bar ') || n.includes('bar.') || n.includes('pub') || n.includes('choperia') || n.includes('boteco') || n.includes('cerveja') || n.includes('bebidas')) return 'Bar / Choperia';

  // Mercados
  if (n.includes('mercado') || n.includes('supermercado') || n.includes('mercearia') || n.includes('armazem') || n.includes('emporio') || n.includes('hiper')) return 'Supermercado / Mercado';

  // Automotivo
  if (n.includes('auto peca') || n.includes('autope') || n.includes('pecas auto')) return 'Loja de Autopeças';
  if (n.includes('mecanic') || n.includes('oficina') || n.includes('auto eletric') || n.includes('funilaria') || n.includes('auto center') || n.includes('car service')) return 'Oficina Mecânica';
  if (n.includes('pneu') || n.includes('borracharia')) return 'Borracharia e Pneus';
  if (n.includes('lavacar') || n.includes('lava jato') || n.includes('estetica auto')) return 'Lava Jato';
  if (n.includes('posto ') || n.includes('combustiv')) return 'Posto de Combustíveis';
  if (n.includes('moto ') || n.includes('motos') || n.includes('motocicl')) return 'Oficina / Loja de Motos';

  // Saúde Animal e Agro
  if (n.includes('veterin') || n.includes('vet ') || n.includes('vet.')) return 'Clínica Veterinária';
  if (n.includes('pet') || n.includes('banho e tosa') || n.includes('racoes')) return 'Pet Shop';
  if (n.includes('agro') || n.includes('agricol') || n.includes('sementes') || n.includes('rural')) return 'Agropecuária';

  // Casa, Móveis e Construção
  if (n.includes('tinta')) return 'Loja de Tintas';
  if (n.includes('construc') || n.includes('materiais') || n.includes('madeireira') || n.includes('ferrag')) return 'Materiais de Construção';
  if (n.includes('moveis') || n.includes('estofad') || n.includes('colchao') || n.includes('marcenaria')) return 'Loja de Móveis e Decoração';
  if (n.includes('vidro') || n.includes('vidracaria')) return 'Vidraçaria';
  if (n.includes('serralh')) return 'Serralheria';

  // Tecnologia e Óptica
  if (n.includes('celular') || n.includes('smart') || n.includes('phone')) return 'Loja de Celulares e Acessórios';
  if (n.includes('informatica') || n.includes('computad') || n.includes('eletron')) return 'Eletrônicos e Informática';
  if (n.includes('otica') || n.includes('optica') || n.includes('oculos') || n.includes('relogio') || n.includes('joia') || n.includes('relojoaria')) return 'Óptica e Joalheria';

  // Beleza e Saúde
  if (n.includes('barbearia') || n.includes('barber')) return 'Barbearia';
  if (n.includes('salao') || n.includes('cabeleireir') || n.includes('hair') || n.includes('studio de beleza')) return 'Salão de Beleza / Cabeleireiro';
  if (n.includes('estetica') || n.includes('cosmet') || n.includes('unhas') || n.includes('manicure') || n.includes('sobrancelh')) return 'Estética e Cosméticos';
  if (n.includes('academia') || n.includes('fitness') || n.includes('crossfit') || n.includes('pilates')) return 'Academia';
  if (n.includes('dentist') || n.includes('odont') || n.includes('sorriso')) return 'Consultório Odontológico';
  if (n.includes('clinica') || n.includes('medica') || n.includes('laboratorio') || n.includes('saude')) return 'Clínica Médica / Saúde';

  // Outros Serviços
  if (n.includes('hotel') || n.includes('pousada')) return 'Hotelaria';
  if (n.includes('imobiliaria') || n.includes('imoveis')) return 'Imobiliária';
  if (n.includes('advoc') || n.includes('advog')) return 'Escritório de Advocacia';
  if (n.includes('contab')) return 'Escritório de Contabilidade';
  if (n.includes('papelaria') || n.includes('livraria')) return 'Papelaria e Livraria';
  if (n.includes('flor') || n.includes('plantas')) return 'Floricultura';
  if (n.includes('bike') || n.includes('biciclet')) return 'Bicicletaria';
  if (n.includes('brinqued')) return 'Loja de Brinquedos';
  if (n.includes('bazar') || n.includes('variedad') || n.includes('presentes')) return 'Loja de Presentes e Variedades';
  if (n.includes('gas ') || n.includes('agua mineral')) return 'Distribuidora de Gás e Água';

  return 'Comércio e Serviços';
}
