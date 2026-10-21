package com.synapse.crm.relatorios.interfaces.cancelamentos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
    @Operation(summary = "Limites e fuso da consulta autorizada",
            description = "Retorna limites operacionais e periodo inicial no fuso da instancia. Exige cancelamentos.ver e flag habilitada; leitura local sem fonte externa.")
    PoliticaConsultaCancelamentos.Configuracao configuracao() { return configuracao.executar(); }

    @GetMapping
    @Operation(summary = "Listar cancelamentos no escopo autorizado",
        description = "Filtros combinados, datas locais inclusivas. Intervalo exclui datas nulas da base; ordem data DESC, id DESC. "
            + "REGISTRO usa recebimento no CRM; CANCELAMENTO usa ocorrencia conhecida na origem; AGENDAMENTO usa o horario agendado. "
            + "Pagina zero-based, sem total global. 200 vazio real; 503 FONTE_INDISPONIVEL; 403 sem capacidade/flag; 400 filtro invalido.")
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
    @Operation(summary = "Detalhe minimo; inexistente ou fora do escopo retorna 404 indistinguivel",
            description = "Registro local autorizado pelas RLS de lead e atendimento. identificacao opcional contem somente CPF/telefone atuais do lead vinculado visivel pela Specification, nao snapshot. Esses dados nao aparecem na lista. Falha de banco retorna 503 sanitizado.")
    DetalheCancelamentoResposta detalhe(@PathVariable String id) {
        var resultado = detalhe.executar(id);
        var r = resultado.registro();
        return new DetalheCancelamentoResposta(r.id(), r.agendadoPara(), r.registradoEm(), r.contatoNome(),
                r.profissionalNome(), r.tipoAgendamento(), r.descricao(), r.recebidoEm(), r.canceladoEm(),
                r.leadId(), r.atendimentoId(), resultado.identificacao());
    }

    record DetalheCancelamentoResposta(String id, Instant agendadoPara, Instant registradoEm, String contatoNome,
            String profissionalNome, String tipoAgendamento, String descricao, Instant recebidoEm,
            Instant canceladoEm, UUID leadId, UUID atendimentoId,
            com.synapse.crm.relatorios.application.cancelamentos.IdentificacaoDoLeadCancelamento.DadosDeIdentificacaoDoCancelamento identificacao) {}

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
