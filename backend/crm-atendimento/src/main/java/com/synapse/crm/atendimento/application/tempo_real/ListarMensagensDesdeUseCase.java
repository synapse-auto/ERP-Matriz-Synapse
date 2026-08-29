package com.synapse.crm.atendimento.application.tempo_real;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.application.reacao.ReacaoDeMensagemRepositorio;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Reconciliacao apos reconexao: o que o cliente perdeu enquanto o WebSocket estava fora.
 *
 * <p>O WebSocket entrega em tempo real, mas nao e duravel para quem estava desconectado — uma queda
 * de rede de 10s nao pode virar mensagem perdida. O cliente guarda o instante da ultima mensagem que
 * recebeu e, ao reconectar, busca aqui o que ficou para tras antes de retomar o socket.
 *
 * <p>{@code mensagem} nao tem politica RLS propria (so {@code lead}, {@code atendimento},
 * {@code lembrete} e {@code mensagem_programada} tem, desde a V12). Por isso a visibilidade e checada
 * aqui, no atendimento — a mesma porta, com a mesma politica, que autoriza a assinatura do WebSocket —
 * antes de tocar em mensagem nenhuma. Pular essa checagem e ler mensagem direto por
 * {@code atendimento_id} seria abrir a conversa de qualquer atendimento para quem soubesse o id.
 */
@Service
public class ListarMensagensDesdeUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final HistoricoDeMensagensRepositorio mensagens;
    private final ReacaoDeMensagemRepositorio reacoes;
    private final UsuarioContext usuarios;

    public ListarMensagensDesdeUseCase(
            AtendimentoRepositorio atendimentos,
            HistoricoDeMensagensRepositorio mensagens,
            ReacaoDeMensagemRepositorio reacoes,
            UsuarioContext usuarios) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.reacoes = reacoes;
        this.usuarios = usuarios;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<MensagemDoHistorico> executar(UUID atendimentoId, Instant desde) {
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        List<MensagemDoHistorico> encontradas = mensagens.desde(atendimentoId, desde);
        UUID usuarioId = usuarios.atual().id();
        List<ReacaoDeMensagemRepositorio.Chave> chaves = encontradas.stream()
                .map(item -> new ReacaoDeMensagemRepositorio.Chave(
                        item.mensagem().id(), item.mensagem().enviadoEm()))
                .toList();
        var resumos = reacoes.resumir(chaves, usuarioId);
        return encontradas.stream()
                .map(item -> {
                    List<ResumoDeReacao> daMensagem = resumos.getOrDefault(
                            new ReacaoDeMensagemRepositorio.Chave(
                                    item.mensagem().id(), item.mensagem().enviadoEm()),
                            List.of());
                    return item.comReacoes(daMensagem);
                })
                .toList();
    }
}
