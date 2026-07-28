package com.synapse.crm.atendimento.interfaces;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A porta de entrada do canal. Rota publica — e tratada como tal.
 *
 * <p>Faz tres coisas, nessa ordem, e nada mais: confere a assinatura, grava o payload cru, responde
 * 200. A traducao e o registro da mensagem acontecem depois, num job.
 *
 * <p>A ordem importa. Assinatura <b>antes</b> de qualquer processamento e antes de gravar: sem isso,
 * qualquer um na internet injeta mensagem falsa na conversa de um cliente com um {@code curl}, ou
 * enche a tabela de lixo.
 *
 * <p>Responder rapido tambem nao e conforto: o provedor tem timeout curto e reentrega o que demora.
 * Um webhook lento nao fica so lento — ele multiplica as reentregas, que e o oposto do que se quer
 * quando o sistema ja esta sob carga.
 */
@RestController
@RequestMapping("/webhook/canal")
public class WebhookCanalController {

    private static final Logger log = LoggerFactory.getLogger(WebhookCanalController.class);

    private final TradutorDeCanal tradutor;
    private final WebhookEntrada entrada;
    private final Clock relogio;

    public WebhookCanalController(TradutorDeCanal tradutor, WebhookEntrada entrada, Clock relogio) {
        this.tradutor = tradutor;
        this.entrada = entrada;
        this.relogio = relogio;
    }

    /**
     * Verificacao de posse da rota, exigida pela Meta ao cadastrar o webhook.
     *
     * <p>Devolve o desafio so quando o token confere; caso contrario 403. Delegar ao tradutor mantem
     * a regra no adaptador, e o padrao e recusar.
     */
    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {

        if (tradutor.assinaturaValida("", token)) {
            return ResponseEntity.ok(desafio);
        }
        log.warn("Verificacao de webhook recusada (modo={}).", modo);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Recebe o evento.
     *
     * <p>Sempre 200 quando a assinatura confere, inclusive para payload que nao e mensagem
     * (confirmacao de entrega, status, heartbeat) e para reentrega ja conhecida. Responder erro
     * nesses casos faria o provedor reentregar para sempre um evento que nunca vamos querer.
     */
    @PostMapping
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResponseEntity<Void> receber(
            @RequestBody String payloadCru,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {

        if (!tradutor.assinaturaValida(payloadCru, assinatura)) {
            // Nem uma linha gravada, nem um log com o corpo: a rota e publica.
            log.warn("Webhook com assinatura invalida recusado.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<String> idExterno = tradutor.idExterno(payloadCru);
        if (idExterno.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        // O contexto de servico e obrigatorio: nao ha usuario numa chamada de provedor, e
        // sem contexto as politicas RLS negariam a escrita.
        boolean novo = ContextoDeServico.buscarComo(
                "webhook-canal",
                () -> entrada.registrarSeNovo(
                        idExterno.get(), tradutor.provedor(), payloadCru, Instant.now(relogio)));

        if (!novo) {
            log.debug("Reentrega do evento {} ignorada.", idExterno.get());
        }
        return ResponseEntity.ok().build();
    }
}
