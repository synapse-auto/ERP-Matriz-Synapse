package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ContagemDosItens;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ItemDeFinalizacao;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.FinalizacaoEmMassaException;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDoItemDeFinalizacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Status, resultado detalhado e historico recente. A visibilidade e a do banco (RLS): o solicitante ve as
 * proprias operacoes e quem enxerga tudo (gestao) ve todas; qualquer outro recebe "nao encontrada".
 */
@Service
public class ConsultarFinalizacaoEmMassaUseCase {

    static final int TAMANHO_MAXIMO_DA_PAGINA = 200;
    static final int OPERACOES_RECENTES = 10;
    private static final String PERMISSAO = "isAuthenticated() and @capacidades.permite('atendimentos.finalizar_lote')";

    private final FinalizacaoEmMassaRepositorio repositorio;
    private final UsuarioContext usuarioContext;

    public ConsultarFinalizacaoEmMassaUseCase(FinalizacaoEmMassaRepositorio repositorio, UsuarioContext usuarioContext) {
        this.repositorio = repositorio;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize(PERMISSAO)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Andamento status(UUID operacaoId) {
        OperacaoDeFinalizacao operacao = exigir(operacaoId);
        return new Andamento(operacao, repositorio.contarItens(operacaoId));
    }

    @PreAuthorize(PERMISSAO)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public PaginaDeItens itens(UUID operacaoId, StatusDoItemDeFinalizacao status, int pagina, int tamanho) {
        exigir(operacaoId);
        int limite = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_DA_PAGINA));
        int paginaValida = Math.max(0, pagina);
        return new PaginaDeItens(repositorio.itens(operacaoId, status, limite, paginaValida * limite), paginaValida, limite);
    }

    @PreAuthorize(PERMISSAO)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<OperacaoDeFinalizacao> recentes() {
        return repositorio.recentes(usuarioContext.atual().id(), OPERACOES_RECENTES);
    }

    @PreAuthorize(PERMISSAO)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Optional<OperacaoDeFinalizacao> porChave(UUID solicitanteId, String chave) {
        return repositorio.porChaveDeIdempotencia(solicitanteId, chave);
    }

    private OperacaoDeFinalizacao exigir(UUID operacaoId) {
        return repositorio.porId(operacaoId).orElseThrow(() -> new FinalizacaoEmMassaException(
                FinalizacaoEmMassaException.Categoria.NAO_ENCONTRADA,
                "OPERACAO_NAO_ENCONTRADA",
                "finalizacao em massa nao encontrada"));
    }

    public record Andamento(OperacaoDeFinalizacao operacao, ContagemDosItens itens) {}

    public record PaginaDeItens(List<ItemDeFinalizacao> itens, int pagina, int tamanho) {}
}
