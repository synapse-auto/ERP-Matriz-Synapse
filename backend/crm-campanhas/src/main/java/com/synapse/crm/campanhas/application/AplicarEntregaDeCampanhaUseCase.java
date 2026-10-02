package com.synapse.crm.campanhas.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.mensagem.MotivosDeFalhaDeEntrega;
import com.synapse.crm.campanhas.application.CampanhaRepositorio.Variacao;
import com.synapse.crm.campanhas.application.DestinatarioRepositorio.AcaoDeConferencia;
import com.synapse.crm.campanhas.application.DestinatarioRepositorio.Alvo;
import com.synapse.crm.campanhas.application.DestinatarioRepositorio.ErroDaMensagem;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.PoliticaDePausa;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Leva ao funil da campanha uma mudanca de status de entrega da mensagem (enviada, entregue, lida, falhou).
 * Idempotente e monotono: a Meta entrega fora de ordem e repete, e o status so avanca. Mensagem que nao e de
 * campanha (a imensa maioria) sai na primeira consulta, por indice.
 *
 * <p>Chamado pelo ouvinte de evento (depois do commit da mudanca da mensagem) e pela reconciliacao do ciclo,
 * que cobre um evento perdido.
 */
@Service
public class AplicarEntregaDeCampanhaUseCase {

    private final DestinatarioRepositorio destinatarios;
    private final CampanhaRepositorio campanhas;
    private final PausaAutomaticaDaCampanha pausa;
    private final ConfiguracaoDeCampanhas configuracao;

    public AplicarEntregaDeCampanhaUseCase(
            DestinatarioRepositorio destinatarios,
            CampanhaRepositorio campanhas,
            PausaAutomaticaDaCampanha pausa,
            ConfiguracaoDeCampanhas configuracao) {
        this.destinatarios = destinatarios;
        this.campanhas = campanhas;
        this.pausa = pausa;
        this.configuracao = configuracao;
    }

    /**
     * Traduz o status da mensagem ({@code status_entrega}); PENDENTE e desconhecido nao mudam nada.
     *
     * <p>{@code REQUIRES_NEW}: o ouvinte chama isto em {@code AFTER_COMMIT}, quando a transacao original ja
     * commitou mas ainda esta "ativa" para o Spring; um {@code REQUIRED} entraria nela e a escrita nunca seria
     * confirmada. O contexto de servico precisa estar aberto por quem chama, antes da transacao comecar.
     */
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, propagation = Propagation.REQUIRES_NEW)
    public void executar(UUID mensagemId, String statusDaMensagem, Instant ocorridoEm) {
        StatusDoDestinatario novo = traduzir(statusDaMensagem);
        if (novo != null) {
            executar(mensagemId, novo, ocorridoEm);
        }
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, propagation = Propagation.REQUIRES_NEW)
    public void executar(UUID mensagemId, StatusDoDestinatario novo, Instant ocorridoEm) {
        destinatarios.bloquearPorMensagem(mensagemId).ifPresent(alvo -> {
            if (novo == StatusDoDestinatario.FALHA) {
                aplicarFalha(alvo, ocorridoEm);
            } else {
                avancar(alvo, novo, ocorridoEm);
            }
        });
    }

    private void avancar(Alvo alvo, StatusDoDestinatario novo, Instant ocorridoEm) {
        List<StatusDoDestinatario> degraus = StatusDoDestinatario.niveisCruzados(alvo.status(), novo);
        if (degraus.isEmpty()) {
            return;
        }
        // Quem estava na conferencia e agora tem desfecho conhecido sai dela.
        destinatarios.aplicarStatus(
                alvo.id(), novo, null, null, ocorridoEm,
                alvo.emConferencia() ? AcaoDeConferencia.LIMPAR : AcaoDeConferencia.MANTER);
        Variacao variacao = alvo.emConferencia() ? Variacao.conferencia(-1) : Variacao.nenhuma();
        for (StatusDoDestinatario degrau : degraus) {
            variacao = variacao.mais(Variacao.cruzou(degrau));
        }
        campanhas.variarContadores(alvo.campanhaId(), variacao);
    }

    private void aplicarFalha(Alvo alvo, Instant ocorridoEm) {
        if (!alvo.status().aceitaFalha()) {
            return;
        }
        ErroDaMensagem erro = destinatarios.erroDaMensagem(alvo.mensagemId(), alvo.mensagemEnviadaEm()).orElse(null);
        Integer codigo = erro == null ? null : erro.codigo();
        boolean naoConfirmado = erro != null && MotivosDeFalhaDeEntrega.ENVIO_NAO_CONFIRMADO.equals(erro.titulo());
        MotivoDoDestinatario motivo =
                naoConfirmado ? MotivoDoDestinatario.ENVIO_NAO_CONFIRMADO : MotivoDoDestinatario.FALHA_NO_PROVEDOR;
        AcaoDeConferencia conferencia = naoConfirmado
                ? AcaoDeConferencia.SINALIZAR
                : (alvo.emConferencia() ? AcaoDeConferencia.LIMPAR : AcaoDeConferencia.MANTER);
        destinatarios.aplicarStatus(alvo.id(), StatusDoDestinatario.FALHA, motivo, codigo, ocorridoEm, conferencia);
        Variacao variacao = Variacao.falhou();
        if (naoConfirmado && !alvo.emConferencia()) {
            variacao = variacao.mais(Variacao.conferencia(1));
        } else if (!naoConfirmado && alvo.emConferencia()) {
            variacao = variacao.mais(Variacao.conferencia(-1));
        }
        campanhas.variarContadores(alvo.campanhaId(), variacao);
        pausarSeNecessario(alvo, codigo);
    }

    /** Erro de limite/qualidade da Meta pausa na hora; senao confere a taxa de falha dos envios recentes. */
    private void pausarSeNecessario(Alvo alvo, Integer codigo) {
        if (PoliticaDePausa.paraImediatamente(codigo)) {
            pausa.pausar(alvo.campanhaId(), PoliticaDePausa.motivoPorCodigo(codigo));
            return;
        }
        PoliticaDePausa politica = configuracao.atuais().politicaDePausa();
        PoliticaDePausa.Desfechos desfechos =
                destinatarios.desfechosRecentes(alvo.campanhaId(), politica.janelaDeEnvios());
        politica.avaliarTaxa(desfechos).ifPresent(motivo -> pausa.pausar(alvo.campanhaId(), motivo));
    }

    static StatusDoDestinatario traduzir(String statusDaMensagem) {
        if (statusDaMensagem == null) {
            return null;
        }
        return switch (statusDaMensagem) {
            case "ENVIADO" -> StatusDoDestinatario.ENVIADO;
            case "ENTREGUE" -> StatusDoDestinatario.ENTREGUE;
            case "LIDO" -> StatusDoDestinatario.LIDO;
            case "FALHOU" -> StatusDoDestinatario.FALHA;
            default -> null;
        };
    }
}
