package com.synapse.crm.atendimento.domain.mensagem;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mensagem trocada dentro de um atendimento.
 *
 * <p>{@code midiaMetadados} e uma {@code String} com JSON, e nao um mapa ou um tipo do Jackson: a
 * coluna e {@code JSONB} e o dominio nao pode conhecer biblioteca de serializacao — o teste de
 * arquitetura reprova. Quem monta o JSON e a camada de fora.
 *
 * <p>{@code enviadoEm} nao e detalhe de auditoria: e a <b>chave de particao</b> da tabela
 * {@code mensagem}. Por isso e obrigatorio e viaja com o agregado em vez de ser preenchido por
 * {@code DEFAULT now()} no banco — quem escreve precisa saber em que instante esta escrevendo.
 */
public record Mensagem(
        UUID id,
        UUID atendimentoId,
        Remetente remetente,
        TipoMensagem tipo,
        String conteudo,
        String midiaUrl,
        String midiaMetadados,
        StatusEntrega statusEntrega,
        Instant enviadoEm,
        String opcoes) {

    public Mensagem(
            UUID id,
            UUID atendimentoId,
            Remetente remetente,
            TipoMensagem tipo,
            String conteudo,
            String midiaUrl,
            String midiaMetadados,
            StatusEntrega statusEntrega,
            Instant enviadoEm) {
        this(id, atendimentoId, remetente, tipo, conteudo, midiaUrl, midiaMetadados, statusEntrega, enviadoEm, null);
    }

    public Mensagem {
        Objects.requireNonNull(id, "id da mensagem e obrigatorio");
        Objects.requireNonNull(atendimentoId, "toda mensagem pertence a um atendimento");
        Objects.requireNonNull(remetente, "remetente e obrigatorio");
        Objects.requireNonNull(tipo, "tipo da mensagem e obrigatorio");
        Objects.requireNonNull(statusEntrega, "status de entrega e obrigatorio");
        Objects.requireNonNull(enviadoEm, "enviadoEm e obrigatorio: e a chave de particao");

        if (tipo.exigeMidia() && (midiaUrl == null || midiaUrl.isBlank())) {
            throw new IllegalArgumentException("mensagem do tipo " + tipo + " exige midiaUrl");
        }
        if (!tipo.exigeMidia() && !tipo.exigeOpcoes() && (conteudo == null || conteudo.isBlank())) {
            throw new IllegalArgumentException("mensagem de texto exige conteudo");
        }
        if (tipo.exigeOpcoes() && (opcoes == null || opcoes.isBlank())) {
            throw new IllegalArgumentException("mensagem interativa exige opcoes normalizadas");
        }
    }

    /** Texto simples — o caso de longe mais comum. */
    public static Mensagem texto(
            UUID id, UUID atendimentoId, Remetente remetente, String conteudo, Instant quando) {
        return new Mensagem(
                id,
                atendimentoId,
                remetente,
                TipoMensagem.TEXTO,
                conteudo,
                null,
                null,
                StatusEntrega.ENVIADO,
                quando,
                null);
    }

    /** Midia — imagem, audio ou documento. {@code conteudo} fica nulo; o invariante ja permite. */
    public static Mensagem midia(
            UUID id,
            UUID atendimentoId,
            Remetente remetente,
            TipoMensagem tipo,
            String midiaUrl,
            String midiaMetadados,
            Instant quando) {
        return new Mensagem(
                id, atendimentoId, remetente, tipo, null, midiaUrl, midiaMetadados,
                StatusEntrega.ENVIADO, quando, null);
    }

    public static Mensagem interativa(
            UUID id,
            UUID atendimentoId,
            Remetente remetente,
            TipoMensagem tipo,
            String conteudo,
            String opcoes,
            Instant quando) {
        return new Mensagem(
                id, atendimentoId, remetente, tipo, conteudo, null, null,
                StatusEntrega.ENVIADO, quando, opcoes);
    }

    public boolean ehRecebida() {
        return remetente.ehDoLead();
    }
}
