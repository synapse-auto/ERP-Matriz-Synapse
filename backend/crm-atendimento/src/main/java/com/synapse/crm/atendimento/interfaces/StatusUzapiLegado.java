package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

import com.synapse.crm.atendimento.application.canal.AplicarStatusLegadoUseCase;
import com.synapse.crm.atendimento.application.canal.StatusLegadoDeCanalRepositorio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.atendimento.infrastructure.canal.StatusUzapiLegadoFilter;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Adaptador de entrada da URL antiga. Nao grava mensagens, repasse ou efeitos de IA. */
@Component
public class StatusUzapiLegado {
    private final boolean ativo;
    private final TradutorDeCanal uzapi;
    private final ObjectMapper json;
    private final StatusLegadoDeCanalRepositorio repositorio;
    private final AplicarStatusLegadoUseCase aplicar;
    public StatusUzapiLegado(List<TradutorDeCanal> tradutores,CanalProperties canal,ObjectMapper json,
            StatusLegadoDeCanalRepositorio repositorio,AplicarStatusLegadoUseCase aplicar,
            @Value("${synapse.canal.transicao.status-uzapi-legado:false}") boolean habilitado) {
        ativo=habilitado && "z-api".equals(canal.provedor());
        uzapi=tradutores.stream().filter(t->"uzapi-autotic".equals(t.provedor())).findFirst().orElseThrow();
        this.json=json;this.repositorio=repositorio;this.aplicar=aplicar;
    }
    public boolean ativo(){return ativo;}
    public ResponseEntity<Void> receber(String corpo) {
        var atributos=RequestContextHolder.getRequestAttributes();
        if(!(atributos instanceof ServletRequestAttributes servlet)
                || !Boolean.TRUE.equals(servlet.getRequest().getAttribute(StatusUzapiLegadoFilter.AUTENTICADO)))
            return ResponseEntity.status(403).build();
        try {
            json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(corpo);
        } catch(Exception invalido){return ResponseEntity.badRequest().build();}
        return ContextoDeServico.buscarComo("webhook-status-uzapi-legado",()->{
            var destinos=uzapi.destinos(corpo);
            if(destinos.quantidadeEventos()==0 || destinos.identificadores().size()!=destinos.quantidadeEventos()
                    || !destinos.identificadores().stream().allMatch(repositorio::destinoPermitido))
                return ResponseEntity.status(403).build();
            aplicar.executar(uzapi.statusDeEntrega(corpo));
            return ResponseEntity.ok().build();
        });
    }
}
