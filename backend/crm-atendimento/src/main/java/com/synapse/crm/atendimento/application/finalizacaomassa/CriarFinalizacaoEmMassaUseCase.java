package com.synapse.crm.atendimento.application.finalizacaomassa;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ContagemPorAtendente;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoAtivaJaExisteException;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.FinalizacaoEmMassaException;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Cria a operacao e congela a lista de atendimentos, sob a visibilidade de quem pede. So roda para pedido
 * novo (a repeticao e resolvida antes, em {@link SolicitarFinalizacaoEmMassaUseCase}): por isso a auditoria
 * fica aqui e nao grava duas vezes o mesmo pedido.
 */
@Service
public class CriarFinalizacaoEmMassaUseCase {

    private final ValidadorDeFiltroDeFinalizacao validador;
    private final FinalizacaoEmMassaRepositorio repositorio;
    private final ParametrosDeFinalizacaoEmMassa parametros;
    private final UsuarioContext usuarioContext;

    public CriarFinalizacaoEmMassaUseCase(
            ValidadorDeFiltroDeFinalizacao validador,
            FinalizacaoEmMassaRepositorio repositorio,
            ParametrosDeFinalizacaoEmMassa parametros,
            UsuarioContext usuarioContext) {
        this.validador = validador;
        this.repositorio = repositorio;
        this.parametros = parametros;
        this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.finalizar_lote')")
    @Auditable(acao = "FINALIZAR_ATENDIMENTOS_EM_MASSA", entidadeTipo = "FINALIZACAO_EM_MASSA")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, propagation = Propagation.REQUIRES_NEW)
    public OperacaoDeFinalizacao executar(String chaveDeIdempotencia, PedidoDeFinalizacao pedido) {
        FiltroDeFinalizacao filtro = validador.montar(pedido);
        validador.exigirAtendentesAcessiveis(filtro);

        long total = repositorio.contarElegiveis(filtro).stream().mapToLong(ContagemPorAtendente::quantidade).sum();
        if (total == 0) {
            throw FinalizacaoEmMassaException.invalida(
                    "SEM_ATENDIMENTOS", "nenhum atendimento em atendimento nos filtros informados");
        }
        int limite = parametros.limitePorOperacao();
        if (total > limite) {
            throw FinalizacaoEmMassaException.invalida(
                    "LIMITE_EXCEDIDO",
                    "o filtro alcanca " + total + " atendimentos; o maximo por operacao e " + limite);
        }
        repositorio.operacaoAtiva().ifPresent(ativa -> {
            throw FinalizacaoEmMassaException.conflito(
                    "OPERACAO_EM_ANDAMENTO", "ja existe uma finalizacao em massa em andamento: " + ativa);
        });
        try {
            return repositorio.criarCongelandoElegiveis(
                    usuarioContext.atual().id(), chaveDeIdempotencia, filtro, limite);
        } catch (OperacaoAtivaJaExisteException corrida) {
            throw FinalizacaoEmMassaException.conflito(
                    "OPERACAO_EM_ANDAMENTO", "ja existe uma finalizacao em massa em andamento");
        }
    }
}
