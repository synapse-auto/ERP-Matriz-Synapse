package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.StatusDaCampanha;
import com.synapse.crm.campanhas.domain.TransicaoDeStatusInvalidaException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A parte transacional de iniciar: materializa os destinatarios (INSERT ... SELECT por conjunto, uma vez) e
 * muda o status, na mesma transacao. Bean separado de {@link IniciarCampanhaUseCase} para a conferencia do
 * template ficar fora da transacao sem auto-invocacao do proxy.
 */
@Component
public class ConfirmarInicioDaCampanha {

    private final CampanhaRepositorio campanhas;
    private final PublicoRepositorio publico;
    private final ConfiguracaoDeCampanhas configuracao;
    private final Clock relogio;
    private final ZoneId fuso;

    public ConfirmarInicioDaCampanha(
            CampanhaRepositorio campanhas,
            PublicoRepositorio publico,
            ConfiguracaoDeCampanhas configuracao,
            Clock relogio,
            ZoneId fuso) {
        this.campanhas = campanhas;
        this.publico = publico;
        this.configuracao = configuracao;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Campanha executar(UUID id) {
        Campanha campanha = campanhas.bloquearPorId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        if (campanha.status() != StatusDaCampanha.RASCUNHO) {
            throw new TransicaoDeStatusInvalidaException(campanha.status(), "iniciar");
        }
        ConfiguracaoDeCampanhas.Parametros parametros = configuracao.atuais();
        if (campanha.limiteDiario() > parametros.tetoDiarioDaInstancia()) {
            throw new CampanhaInvalidaException(
                    "LIMITE_ACIMA_DO_TETO",
                    "o limite diario (" + campanha.limiteDiario() + ") passa do teto da instancia ("
                            + parametros.tetoDiarioDaInstancia() + "); reduza o limite antes de iniciar");
        }
        publico.materializar(id, campanha.filtro(), parametros.cooldownProativoHoras(), fuso);
        Campanha comPublico = campanhas.porId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
        if (comPublico.contadores().pendentes() == 0) {
            throw new CampanhaInvalidaException(
                    "SEM_DESTINATARIOS", "nenhum contato elegivel: revise o filtro do publico");
        }
        Instant agora = Instant.now(relogio);
        campanhas.atualizar(campanha.iniciar(agora));
        return campanhas.porId(id).orElseThrow(() -> new CampanhaNaoEncontradaException(id));
    }
}
