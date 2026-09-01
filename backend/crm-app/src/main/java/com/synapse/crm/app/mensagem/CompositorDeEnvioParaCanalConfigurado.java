package com.synapse.crm.app.mensagem;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.CompositorDeEnvioParaCanal;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Compo a representacao de saida a partir do fato persistido, antes de entregar ao adaptador do
 * provedor. A autoria e resolvida pelo id da mensagem e o formato vem do catalogo da instancia;
 * o adaptador do canal permanece alheio a usuarios e regras de apresentacao.
 */
@Component
public class CompositorDeEnvioParaCanalConfigurado implements CompositorDeEnvioParaCanal {

    private static final String NOME = "{nome}";
    private static final String MENSAGEM = "{mensagem}";

    private final OrigemDeMensagemRepositorio origens;
    private final ConfiguracaoDeInstanciaResources recursos;

    public CompositorDeEnvioParaCanalConfigurado(
            OrigemDeMensagemRepositorio origens, ConfiguracaoDeInstanciaResources recursos) {
        this.origens = origens;
        this.recursos = recursos;
    }

    /**
     * A consulta e curta e acontece sob a autoridade de servico do publicador. O resultado da
     * composicao e descartavel: a mensagem armazenada e o payload da outbox nunca sao alterados.
     */
    @Override
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public CanalGateway.Envio montar(Outbox.EnvioPendente pendente) {
        ConteudoDeEnvio conteudo = prefixarSeNecessario(pendente);
        return new CanalGateway.Envio(
                pendente.mensagemId(),
                pendente.telefoneDestino(),
                conteudo,
                pendente.credencialId(),
                pendente.contextoWamid());
    }

    private ConteudoDeEnvio prefixarSeNecessario(Outbox.EnvioPendente pendente) {
        if (pendente.conteudo() instanceof ConteudoDeEnvio.MensagemTemplate) {
            return pendente.conteudo();
        }

        Optional<OrigemDeMensagem> origem = origens.buscar(pendente.mensagemId(), pendente.enviadoEm());
        String nome = origem.map(OrigemDeMensagem::remetenteNome).orElse(null);
        if (origem.isEmpty()
                || origem.get().mensagem().remetente().tipo() != RemetenteTipo.ATENDENTE
                || nome == null
                || nome.isBlank()) {
            return pendente.conteudo();
        }

        String formato = formatoConfigurado();
        if (formato.isBlank() || !formato.contains(NOME) || !formato.contains(MENSAGEM)) {
            return pendente.conteudo();
        }

        return switch (pendente.conteudo()) {
            case ConteudoDeEnvio.MensagemLivre livre ->
                    new ConteudoDeEnvio.MensagemLivre(aplicar(formato, nome, livre.texto()));
            case ConteudoDeEnvio.MensagemMidia midia ->
                    midia.legenda() == null || midia.legenda().isBlank()
                            ? midia
                            : new ConteudoDeEnvio.MensagemMidia(
                                    midia.tipo(),
                                    midia.referenciaStorage(),
                                    midia.metadados(),
                                    aplicar(formato, nome, midia.legenda()));
            case ConteudoDeEnvio.MensagemTemplate template -> template;
        };
    }

    private String formatoConfigurado() {
        JsonNode no = recursos.textos()
                .path("atendimentos")
                .path("mensagem")
                .path("prefixoAtendente");
        return no.isTextual() ? no.asText() : "";
    }

    private static String aplicar(String formato, String nome, String mensagem) {
        return formato.replace(NOME, nome).replace(MENSAGEM, mensagem);
    }
}
