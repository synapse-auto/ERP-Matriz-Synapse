package com.synapse.crm.relatorios.interfaces.cancelamentos;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import com.synapse.crm.relatorios.application.cancelamentos.FonteCancelamentosException;
import com.synapse.crm.relatorios.application.cancelamentos.LeituraDeCancelamentos;
import com.synapse.crm.relatorios.application.cancelamentos.ListarCancelamentosUseCase;
import com.synapse.crm.relatorios.application.cancelamentos.ObterCancelamentoUseCase;
import com.synapse.crm.relatorios.application.cancelamentos.ObterConfiguracaoCancelamentosUseCase;
import com.synapse.crm.relatorios.application.cancelamentos.PoliticaConsultaCancelamentos;
import com.synapse.crm.relatorios.domain.cancelamentos.*;

@RestController
@RequestMapping("/api/v1/cancelamentos")
@Tag(name = "Cancelamentos", description = "Consulta de agendamentos cancelados; nao altera agendamentos.")
@SecurityRequirement(name = "bearerAuth")
class CancelamentosController {
    private final ListarCancelamentosUseCase lista;
    private final ObterCancelamentoUseCase detalhe;
    private final ObterConfiguracaoCancelamentosUseCase configuracao;
    CancelamentosController(ListarCancelamentosUseCase lista, ObterCancelamentoUseCase detalhe, ObterConfiguracaoCancelamentosUseCase configuracao) {
        this.lista=lista; this.detalhe=detalhe; this.configuracao=configuracao;
    }

    @GetMapping("/configuracao")
    @Operation(summary = "Limites e fuso da consulta autorizada")
    PoliticaConsultaCancelamentos.Configuracao configuracao() { return configuracao.executar(); }

    @GetMapping
    @Operation(summary = "Listar cancelamentos no escopo autorizado",
        description = "Filtros combinados, datas locais inclusivas. Ordem data da base DESC NULLS LAST, id DESC. "
            + "Pagina zero-based, sem total global. 200 vazio real; 503 INTEGRACAO_NAO_CONFIGURADA ou FONTE_INDISPONIVEL; 403 sem capacidade/flag; 400 filtro invalido.")
    LeituraDeCancelamentos.Pagina listar(
            @RequestParam(defaultValue = "REGISTRO") FiltroCancelamentos.BaseTemporal base,
            @RequestParam LocalDate inicio, @RequestParam LocalDate fim,
            @RequestParam(required = false) String profissional, @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "TODAS") FiltroCancelamentos.Descricao descricao,
            @RequestParam(required = false) String nome, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) Integer tamanho) {
        return lista.executar(base, inicio, fim, profissional, tipo, descricao, nome, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe minimo; inexistente ou fora do escopo retorna 404 indistinguivel")
    CancelamentoAgendamento detalhe(@PathVariable String id) { return detalhe.executar(id); }

    @ExceptionHandler(FiltroCancelamentosInvalidoException.class)
    ProblemDetail filtroInvalido() {
        var erro = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira o periodo e os limites da consulta.");
        erro.setProperty("codigo", "FILTRO_CANCELAMENTOS_INVALIDO");
        return erro;
    }

    @ExceptionHandler(FonteCancelamentosException.class)
    ProblemDetail fonte(FonteCancelamentosException falha) {
        var erro = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A fonte de cancelamentos nao esta disponivel.");
        erro.setProperty("codigo", falha.codigo().name());
        return erro;
    }
}
