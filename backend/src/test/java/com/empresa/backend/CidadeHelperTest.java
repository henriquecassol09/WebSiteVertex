package com.empresa.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CidadeHelperTest {

    @Test
    @DisplayName("Deve reconhecer e normalizar todas as 6 cidades citadas")
    void deveReconhecerCidadesCitadas() {
        assertEquals("Laranjeiras do Sul", CidadeHelper.normalizarCidade("Laranjeiras do Sul"));
        assertEquals("Laranjeiras do Sul", CidadeHelper.normalizarCidade("laranjeiras do sul"));
        assertEquals("Laranjeiras do Sul", CidadeHelper.normalizarCidade("LARANJEIRAS DO SUL"));
        
        assertEquals("Rio Bonito do Iguaçu", CidadeHelper.normalizarCidade("Rio Bonito do Iguaçu"));
        assertEquals("Rio Bonito do Iguaçu", CidadeHelper.normalizarCidade("rio bonito do iguacu"));
        
        assertEquals("Porto Barreiro", CidadeHelper.normalizarCidade("Porto Barreiro"));
        assertEquals("Porto Barreiro", CidadeHelper.normalizarCidade("porto barreiro"));
        
        assertEquals("Virmond", CidadeHelper.normalizarCidade("Virmond"));
        assertEquals("Virmond", CidadeHelper.normalizarCidade("virmond"));
        
        assertEquals("Cantagalo", CidadeHelper.normalizarCidade("Cantagalo"));
        assertEquals("Cantagalo", CidadeHelper.normalizarCidade("cantagalo"));
        
        assertEquals("Nova Laranjeiras", CidadeHelper.normalizarCidade("Nova Laranjeiras"));
        assertEquals("Nova Laranjeiras", CidadeHelper.normalizarCidade("nova laranjeiras"));
    }

    @Test
    @DisplayName("Deve rejeitar cidades não atendidas, incluindo Candói, Curitiba e Cascavel")
    void deveRejeitarCidadesNaoCitadas() {
        assertNull(CidadeHelper.normalizarCidade("Curitiba"));
        assertNull(CidadeHelper.normalizarCidade("Cascavel"));
        assertNull(CidadeHelper.normalizarCidade("Guarapuava"));
        assertNull(CidadeHelper.normalizarCidade("Candói"));
        assertNull(CidadeHelper.normalizarCidade("candoi"));
        assertNull(CidadeHelper.normalizarCidade("Toledo"));
        assertNull(CidadeHelper.normalizarCidade("Pato Branco"));
        assertNull(CidadeHelper.normalizarCidade("São Paulo"));
        assertNull(CidadeHelper.normalizarCidade(""));
        assertNull(CidadeHelper.normalizarCidade(null));

        assertFalse(CidadeHelper.isCidadeCitada("Curitiba"));
        assertFalse(CidadeHelper.isCidadeCitada("Cascavel"));
        assertFalse(CidadeHelper.isCidadeCitada("Candói"));
        assertTrue(CidadeHelper.isCidadeCitada("Porto Barreiro"));
        assertTrue(CidadeHelper.isCidadeCitada("Laranjeiras do Sul"));
    }

    @Test
    @DisplayName("Deve identificar cidade por coordenadas quando dentro do raio geográfico das 6 cidades")
    void deveIdentificarCidadePorCoordenadas() {
        CidadeHelper.CidadeInfo info = CidadeHelper.identificarCidade(-25.4090, -52.4170, null);
        assertNotNull(info);
        assertEquals("Laranjeiras do Sul", info.nome());

        CidadeHelper.CidadeInfo infoPb = CidadeHelper.identificarCidade(-25.5470, -52.4080, null);
        assertNotNull(infoPb);
        assertEquals("Porto Barreiro", infoPb.nome());

        // Candói não deve ser identificado e deve ser rejeitado
        CidadeHelper.CidadeInfo infoCandoi = CidadeHelper.identificarCidade(-25.5786, -52.0394, null);
        assertNull(infoCandoi);
    }

    @Test
    @DisplayName("Deve rejeitar quando cidade informada for Candói ou fora das 6 cidades")
    void deveRejeitarQuandoAddrCityForCandoi() {
        CidadeHelper.CidadeInfo info = CidadeHelper.identificarCidade(-25.4086, -52.4161, "Candói");
        assertNull(info);
    }

    @Test
    @DisplayName("Deve rejeitar coordenadas muito distantes de qualquer centro citado")
    void deveRejeitarCoordenadasForaDoRaio() {
        CidadeHelper.CidadeInfo infoLonge = CidadeHelper.identificarCidade(-23.5505, -46.6333, null); // São Paulo
        assertNull(infoLonge);
    }
}
