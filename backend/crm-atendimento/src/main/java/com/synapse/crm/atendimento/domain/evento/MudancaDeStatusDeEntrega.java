package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * A mensagem avancou no ciclo de entrega: {@code PENDENTE -> ENVIADO}, ou {@code -> FALHOU}.
 *
 * <p>Existe porque a E05 tornou o envio honestamente assincrono — a mensagem nasce {@code PENDENTE} e
 * so muda de estado quando o publisher da outbox fala com o provedor, minutos ou segundos depois da
 * transacao que a criou. Sem este evento a tela ficaria presa no relogio ate o atendente recarregar a
 * pagina.
 *
 * <p>{@code FALHOU} e a transicao que mais importa aqui: um tique de "enviado" que nunca devia ter
 * aparecido e pior que um alerta — o atendente precisa saber, enquanto o cliente ainda esta na
 * conversa, que a mensagem nao saiu.
 */
public record MudancaDeStatusDeEntrega(
        UUID mensagemId, UUID atendimentoId, UUID leadId, String statusEntrega, Instant ocorridoEm) {}
