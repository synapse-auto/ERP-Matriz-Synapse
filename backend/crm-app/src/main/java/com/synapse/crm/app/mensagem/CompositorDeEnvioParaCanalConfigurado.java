package com.synapse.crm.app.mensagem;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.CompositorDeEnvioParaCanal;
import com.synapse.crm.atendimento.application.ContaDeTesteSomenteLeituraException;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.referencia.CorrelacaoDeMensagemDoCanalRepositorio;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.atendimento.infrastructure.canal.CanalProperties;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;
import com.synapse.crm.equipe.application.usuario.AuditarBloqueioContaTesteUseCase;
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
    private final AuditarBloqueioContaTesteUseCase auditoria;
    private final CorrelacaoDeMensagemDoCanalRepositorio correlacoes;
    private final CanalProperties canal;

    public CompositorDeEnvioParaCanalConfigurado(
            OrigemDeMensagemRepositorio origens, ConfiguracaoDeInstanciaResources recursos,AuditarBloqueioContaTesteUseCase auditoria,
            CorrelacaoDeMensagemDoCanalRepositorio correlacoes, CanalProperties canal) {
        this.origens = origens;
        this.recursos = recursos;
        this.auditoria=auditoria;
        this.correlacoes=correlacoes;
        this.canal=canal;
    }

    /**
     * A consulta e curta e acontece sob a autoridade de servico do publicador. O resultado da
     * composicao e descartavel: a mensagem armazenada e o payload da outbox nunca sao alterados.
     */
    @Override
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public CanalGateway.Envio montar(Outbox.EnvioPendente pendente) {
        ConteudoDeEnvio conteudo = prefixarSeNecessario(pendente);
        IdentidadeDeMensagemDoCanal citacao = "z-api".equals(canal.provedor())
                && pendente.contextoWamid()!=null && !pendente.contextoWamid().isBlank()
                ? correlacoes.resolverCitacao(pendente.mensagemId(),pendente.enviadoEm(),pendente.atendimentoId(),canal.provedor(),canal.numeroPrincipal()).orElse(null)
                : null;
        return new CanalGateway.Envio(
                pendente.mensagemId(),
                pendente.telefoneDestino(),
                conteudo,
                pendente.credencialId(),
                citacao == null ? pendente.contextoWamid() : citacao.valor(), citacao);
    }

    private ConteudoDeEnvio prefixarSeNecessario(Outbox.EnvioPendente pendente) {
        Optional<OrigemDeMensagem> origem = origens.buscar(pendente.mensagemId(), pendente.enviadoEm());
        if (origem.map(OrigemDeMensagem::remetenteTeste).orElse(false)) {
            auditoria.executar(origem.orElseThrow().mensagem().remetente().id(),"OUTBOX_ENVIO");
            throw new ContaDeTesteSomenteLeituraException();
        }
        if (pendente.conteudo() instanceof ConteudoDeEnvio.MensagemTemplate) {
            return pendente.conteudo();
        }

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
