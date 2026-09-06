package com.synapse.crm.automacaoconfig.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.MarcaDaInstanciaInvalidaException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.RegrasDeAnexoBase;

/** Valida e troca a logo no storage, mantendo a referencia antiga ate a nova estar persistida. */
@Service
public class AtualizarLogoDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final ArmazenamentoDeMidia armazenamento;
    private final DetectorDeTipoReal detector;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public AtualizarLogoDaInstanciaUseCase(
            MarcaDaInstanciaRepositorio marcas,
            ArmazenamentoDeMidia armazenamento,
            DetectorDeTipoReal detector,
            UsuarioContext usuario,
            Clock relogio) {
        this.marcas = marcas;
        this.armazenamento = armazenamento;
        this.detector = detector;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void executar(byte[] conteudo, String nomeArquivo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new MarcaDaInstanciaInvalidaException("logo da instancia e obrigatoria");
        }

        String mimetype = detector.detectar(conteudo);
        boolean imagemPermitida = RegrasDeAnexoBase.categoriaDe(mimetype)
                .filter(categoria -> categoria == CategoriaDeMidia.IMAGEM)
                .isPresent();
        if (!imagemPermitida) {
            throw new MarcaDaInstanciaInvalidaException("tipo de logo nao permitido: " + mimetype);
        }

        // O contrato publico da rota continua sendo image/png, como era para logo.png. A
        // allowlist de logo, portanto, aceita somente PNG; o detector usa magic bytes, nao extensao.
        if (!"image/png".equals(mimetype)) {
            throw new MarcaDaInstanciaInvalidaException("a logo deve ser uma imagem PNG");
        }

        var anterior = marcas.obter().logoReferenciaStorage();
        String nomeSanitizado = sanitizarNome(nomeArquivo);
        String novaReferencia = armazenamento.salvar(conteudo, nomeSanitizado, mimetype);
        try {
            marcas.salvarLogo(novaReferencia, usuario.atual().id(), Instant.now(relogio));
        } catch (RuntimeException erro) {
            armazenamento.remover(novaReferencia);
            throw erro;
        }

        if (anterior != null && !anterior.equals(novaReferencia)) {
            armazenamento.remover(anterior);
        }
    }

    private static String sanitizarNome(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return "logo.png";
        }
        String semCaminho = nomeArquivo.replace('\\', '/');
        int barra = semCaminho.lastIndexOf('/');
        String base = barra >= 0 ? semCaminho.substring(barra + 1) : semCaminho;
        String sanitizado = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitizado.isBlank() ? "logo.png" : sanitizado;
    }
}
