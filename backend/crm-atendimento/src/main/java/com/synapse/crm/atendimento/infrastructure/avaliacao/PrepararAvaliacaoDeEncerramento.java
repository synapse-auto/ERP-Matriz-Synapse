package com.synapse.crm.atendimento.infrastructure.avaliacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.OutboxDeAvaliacao;
import com.synapse.crm.atendimento.application.SolicitacaoDeAvaliacao;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/**
 * Grava a intencao de avaliacao no outbox, dentro da transacao de finalizacao individual.
 *
 * <p><b>O gate desta classe nao e o toggle da tela de Automacao.</b> A chave
 * {@code avaliacao_atendimento.habilitada} (V55) existe para o n8n ler em
 * {@code GET /internal/v1/automation-config} e decidir se manda os tres botoes ao cliente
 * (EV-08 secoes 4 e 5). O CRM nao a le, por RN-CRM-07: "O CRM configura a automacao; nao a
 * executa." Quem decide aqui e {@code config.configurada()} — URL, token e nome de header
 * validos. Ligar a chave sem configurar o webhook nao faz o CRM enfileirar; desligar a chave nao
 * faz o CRM parar. Sao dois interruptores de donos diferentes, e trocar um pelo outro esconderia
 * a falha de configuracao atras de um toggle verde na tela.
 *
 * <p>A {@code IllegalStateException} do {@code catch} reverte a finalizacao de proposito: falha
 * de <b>banco</b> nao e falha de n8n, e a regra de precedencia so protege o caminho de rede.
 */
@Component
class PrepararAvaliacaoDeEncerramento implements SolicitacaoDeAvaliacao {

    private static final Logger log = LoggerFactory.getLogger(PrepararAvaliacaoDeEncerramento.class);
    private final OutboxDeAvaliacao outbox;
    private final LeadNoCaminhoDeMensagem leads;
    private final AvaliacaoWebhookProperties config;

    PrepararAvaliacaoDeEncerramento(
            OutboxDeAvaliacao outbox, LeadNoCaminhoDeMensagem leads, AvaliacaoWebhookProperties config) {
        this.outbox = outbox;
        this.leads = leads;
        this.config = config;
    }

    @Override
    public void preparar(Atendimento atendimento) {
        TransacaoObrigatoria.exigir("prepararAvaliacaoDeEncerramento");
        if (atendimento.estaAberto()) {
            throw new IllegalArgumentException("avaliacao exige atendimento finalizado");
        }
        if (atendimento.atendenteId() == null) {
            log.info("Avaliacao nao enfileirada: atendimento={} motivo=SEM_RESPONSAVEL", atendimento.id());
            return;
        }
        if (!config.configurada()) {
            log.warn("Avaliacao nao enfileirada: atendimento={} motivo=CONFIGURACAO_AUSENTE_OU_INVALIDA",
                    atendimento.id());
            return;
        }
        String telefone = leads.contatoParaEnvio(atendimento.leadId())
                .map(LeadNoCaminhoDeMensagem.ContatoParaEnvio::telefone).orElse(null);
        // Contrato wa_id/E.164. Nao normalizar nem inventar DDI aqui: a escrita do lead ja o faz.
        if (telefone == null || !telefone.matches("[1-9][0-9]{9,14}")) {
            log.warn("Avaliacao nao enfileirada: atendimento={} motivo=SEM_TELEFONE_VALIDO", atendimento.id());
            return;
        }
        try {
            outbox.enfileirar(atendimento.id(), atendimento.leadId(), atendimento.atendenteId(),
                    telefone, atendimento.finalizadoEm());
        } catch (DataAccessException e) {
            log.error("Avaliacao: persistencia falhou atendimento={} classe={}",
                    atendimento.id(), e.getClass().getSimpleName());
            // Rollback continua obrigatorio; erro SQL pode conter o payload/telefone nos detalhes.
            throw new IllegalStateException("Falha ao persistir intencao de avaliacao; finalizacao revertida");
        }
    }
}
