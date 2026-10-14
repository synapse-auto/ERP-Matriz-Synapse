package com.synapse.crm.atendimento.interfaces;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.synapse.crm.atendimento.application.painel.operacional.FiltroOperacional;
import com.synapse.crm.atendimento.application.painel.operacional.ListarFilaOperacionalUseCase;
import com.synapse.crm.atendimento.application.painel.operacional.ObterPainelOperacionalUseCase;
import com.synapse.crm.atendimento.application.painel.operacional.PainelOperacional;

@RestController
@RequestMapping("/api/v1/atendimentos/painel-operacional")
@SecurityRequirement(name = "bearerAuth")
class PainelOperacionalController {
    private final ObterPainelOperacionalUseCase painel;
    private final ListarFilaOperacionalUseCase fila;

    PainelOperacionalController(ObterPainelOperacionalUseCase painel, ListarFilaOperacionalUseCase fila) {
        this.painel = painel;
        this.fila = fila;
    }

    @GetMapping
    @Operation(summary = "Snapshot operacional autorizado", description = "Exige atendimentos.painel_operacional. Capacidade não amplia Specification/RLS. Contagens por lead; finalizados limitados ao dia da instância. Não devolve conteúdo de mensagens.")
    PainelOperacional obter(@RequestParam(required = false) UUID atendenteId,
            @RequestParam(defaultValue = "false") boolean entrada,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID etapaId,
            @RequestParam(defaultValue = "TODOS") FiltroOperacional.Sinal sinal) {
        return painel.executar(new FiltroOperacional(null, atendenteId, entrada, busca, etapaId, sinal));
    }

    @GetMapping("/fila")
    @Operation(summary = "Fila operacional paginada", description = "Mesmos critérios do snapshot. Cursor por atividade e UUID. entrada e atendenteId são incompatíveis. Busca/etapa aplicadas no servidor antes da paginação. Não altera posse ou leitura.")
    PainelOperacional.Pagina listar(@RequestParam(defaultValue = "EM_ABERTO") FiltroOperacional.Fila recorte,
            @RequestParam(required = false) UUID atendenteId,
            @RequestParam(defaultValue = "false") boolean entrada,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID etapaId,
            @RequestParam(defaultValue = "TODOS") FiltroOperacional.Sinal sinal,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(required = false) String cursor) {
        return fila.executar(new FiltroOperacional(recorte, atendenteId, entrada, busca, etapaId, sinal), tamanho, cursor);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail filtroInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Filtro ou cursor operacional inválido.");
    }
}
