package com.synapse.crm.atendimento.interfaces;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.finalizacaomassa.ConsultarFinalizacaoEmMassaUseCase;
import com.synapse.crm.atendimento.application.finalizacaomassa.ConsultarFinalizacaoEmMassaUseCase.Andamento;
import com.synapse.crm.atendimento.application.finalizacaomassa.PreverFinalizacaoEmMassaUseCase;
import com.synapse.crm.atendimento.application.finalizacaomassa.SolicitarFinalizacaoEmMassaUseCase;
import com.synapse.crm.atendimento.application.finalizacaomassa.SolicitarFinalizacaoEmMassaUseCase.Solicitacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDoItemDeFinalizacao;
import com.synapse.crm.atendimento.interfaces.FinalizacaoEmMassaDtos.ItemResposta;
import com.synapse.crm.atendimento.interfaces.FinalizacaoEmMassaDtos.OperacaoResposta;
import com.synapse.crm.atendimento.interfaces.FinalizacaoEmMassaDtos.PaginaDeItensResposta;
import com.synapse.crm.atendimento.interfaces.FinalizacaoEmMassaDtos.PedidoRequisicao;
import com.synapse.crm.atendimento.interfaces.FinalizacaoEmMassaDtos.PreviaResposta;

/**
 * Finalizacao em massa de atendimentos por atendentes e periodo (docs/55). A permissao
 * {@code atendimentos.finalizar_lote} e exigida em cada caso de uso, nao so aqui.
 */
@RestController
@RequestMapping("/api/v1/atendimentos/finalizacoes-em-massa")
@Tag(name = "Finalização em massa", description = "Finaliza, em segundo plano, os atendimentos de vários atendentes num período")
class FinalizacaoEmMassaController {

    private static final String BASE = "/api/v1/atendimentos/finalizacoes-em-massa/";

    private final PreverFinalizacaoEmMassaUseCase prever;
    private final SolicitarFinalizacaoEmMassaUseCase solicitar;
    private final ConsultarFinalizacaoEmMassaUseCase consultar;

    FinalizacaoEmMassaController(
            PreverFinalizacaoEmMassaUseCase prever,
            SolicitarFinalizacaoEmMassaUseCase solicitar,
            ConsultarFinalizacaoEmMassaUseCase consultar) {
        this.prever = prever;
        this.solicitar = solicitar;
        this.consultar = consultar;
    }

    @Operation(
            summary = "Prévia: quantos atendimentos os filtros alcançam",
            description = "Conta, por atendente, os atendimentos em atendimento cuja última atividade cai no período. "
                    + "Não altera nada. Recusa período invertido, vazio ou acima do máximo, e atendentes inexistentes ou fora do escopo.")
    @PostMapping("/previa")
    PreviaResposta previa(@Valid @RequestBody PedidoRequisicao pedido) {
        return PreviaResposta.de(prever.executar(pedido.paraDominio()));
    }

    @Operation(
            summary = "Cria a finalização em massa (assíncrona)",
            description = "Congela os atendimentos elegíveis sob a visibilidade de quem pede e responde 202 com a operação; "
                    + "o worker finaliza em lotes. Exige o cabeçalho Idempotency-Key: repetir a mesma chave com os mesmos "
                    + "filtros devolve a mesma operação (200); com filtros diferentes é 409. Só uma operação ativa por vez (409).")
    @PostMapping
    ResponseEntity<OperacaoResposta> criar(
            @RequestHeader(name = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody PedidoRequisicao pedido) {
        Solicitacao solicitacao = solicitar.executar(chave, pedido.paraDominio());
        Andamento andamento = consultar.status(solicitacao.operacao().id());
        OperacaoResposta corpo = OperacaoResposta.de(andamento.operacao(), andamento.itens(), solicitacao.repetida());
        URI local = URI.create(BASE + corpo.id());
        return solicitacao.repetida()
                ? ResponseEntity.ok().location(local).body(corpo)
                : ResponseEntity.accepted().location(local).body(corpo);
    }

    @Operation(
            summary = "Status e progresso da operação",
            description = "Contadores ao vivo (pendentes, finalizados, ignorados, falhas) e o percentual. "
                    + "Vale para consultar de novo depois de fechar a janela.")
    @GetMapping("/{id}")
    OperacaoResposta status(@PathVariable UUID id) {
        Andamento andamento = consultar.status(id);
        return OperacaoResposta.de(andamento.operacao(), andamento.itens(), false);
    }

    @Operation(
            summary = "Resultado detalhado, atendimento por atendimento",
            description = "Itens paginados, com filtro opcional por status (PENDENTE, FINALIZADO, IGNORADO, FALHA) "
                    + "e o motivo de cada ignorado ou falha.")
    @GetMapping("/{id}/itens")
    PaginaDeItensResposta itens(
            @PathVariable UUID id,
            @RequestParam(required = false) StatusDoItemDeFinalizacao status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        var resultado = consultar.itens(id, status, pagina, tamanho);
        List<ItemResposta> itens = resultado.itens().stream().map(ItemResposta::de).toList();
        return new PaginaDeItensResposta(itens, resultado.pagina(), resultado.tamanho());
    }

    @Operation(
            summary = "Operações recentes de quem consulta",
            description = "As últimas operações pedidas pelo usuário, da mais nova para a mais antiga, para reabrir um resultado.")
    @GetMapping
    List<OperacaoResposta> recentes() {
        return consultar.recentes().stream()
                .map(o -> OperacaoResposta.de(o, consultar.status(o.id()).itens(), false))
                .toList();
    }
}
