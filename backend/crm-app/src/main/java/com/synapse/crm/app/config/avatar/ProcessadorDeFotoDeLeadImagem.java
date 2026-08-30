package com.synapse.crm.app.config.avatar;

import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.lead.foto.FotoDeLeadInvalidaException;
import com.synapse.crm.core.application.lead.foto.ProcessadorDeFotoDeLead;
import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;
import com.synapse.crm.equipe.application.usuario.ProcessadorDeAvatar;

/**
 * A foto do lead passa pelo mesmo reprocessamento da foto de usuario (E50).
 *
 * <p>crm-core nao pode depender de crm-equipe, entao a <b>porta</b> e duplicada — mas a logica de
 * validar magic bytes, cortar no centro, redimensionar para 256px e reencodar em PNG existe uma vez
 * so, em {@code ProcessadorDeAvatarImagem}. Este adaptador mora em crm-app, o unico modulo que
 * enxerga os dois lados, e so traduz a excecao de fronteira.
 *
 * <p>Reencodar ja descarta EXIF e qualquer outro metadado: nao existe "tirar EXIF" a fazer, e
 * consequencia do reencode.
 */
@Component
class ProcessadorDeFotoDeLeadImagem implements ProcessadorDeFotoDeLead {

    private final ProcessadorDeAvatar reprocessamento;

    ProcessadorDeFotoDeLeadImagem(ProcessadorDeAvatar reprocessamento) {
        this.reprocessamento = reprocessamento;
    }

    @Override
    public Resultado processar(byte[] original) {
        try {
            ProcessadorDeAvatar.Resultado pronto = reprocessamento.processar(original);
            return new Resultado(pronto.conteudo(), pronto.mimetype());
        } catch (FotoDeUsuarioInvalidaException erro) {
            throw new FotoDeLeadInvalidaException(erro.getMessage());
        }
    }
}
