package com.synapse.crm.atendimento.infrastructure.webhook;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.ArmazenamentoDeMidia;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Drena a fila de entrada: traduz o payload cru e registra a mensagem.
 *
 * <p>Espelho do publisher da outbox, no sentido oposto. Fica fora do ciclo de request de proposito:
 * assim uma falha de traducao, um lead que precisa ser criado ou um pico de carga nao viram timeout
 * no webhook — e timeout no webhook vira reentrega, que vira mais carga.
 *
 * <p>Bean separado de {@link ProcessadorDeWebhookEntrada} de proposito: quem tem o {@code @Scheduled}
 * e o outro, e ele chama {@link #rodada()} atraves desta instancia injetada — uma chamada externa de
 * verdade, que passa pelo proxy do Spring. Ver o Javadoc de {@link ProcessadorDeWebhookEntrada} para
 * o porque (E07b).
 *
 * <p>Roda em {@link ContextoDeServico}, publicado por {@link ProcessadorDeWebhookEntrada} antes de
 * chamar: nao ha usuario numa mensagem que chega do cliente. E o contexto tambem e o que permite
 * {@code resolverPorTelefone} enxergar leads de qualquer atendente — com contexto de usuario, a RLS
 * esconderia o lead de um colega e criariamos um duplicado.
 */
@Component
public class ProcessadorDeWebhookEntradaOperacoes {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeWebhookEntradaOperacoes.class);

    public static final String MARCADOR_ALARME = "[ALERTA_WEBHOOK_ESGOTADO]";

    private final WebhookEntrada entrada;
    private final TradutorDeCanal tradutor;
    private final RegistrarMensagemRecebidaUseCase registrar;
    private final LeadNoCaminhoDeMensagem leads;
    private final CanalGateway canal;
    private final ArmazenamentoDeMidia armazenamento;
    private final ObjectMapper json;
    private final Clock relogio;
    private final int lote;
    private final int maximoDeTentativas;

    public ProcessadorDeWebhookEntradaOperacoes(
            WebhookEntrada entrada,
            TradutorDeCanal tradutor,
            RegistrarMensagemRecebidaUseCase registrar,
            LeadNoCaminhoDeMensagem leads,
            CanalGateway canal,
            ArmazenamentoDeMidia armazenamento,
            ObjectMapper json,
            Clock relogio,
            @Value("${synapse.canal.webhook.lote:50}") int lote,
            @Value("${synapse.canal.webhook.maximo-de-tentativas:5}") int maximoDeTentativas) {
        this.entrada = entrada;
        this.tradutor = tradutor;
        this.registrar = registrar;
        this.leads = leads;
        this.canal = canal;
        this.armazenamento = armazenamento;
        this.json = json;
        this.relogio = relogio;
        this.lote = lote;
        this.maximoDeTentativas = maximoDeTentativas;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public int rodada() {
        List<WebhookEntrada.Pendente> pendentes = entrada.reservarPendentes(lote);
        for (WebhookEntrada.Pendente pendente : pendentes) {
            processar(pendente);
        }
        return pendentes.size();
    }

    private void processar(WebhookEntrada.Pendente pendente) {
        Instant agora = Instant.now(relogio);
        try {
            Optional<TradutorDeCanal.MensagemRecebidaDoCanal> traduzida =
                    tradutor.traduzir(pendente.payloadCru());

            if (traduzida.isEmpty()) {
                // Nao e mensagem de cliente (status, midia ainda nao suportada). Marcar
                // processado e o certo: reagendar faria a linha girar para sempre.
                entrada.marcarProcessado(pendente.idExterno(), agora);
                return;
            }

            TradutorDeCanal.MensagemRecebidaDoCanal mensagem = traduzida.get();
            UUID leadId =
                    leads.resolverPorTelefone(mensagem.telefoneRemetente(), mensagem.nomeExibicao());

            registrar.executar(mensagem.ehMidia()
                    ? mensagemRecebidaDeMidia(leadId, mensagem)
                    : new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                            leadId, null, null, mensagem.texto()));

            entrada.marcarProcessado(pendente.idExterno(), agora);

        } catch (RuntimeException e) {
            falhar(pendente, agora, e);
        }
    }

    /**
     * Troca o id de midia da Meta pelos bytes de verdade e persiste no storage proprio. A URL da
     * Meta expira em minutos; guardar so a referencia opaca do nosso storage e o que faz o historico
     * do cliente continuar acessivel depois disso (E11b, secao 3 do prompt).
     */
    private RegistrarMensagemRecebidaUseCase.MensagemRecebida mensagemRecebidaDeMidia(
            UUID leadId, TradutorDeCanal.MensagemRecebidaDoCanal mensagem) {
        CanalGateway.MidiaRecebida baixada = canal.baixarMidiaRecebida(mensagem.midiaIdExterno());
        TipoMensagem tipo = TipoMensagem.valueOf(mensagem.tipo());
        String referencia = armazenamento.salvar(baixada.conteudo(), mensagem.nomeArquivo(), baixada.mimetype());

        ObjectNode metadados = json.createObjectNode();
        if (mensagem.nomeArquivo() != null) {
            metadados.put("nome", mensagem.nomeArquivo());
        }
        metadados.put("mimetype", baixada.mimetype());
        metadados.put("tamanho", baixada.conteudo().length);
        if (mensagem.legenda() != null && !mensagem.legenda().isBlank()) {
            metadados.put("legenda", mensagem.legenda());
        }

        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                leadId, null, null, null, tipo, referencia, metadados.toString());
    }

    private void falhar(WebhookEntrada.Pendente pendente, Instant agora, RuntimeException e) {
        int tentativasFeitas = pendente.tentativas() + 1;

        if (tentativasFeitas >= maximoDeTentativas) {
            entrada.esgotar(pendente.idExterno(), agora, e.toString());
            log.error(
                    "{} evento {} nao pode ser processado apos {} tentativa(s) e NAO virou mensagem na "
                            + "conversa. O payload cru fica em webhook_entrada para reprocessamento "
                            + "manual. Ultimo erro: {}",
                    MARCADOR_ALARME,
                    pendente.idExterno(),
                    tentativasFeitas,
                    e.toString());
        } else {
            entrada.reagendar(pendente.idExterno(), e.toString());
            log.warn("Falha ao processar o evento {}; sera retentado.", pendente.idExterno(), e);
        }
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotados() {
        return entrada.quantidadeEsgotada();
    }
}
