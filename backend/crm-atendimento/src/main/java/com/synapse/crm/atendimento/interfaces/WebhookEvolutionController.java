package com.synapse.crm.atendimento.interfaces;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.AgendarRepasseWebhookAutomacaoUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.infrastructure.canal.EvolutionWebhookProtegidoFilter;
import com.synapse.crm.atendimento.infrastructure.canal.EvolutionWebhookTradutor;
import com.synapse.crm.atendimento.infrastructure.canal.RestricaoDeNumerosWhatsApp;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@RestController
@ConditionalOnProperty(prefix="synapse.canal.evolution",name="habilitado",havingValue="true")
public class WebhookEvolutionController {
    private final EvolutionWebhookTradutor tradutor;
    private final WebhookEntrada entrada;
    private final ValidarDestinoWebhookUseCase destino;
    private final AgendarRepasseWebhookAutomacaoUseCase repasse;
    private final Clock clock;
    private final RestricaoDeNumerosWhatsApp restricaoNumeros;
    public WebhookEvolutionController(EvolutionWebhookTradutor tradutor,WebhookEntrada entrada,ValidarDestinoWebhookUseCase destino,
            AgendarRepasseWebhookAutomacaoUseCase repasse,Clock clock,RestricaoDeNumerosWhatsApp restricaoNumeros) {this.tradutor=tradutor;this.entrada=entrada;this.destino=destino;this.repasse=repasse;this.clock=clock;this.restricaoNumeros=restricaoNumeros;}
    @PostMapping(EvolutionWebhookProtegidoFilter.ROTA)
    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public ResponseEntity<Void> receber(@RequestBody String payload,HttpServletRequest r) {
        if(!Boolean.TRUE.equals(r.getAttribute(EvolutionWebhookProtegidoFilter.AUTENTICADO))) return ResponseEntity.status(403).build();
        if(destino.executar(tradutor.destinos(payload)).resultado()!=ValidarDestinoWebhookUseCase.Resultado.ACEITO) return ResponseEntity.status(503).build();
        var ids=tradutor.idsExternos(payload);if(ids.isEmpty())return ResponseEntity.badRequest().build();
        Instant agora=clock.instant();
        ContextoDeServico.executarComo("webhook-evolution",()-> {
            if(entrada.registrarSeNovo(ids.getFirst(),tradutor.provedor(),payload,agora))
                tradutor.repasseSomenteNumerosPermitidos(payload,null,restricaoNumeros::permite)
                        .ifPresent(item->repasse.executar(item.payloadCru(),null,agora,tradutor.provedor()));
        });
        return ResponseEntity.ok().build();
    }
}
