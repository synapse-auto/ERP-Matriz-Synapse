package com.synapse.crm.atendimento.interfaces;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.AgendarRepasseWebhookAutomacaoUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.infrastructure.canal.ZApiProperties;
import com.synapse.crm.atendimento.infrastructure.canal.ZApiWebhookProtegidoFilter;
import com.synapse.crm.atendimento.infrastructure.canal.ZApiWebhookTradutor;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Entrada dedicada: a posse do segredo e conferida antes de MVC/transacao/fila. */
@RestController
@ConditionalOnProperty(prefix = "synapse.canal.z-api.webhook", name = "habilitado", havingValue = "true")
public class WebhookZApiController {
    private final ZApiProperties api;
    private final ZApiWebhookTradutor tradutor;
    private final WebhookEntrada entrada;
    private final ValidarDestinoWebhookUseCase destino;
    private final AgendarRepasseWebhookAutomacaoUseCase repasse;
    private final ObjectMapper json;
    private final Clock relogio;

    public WebhookZApiController(ZApiProperties api, ZApiWebhookTradutor tradutor, WebhookEntrada entrada,
            ValidarDestinoWebhookUseCase destino, AgendarRepasseWebhookAutomacaoUseCase repasse,
            ObjectMapper json, Clock relogio) {
        this.api = api; this.tradutor = tradutor; this.entrada = entrada;
        this.destino = destino; this.repasse = repasse; this.json = json; this.relogio = relogio;
    }

    @PostMapping(ZApiWebhookProtegidoFilter.ROTA)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResponseEntity<?> receber(@RequestBody byte[] bytes, HttpServletRequest requisicao) {
        if (!Boolean.TRUE.equals(requisicao.getAttribute(ZApiWebhookProtegidoFilter.AUTENTICADO))) {
            return recusar(403);
        }
        String payload;
        try {
            var no = json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            if (no == null || !no.isObject() || !no.path("instanceId").isTextual()
                    || !api.instanciaId().equals(no.path("instanceId").asText())) {
                return recusar(403);
            }
            // Serializacao do JSON autenticado, sem query/header/segredo na fila.
            payload = json.writeValueAsString(no);
        } catch (Exception invalido) { return recusar(400); }
        var decisao = destino.executar(tradutor.destinos(payload));
        if (decisao.resultado() != ValidarDestinoWebhookUseCase.Resultado.ACEITO) {
            return recusar(503);
        }
        var ids = tradutor.idsExternos(payload);
        if (ids.isEmpty()) return recusar(400);
        Instant agora = Instant.now(relogio);
        ContextoDeServico.executarComo("webhook-z-api", () -> {
            if (entrada.registrarSeNovo(ids.getFirst(), tradutor.provedor(), payload, agora)) {
                tradutor.repasseSemGrupos(payload, null)
                        .ifPresent(item -> repasse.executar(item.payloadCru(), item.assinatura(), agora, tradutor.provedor()));
            }
        });
        return ResponseEntity.ok().build();
    }

    private static ResponseEntity<org.springframework.http.ProblemDetail> recusar(int status) {
        var problema = org.springframework.http.ProblemDetail.forStatus(status);
        problema.setTitle("Webhook recusado");
        return ResponseEntity.status(status).body(problema);
    }
}
