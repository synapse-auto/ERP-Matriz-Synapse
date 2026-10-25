package com.synapse.crm.atendimento.infrastructure.outbox;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;

class RepasseWebhookOrigemTest {
    @ParameterizedTest @CsvSource({"z-api,uzapi-autotic", "uzapi-autotic,z-api", "meta-cloud,z-api", "z-api,NULL"})
    void trocaERollbackNaoEntregamEventoDeOutroProvedor(String ativo,String origem) {
        var outbox=mock(Outbox.class);var gateway=mock(RepasseWebhookAutomacaoGateway.class);UUID id=UUID.randomUUID();
        when(gateway.configurado()).thenReturn(true);
        when(outbox.reservarRepassesWebhookPendentes(anyInt(),any())).thenReturn(List.of(new Outbox.RepasseWebhookPendente(id,"{}",null,0,origem.equals("NULL")?null:origem)));
        var canal=new CanalProperties(ativo,null,null,null,null,null,null,null,null,null,null);
        new PublicadorDeRepasseWebhookOperacoes(outbox,gateway,new OutboxProperties(1,2,null,null,1,null),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),canal).rodada();
        verify(gateway,never()).repassar(any());
        verify(outbox).esgotar(eq(id),eq(Instant.EPOCH),contains("REPASSE_PROVEDOR_DIVERGENTE"));
    }
    @ParameterizedTest @CsvSource({"z-api,z-api", "meta-cloud,meta-cloud", "uzapi-autotic,uzapi-autotic", "meta-cloud,NULL", "uzapi-autotic,NULL"})
    void origemCorretaOuLegadoExistenteForaDaZApiMantemCompatibilidade(String ativo,String origem) {
        var outbox=mock(Outbox.class);var gateway=mock(RepasseWebhookAutomacaoGateway.class);UUID id=UUID.randomUUID();
        when(gateway.configurado()).thenReturn(true);when(gateway.repassar(any())).thenReturn(RepasseWebhookAutomacaoGateway.ResultadoRepasse.ACEITO);
        when(outbox.reservarRepassesWebhookPendentes(anyInt(),any())).thenReturn(List.of(new Outbox.RepasseWebhookPendente(id,"{}",null,0,origem.equals("NULL")?null:origem)));
        var canal=new CanalProperties(ativo,null,null,null,null,null,null,null,null,null,null);
        new PublicadorDeRepasseWebhookOperacoes(outbox,gateway,new OutboxProperties(1,2,null,null,1,null),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),canal).rodada();
        verify(gateway).repassar(any());verify(outbox).marcarPublicado(id,Instant.EPOCH);
    }
}
