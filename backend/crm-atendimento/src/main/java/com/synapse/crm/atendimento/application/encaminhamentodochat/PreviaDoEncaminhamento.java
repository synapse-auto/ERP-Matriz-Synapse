package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.UUID;

/**
 * O que a tela mostra antes de confirmar. Nada aqui é gravado: é leitura do destino e do conteúdo,
 * sob o alcance do usuário. O telefone vai sempre mascarado.
 *
 * @param efeito o que o envio faz com a responsabilidade do atendimento (decisão de negócio de 05/10/2026)
 * @param bloqueio {@code null} quando pode enviar
 */
public record PreviaDoEncaminhamento(
        UUID atendimentoId,
        String clienteNome,
        String telefoneMascarado,
        String statusAtendimento,
        String responsavelNome,
        Efeito efeito,
        String tipo,
        String texto,
        String legenda,
        String nomeArquivo,
        String mimetype,
        Long tamanhoBytes,
        boolean podeEnviar,
        MotivoDeBloqueio bloqueio) {

    public enum Efeito {
        /** Atendimento sem responsável: quem encaminha assume o lead (RN-CRM-06). */
        ASSUME_O_LEAD,
        /** Já existe responsável (outra pessoa); ele continua e quem encaminha recebe convite. */
        MANTEM_RESPONSAVEL_E_CONVIDA,
        /** Já existe responsável e quem encaminha já participa: nada muda, sem novo convite. */
        MANTEM_RESPONSAVEL,
        /** Quem encaminha já é o responsável. */
        VOCE_E_RESPONSAVEL
    }
}
