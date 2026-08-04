package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Le a conversa em paginas estaveis, das mensagens mais recentes para as mais antigas. */
@Service
public class ListarHistoricoMensagensUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;

    public ListarHistoricoMensagensUseCase(
            AtendimentoRepositorio atendimentos, MensagemRepositorio mensagens) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Pagina executar(UUID atendimentoId, Cursor cursor, int tamanho) {
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));

        List<Mensagem> encontradas = mensagens.anteriores(
                atendimentoId,
                cursor == null ? null : cursor.enviadoEm(),
                cursor == null ? null : cursor.id(),
                tamanho + 1);
        boolean temMais = encontradas.size() > tamanho;
        List<Mensagem> pagina = new ArrayList<>(
                temMais ? encontradas.subList(0, tamanho) : encontradas);
        Cursor proximo = temMais
                ? new Cursor(pagina.getLast().enviadoEm(), pagina.getLast().id())
                : null;
        return new Pagina(pagina.reversed(), proximo);
    }

    public record Cursor(Instant enviadoEm, UUID id) {}

    public record Pagina(List<Mensagem> mensagens, Cursor proximoCursor) {
        public Pagina {
            mensagens = List.copyOf(mensagens);
        }
    }
}
