package com.synapse.crm.atendimento.interfaces;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import com.synapse.crm.atendimento.application.AgendarRepasseWebhookAutomacaoUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase.Decisao;
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
@Tag(name = "Webhook do canal", description = "Entrada pública autenticada pelo protocolo do provedor de canal.")
public class WebhookCanalController {

    private static final Logger log = LoggerFactory.getLogger(WebhookCanalController.class);

    private final TradutorDeCanal tradutor;
    private final WebhookEntrada entrada;
    private final AgendarRepasseWebhookAutomacaoUseCase agendarRepasse;
    private final ValidarDestinoWebhookUseCase validarDestino;
    private final Clock relogio;

    public WebhookCanalController(
            TradutorDeCanal tradutor,
            WebhookEntrada entrada,
            AgendarRepasseWebhookAutomacaoUseCase agendarRepasse,
            ValidarDestinoWebhookUseCase validarDestino,
            Clock relogio) {
        this.tradutor = tradutor;
        this.entrada = entrada;
        this.agendarRepasse = agendarRepasse;
        this.validarDestino = validarDestino;
        this.relogio = relogio;
    }

    /**
     * Verificacao de posse da rota, exigida pela Meta ao cadastrar o webhook.
     *
     * <p>Devolve o desafio so quando o token confere; caso contrario 403. Delegar ao tradutor mantem
     * a regra no adaptador, e o padrao e recusar.
     */
    @Operation(
            summary = "Verificar webhook",
            description = "Confirma a posse da rota durante o cadastro no provedor; o token de verificação nunca aparece na documentação.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Token válido; devolve o desafio."),
                @ApiResponse(responseCode = "403", description = "Token de verificação inválido.")
            })
    @GetMapping
    public ResponseEntity<String> verificar(
            @Parameter(description = "Modo enviado pelo provedor.", example = "subscribe")
                    @RequestParam(name = "hub.mode", required = false) String modo,
            @Parameter(description = "Token secreto de verificação configurado fora do código.")
                    @RequestParam(name = "hub.verify_token", required = false) String token,
            @Parameter(description = "Desafio que deve ser devolvido sem alteração.", example = "123456789")
                    @RequestParam(name = "hub.challenge", required = false) String desafio) {

        if (tradutor.tokenDeVerificacaoValido(token)) {
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
    @Operation(
            summary = "Receber evento do canal",
            description = "Valida a assinatura antes de persistir o payload bruto e responde rapidamente; tradução e processamento ocorrem depois.",
            security = @SecurityRequirement(name = "metaWebhookSignature"),
            responses = {
                @ApiResponse(responseCode = "200", description = "Evento aceito, ignorado ou reconhecido como reentrega."),
                @ApiResponse(responseCode = "403", description = "Assinatura HMAC ausente ou inválida.")
            })
    @PostMapping
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ResponseEntity<Void> receber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "Payload JSON bruto do provedor.",
                            required = true,
                            content = @Content(examples = @ExampleObject(value = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}")))
                    @RequestBody String payloadCru,
            @Parameter(description = "Assinatura no formato sha256=<hex>.", required = true)
                    @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {

        if (!tradutor.assinaturaValida(payloadCru, assinatura)) {
            // Nem uma linha gravada, nem um log com o corpo: a rota e publica.
            log.warn("Webhook com assinatura invalida recusado.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // O destino e validado depois do HMAC, mas antes das duas persistencias do payload cru.
        // Reescrever um POST misto quebraria a reconferencia da assinatura, por isso ele e
        // descartado por inteiro.
        Decisao destino = validarDestino.executar(tradutor.destinos(payloadCru));
        switch (destino.resultado()) {
            case ACEITO -> {
                // segue o fluxo normal abaixo
            }
            case OUTRO_CANAL -> {
                log.warn(
                        "Webhook de outro canal descartado (phone_number_ids={}, eventos={}).",
                        destino.identificadoresRecebidos(),
                        destino.quantidadeEventos());
                return ResponseEntity.ok().build();
            }
            case MISTO -> {
                log.error(
                        "Webhook com destinos mistos descartado (phone_number_ids={}, eventos={}).",
                        destino.identificadoresRecebidos(),
                        destino.quantidadeEventos());
                return ResponseEntity.ok().build();
            }
            case SEM_CONFIGURACAO -> {
                log.error(
                        "Webhook descartado: canal ativo sem phone_number_id configurado"
                                + " (canais_ativos={}, canais_sem_identificador={}, eventos={}).",
                        destino.quantidadeCanaisAtivos(),
                        destino.quantidadeCanaisSemIdentificador(),
                        destino.quantidadeEventos());
                return ResponseEntity.ok().build();
            }
        }

        Instant recebidoEm = Instant.now(relogio);
        agendarRepasse.executar(payloadCru, assinatura, recebidoEm);

        Optional<String> idExterno = tradutor.idExterno(payloadCru);
        if (idExterno.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        // O contexto de servico e obrigatorio: nao ha usuario numa chamada de provedor, e
        // sem contexto as politicas RLS negariam a escrita.
        boolean novo = ContextoDeServico.buscarComo(
                "webhook-canal",
                () -> entrada.registrarSeNovo(
                        idExterno.get(), tradutor.provedor(), payloadCru, recebidoEm));

        if (!novo) {
            log.debug("Reentrega do evento {} ignorada.", idExterno.get());
        }
        return ResponseEntity.ok().build();
    }
}
