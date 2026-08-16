package com.synapse.crm.app.saude.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.ConfiguracaoCanalAtivo;

/** Torna visivel no startup a configuracao que faria a entrada do webhook falhar fechada. */
@Component
public class DiagnosticoCanalNoStartup {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticoCanalNoStartup.class);

    private final CanalCredencialAtivaRepositorio credenciais;

    public DiagnosticoCanalNoStartup(CanalCredencialAtivaRepositorio credenciais) {
        this.credenciais = credenciais;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void diagnosticar() {
        try {
            ConfiguracaoCanalAtivo configuracao = credenciais.carregarConfiguracao();
            if (configuracao.quantidadeCanaisAtivos() < 1) {
                log.error(
                        "Nenhum canal ativo cadastrado: webhooks serao recusados. Execute o provisionamento da instancia.");
            } else if (configuracao.quantidadeSemIdentificadorExterno() > 0) {
                log.error(
                        "Existem {} canal(is) ativo(s) sem phone_number_id: webhooks serao recusados."
                                + " Execute o provisionamento para preencher canal_credencial.identificador_externo.",
                        configuracao.quantidadeSemIdentificadorExterno());
            }
        } catch (RuntimeException e) {
            log.error(
                    "Nao foi possivel validar o phone_number_id dos canais no startup;"
                            + " /health/critical deve ser consultado antes de liberar trafego.",
                    e);
        }
    }
}
