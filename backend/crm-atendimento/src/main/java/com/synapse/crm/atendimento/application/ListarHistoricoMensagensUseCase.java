package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.application.reacao.ReacaoDeMensagemRepositorio;
import com.synapse.crm.atendimento.application.reacao.ReacaoDoClienteRepositorio;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Le a conversa em paginas estaveis, das mensagens mais recentes para as mais antigas. */
@Service
public class ListarHistoricoMensagensUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final HistoricoDeMensagensRepositorio mensagens;
    private final ReacaoDeMensagemRepositorio reacoes;
    private final ReacaoDoClienteRepositorio reacoesDoCliente;
    private final UsuarioContext usuarios;

    public ListarHistoricoMensagensUseCase(
            AtendimentoRepositorio atendimentos,
            HistoricoDeMensagensRepositorio mensagens,
            ReacaoDeMensagemRepositorio reacoes,
            ReacaoDoClienteRepositorio reacoesDoCliente,
            UsuarioContext usuarios) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.reacoes = reacoes;
        this.reacoesDoCliente = reacoesDoCliente;
        this.usuarios = usuarios;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public MensagemDoHistorico executarPorId(UUID atendimentoId, UUID mensagemId) {
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        MensagemDoHistorico encontrada = mensagens
                .porId(atendimentoId, mensagemId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("mensagem", mensagemId));
        return anexarReacoes(List.of(encontrada)).getFirst();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Pagina executar(UUID atendimentoId, Cursor cursor, int tamanho) {
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));

        List<MensagemDoHistorico> encontradas = mensagens.anteriores(
                atendimentoId,
                cursor == null ? null : cursor.enviadoEm(),
                cursor == null ? null : cursor.id(),
                tamanho + 1);
        boolean temMais = encontradas.size() > tamanho;
        List<MensagemDoHistorico> pagina = new ArrayList<>(
                temMais ? encontradas.subList(0, tamanho) : encontradas);
        Cursor proximo = temMais
                ? new Cursor(
                        pagina.getLast().mensagem().enviadoEm(),
                        pagina.getLast().mensagem().id())
                : null;
        return new Pagina(anexarReacoes(pagina.reversed()), proximo);
    }

    private List<MensagemDoHistorico> anexarReacoes(List<MensagemDoHistorico> pagina) {
        UUID usuarioId = usuarios.atual().id();
        List<ReacaoDeMensagemRepositorio.Chave> chaves = pagina.stream()
                .map(item -> new ReacaoDeMensagemRepositorio.Chave(
                        item.mensagem().id(), item.mensagem().enviadoEm()))
                .toList();
        var resumos = reacoes.resumir(chaves, usuarioId);
        var doCliente = reacoesDoCliente.atuais(chaves);
        return pagina.stream()
                .map(item -> {
                    var chave = new ReacaoDeMensagemRepositorio.Chave(
                            item.mensagem().id(), item.mensagem().enviadoEm());
                    List<ResumoDeReacao> daMensagem = resumos.getOrDefault(chave, List.of());
                    return item.comReacoes(daMensagem).comReacaoDoCliente(doCliente.get(chave));
                })
                .toList();
    }

    public record Cursor(Instant enviadoEm, UUID id) {}

    public record Pagina(List<MensagemDoHistorico> mensagens, Cursor proximoCursor) {
        public Pagina {
            mensagens = List.copyOf(mensagens);
        }
    }
}
