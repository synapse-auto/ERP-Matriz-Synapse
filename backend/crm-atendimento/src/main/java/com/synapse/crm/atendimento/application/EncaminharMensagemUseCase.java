package com.synapse.crm.atendimento.application;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.EncaminhamentoIncompativelException;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.ReferenciaDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.mensagem.TipoReferencia;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Encaminha uma mensagem visivel para outra conversa visivel.
 *
 * <p>O WhatsApp nao tem tipo nativo de encaminhamento: o produto cria uma mensagem nova no destino,
 * com citacao da origem. A origem nao e movida, editada nem apagada. O corpo nao vem do cliente —
 * sai da linha persistida, para o frontend nao poder trocar o conteudo.
 */
@Service
public class EncaminharMensagemUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final OrigemDeMensagemRepositorio origens;
    private final EnviarMensagemUseCase enviar;

    public EncaminharMensagemUseCase(
            AtendimentoRepositorio atendimentos,
            OrigemDeMensagemRepositorio origens,
            EnviarMensagemUseCase enviar) {
        this.atendimentos = atendimentos;
        this.origens = origens;
        this.enviar = enviar;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public EnviarMensagemUseCase.Resultado executar(
            UUID origemAtendimentoId,
            UUID origemMensagemId,
            java.time.Instant origemEnviadaEm,
            UUID destinoAtendimentoId) {
        Atendimento origemAtendimento = atendimentos
                .porId(origemAtendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento", origemAtendimentoId));
        OrigemDeMensagem origem = origens
                .buscar(origemMensagemId, origemEnviadaEm)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "mensagem", origemMensagemId));
        if (!origemAtendimento.id().equals(origem.mensagem().atendimentoId())
                && !origemAtendimento.leadId().equals(origem.leadId())) {
            throw new RecursoDeAtendimentoIndisponivelException("mensagem", origemMensagemId);
        }
        Atendimento destino = atendimentos
                .porId(destinoAtendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento", destinoAtendimentoId));
        if (destino.id().equals(origemAtendimento.id())
                || destino.leadId().equals(origemAtendimento.leadId())) {
            throw new EncaminhamentoIncompativelException(
                    "o destino nao pode ser a mesma conversa da origem");
        }

        Mensagem mensagem = origem.mensagem();
        ConteudoDeEnvio conteudo = conteudoDe(mensagem);
        ReferenciaDeMensagem referencia = new ReferenciaDeMensagem(
                TipoReferencia.ENCAMINHAMENTO,
                mensagem.id(),
                mensagem.enviadoEm(),
                mensagem.atendimentoId(),
                CitacaoDeMensagem.autorDe(
                        mensagem.remetente().tipo(), origem.leadNome(), origem.remetenteNome()),
                mensagem.tipo().name(),
                CitacaoDeMensagem.previaDe(
                        mensagem.tipo(), mensagem.conteudo(), mensagem.midiaMetadados()),
                null);
        return enviar.executarComReferencia(destino.leadId(), conteudo, referencia);
    }

    private static ConteudoDeEnvio conteudoDe(Mensagem mensagem) {
        TipoMensagem tipo = mensagem.tipo();
        if (tipo == TipoMensagem.TEXTO) {
            if (mensagem.conteudo() == null || mensagem.conteudo().isBlank()) {
                throw new EncaminhamentoIncompativelException("a origem nao tem texto para encaminhar");
            }
            return new ConteudoDeEnvio.MensagemLivre(mensagem.conteudo());
        }
        if (tipo.exigeMidia()) {
            String legenda = CitacaoDeMensagem.previaDe(tipo, mensagem.conteudo(), mensagem.midiaMetadados());
            return new ConteudoDeEnvio.MensagemMidia(
                    tipo, mensagem.midiaUrl(), mensagem.midiaMetadados(), legenda.isBlank() ? null : legenda);
        }
        throw new EncaminhamentoIncompativelException(
                "mensagens interativas nao podem ser encaminhadas");
    }
}
