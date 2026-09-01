package com.synapse.crm.atendimento.application.referencia;

import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.TipoReferencia;

/** Monta a referencia desnormalizada usada tanto em respostas de entrada quanto de saida. */
public final class MontadorDeReferenciaDeMensagem {

    private MontadorDeReferenciaDeMensagem() {}

    public static ReferenciaDeMensagem resposta(OrigemDeMensagem origem, String contextoWamid) {
        var mensagem = origem.mensagem();
        return new ReferenciaDeMensagem(
                TipoReferencia.RESPOSTA,
                mensagem.id(),
                mensagem.enviadoEm(),
                mensagem.atendimentoId(),
                CitacaoDeMensagem.autorDe(
                        mensagem.remetente().tipo(), origem.leadNome(), origem.remetenteNome()),
                mensagem.tipo().name(),
                CitacaoDeMensagem.previaDe(
                        mensagem.tipo(), mensagem.conteudo(), mensagem.midiaMetadados()),
                contextoWamid);
    }

    public static CitacaoDeMensagem citacaoDe(ReferenciaDeMensagem referencia) {
        if (referencia == null) {
            return null;
        }
        return new CitacaoDeMensagem(
                referencia.origemMensagemId(),
                referencia.tipo().name(),
                referencia.citacaoAutor(),
                referencia.citacaoTipo(),
                referencia.citacaoPrevia());
    }
}
