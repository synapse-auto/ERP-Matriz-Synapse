package com.synapse.crm.core.domain.filtro;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Valor ja convertido para o tipo do campo.
 *
 * <p>Existe para que <strong>nenhuma string crua do cliente chegue ao interpretador</strong>. Depois
 * daqui nao ha mais "texto que talvez seja um UUID": ou virou {@link Referencia}, ou a requisicao ja
 * foi rejeitada. Um {@code valor} contendo {@code ' OR 1=1--} num campo de etapa morre na conversao,
 * muito antes de existir qualquer clausula.
 *
 * <p>Selado pelo motivo de sempre neste projeto: o interpretador casa exaustivamente sobre estas
 * alternativas. Um tipo de valor novo quebra o build ate ganhar traducao.
 */
public sealed interface ValorDeFiltro {

    record Texto(String valor) implements ValorDeFiltro {}

    record Numero(long valor) implements ValorDeFiltro {}

    record Data(Instant valor) implements ValorDeFiltro {}

    record Status(StatusBasicoLead valor) implements ValorDeFiltro {}

    record Referencia(UUID valor) implements ValorDeFiltro {}

    /** So para campo customizado do tipo BOOLEANO (E06b) — nenhum campo fixo do lead e booleano. */
    record Booleano(boolean valor) implements ValorDeFiltro {}
}
