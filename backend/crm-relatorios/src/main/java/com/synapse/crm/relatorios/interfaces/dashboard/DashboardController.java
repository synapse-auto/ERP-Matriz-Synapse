package com.synapse.crm.relatorios.interfaces.dashboard;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.relatorios.application.dashboard.ObterVisaoGeralDashboardUseCase;
import com.synapse.crm.relatorios.domain.dashboard.FiltroDashboardInvalidoException;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/** Endpoint único da aba Visão Geral; restrição de papel declarada no caso de uso. */
@RestController
@RequestMapping("/api/v1/dashboard/visao-geral")
@Tag(name = "Dashboard", description = "Indicadores gerenciais consolidados da operação.")
@SecurityRequirement(name = "bearerAuth")
class DashboardController {

    private final ObterVisaoGeralDashboardUseCase obter;

    DashboardController(ObterVisaoGeralDashboardUseCase obter) {
        this.obter = obter;
    }

    @Operation(
            summary = "Obter Visão Geral",
            description = "Agrega KPIs, funil, ranking e mensagens por hora para os meses selecionados, incluindo comparativos calculados no servidor.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Visão consolidada."),
                @ApiResponse(responseCode = "400", description = "Período inválido."),
                @ApiResponse(responseCode = "403", description = "Usuário não possui papel de gestão.")
            })
    @Parameters({
        @Parameter(name = "ano", description = "Ano dos meses selecionados.", example = "2026"),
        @Parameter(name = "meses", description = "Meses de 1 a 12, separados por vírgula; ausente seleciona o ano inteiro.", example = "7,8"),
        @Parameter(name = "origemInicio", description = "Início inclusivo opcional do coorte de leads.", example = "2026-01-01"),
        @Parameter(name = "origemFim", description = "Fim inclusivo opcional do coorte de leads.", example = "2026-06-30")
    })
    @GetMapping
    VisaoGeralDashboard obter(
            @RequestParam int ano,
            @RequestParam(required = false) List<Integer> meses,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate origemInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate origemFim) {
        return obter.executar(ano, meses, origemInicio, origemFim);
    }

    @ExceptionHandler(FiltroDashboardInvalidoException.class)
    ProblemDetail aoReceberFiltroInvalido(FiltroDashboardInvalidoException erro) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Periodo do dashboard invalido");
        return problema;
    }
}
