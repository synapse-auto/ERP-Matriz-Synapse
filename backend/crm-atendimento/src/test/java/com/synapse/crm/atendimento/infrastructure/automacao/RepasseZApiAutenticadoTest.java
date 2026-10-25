package com.synapse.crm.atendimento.infrastructure.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;

class RepasseZApiAutenticadoTest {
    @Test void contratoProprioNaoFabricaHmacNemPropagaSegredoDoCallback() throws Exception {
        var cabecalhos=new AtomicReference<com.sun.net.httpserver.Headers>();
        var corpo=new AtomicReference<String>();var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        servidor.createContext("/eventos",e->{cabecalhos.set(e.getRequestHeaders());corpo.set(new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));e.sendResponseHeaders(204,-1);e.close();});servidor.start();
        try {
            var props=new RepasseWebhookAutomacaoProperties("http://127.0.0.1:"+servidor.getAddress().getPort()+"/eventos",Duration.ofSeconds(2),"token-sintetico-crm-n8n");
            var adapter=new RepasseWebhookAutomacaoHttpAdapter(RestClient.builder(),props,CircuitBreakerRegistry.ofDefaults());
            UUID evento=UUID.randomUUID();String payload="{\"type\":\"ReceivedCallback\"}";
            assertThat(adapter.repassar(new RepasseWebhookAutomacaoGateway.Repasse(evento,1,payload,null))).isEqualTo(RepasseWebhookAutomacaoGateway.ResultadoRepasse.ACEITO);
            assertThat(cabecalhos.get().getFirst("X-Hub-Signature-256")).isNull();
            assertThat(cabecalhos.get().getFirst("X-Synapse-Webhook-Token")).isEqualTo("token-sintetico-crm-n8n");
            assertThat(cabecalhos.get().getFirst("X-Synapse-Evento-Id")).isEqualTo(evento.toString());
            assertThat(corpo.get()).isEqualTo(payload);
            assertThat(props.toString()).doesNotContain(props.token(),props.url());
        } finally {servidor.stop(0);}
    }
}
