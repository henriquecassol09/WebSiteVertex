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
    @DisplayName("Deve rejeitar qualquer cidade que não esteja entre as 6 citadas")
    void deveRejeitarCidadesNaoCitadas() {
        assertNull(CidadeHelper.normalizarCidade("Curitiba"));
        assertNull(CidadeHelper.normalizarCidade("Cascavel"));
        assertNull(CidadeHelper.normalizarCidade("Marquinho"));
        assertNull(CidadeHelper.normalizarCidade("Candói"));
        assertNull(CidadeHelper.normalizarCidade("Guarapuava"));
        assertNull(CidadeHelper.normalizarCidade("Toledo"));
        assertNull(CidadeHelper.normalizarCidade("Pato Branco"));
        assertNull(CidadeHelper.normalizarCidade("São Paulo"));
        assertNull(CidadeHelper.normalizarCidade("Quedas do Iguaçu"));
        assertNull(CidadeHelper.normalizarCidade(""));
        assertNull(CidadeHelper.normalizarCidade(null));

        assertFalse(CidadeHelper.isCidadeCitada("Curitiba"));
        assertFalse(CidadeHelper.isCidadeCitada("Cascavel"));
        assertFalse(CidadeHelper.isCidadeCitada("Marquinho"));
        assertTrue(CidadeHelper.isCidadeCitada("Porto Barreiro"));
        assertTrue(CidadeHelper.isCidadeCitada("Laranjeiras do Sul"));
    }

    @Test
    @DisplayName("Deve identificar cidade por coordenadas quando dentro do raio geográfico")
    void deveIdentificarCidadePorCoordenadas() {
        // Coordenadas centrais de Laranjeiras do Sul (-25.4086, -52.4161)
        CidadeHelper.CidadeInfo info = CidadeHelper.identificarCidade(-25.4090, -52.4170, null);
        assertNotNull(info);
        assertEquals("Laranjeiras do Sul", info.nome());

        // Coordenadas de Porto Barreiro (-25.5469, -52.4078)
        CidadeHelper.CidadeInfo infoPb = CidadeHelper.identificarCidade(-25.5470, -52.4080, null);
        assertNotNull(infoPb);
        assertEquals("Porto Barreiro", infoPb.nome());
    }

    @Test
    @DisplayName("Deve rejeitar ponto geográfico se addr:city for de cidade vizinha não citada")
    void deveRejeitarQuandoAddrCityForDeOutraCidade() {
        // Mesmo que a coordenada caia perto, se o endereço explicitamente diz "Marquinho", rejeita!
        CidadeHelper.CidadeInfo info = CidadeHelper.identificarCidade(-25.4086, -52.4161, "Marquinho");
        assertNull(info);

        CidadeHelper.CidadeInfo infoCandoi = CidadeHelper.identificarCidade(-25.3744, -52.0089, "Candói");
        assertNull(infoCandoi);
    }

    @Test
    @DisplayName("Deve rejeitar coordenadas muito distantes de qualquer centro citado")
    void deveRejeitarCoordenadasForaDoRaio() {
        // Coordenada em Guarapuava (-25.3953, -51.4625) ~100 km a leste
        CidadeHelper.CidadeInfo infoLonge = CidadeHelper.identificarCidade(-25.3953, -51.4625, null);
        assertNull(infoLonge);
    }
}
