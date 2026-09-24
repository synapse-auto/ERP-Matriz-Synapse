package com.synapse.crm.atendimento.interfaces.internal;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/**
 * Tipos que a Automação pode registrar em {@code /internal/v1}, congelados no contrato publicado.
 *
 * <p>Existe separado de {@link TipoMensagem} de propósito: o domínio ganha tipos que só chegam do
 * cliente (como {@code CONTATO}), e expor o enum do domínio no DTO mudaria o contrato da Automação
 * de todos os filhos a cada variante nova. Ampliar esta lista é decisão de contrato, com snapshot e
 * coordenação com o n8n.
 */
enum TipoMensagemDaAutomacao {
    TEXTO,
    AUDIO,
    IMAGEM,
    DOCUMENTO,
    VIDEO,
    BOTOES,
    LISTA,
    LOCALIZACAO;

    TipoMensagem paraDominio() {
        return TipoMensagem.valueOf(name());
    }
}
