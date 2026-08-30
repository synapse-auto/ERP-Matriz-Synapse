package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.etapa.EtapaNaoEncontradaException;
import com.synapse.crm.core.application.lead.AtualizarLeadUseCase;
import com.synapse.crm.core.application.lead.BuscarLeadParaEntradaUseCase;
import com.synapse.crm.core.application.lead.DadosDeAtualizacaoLead;
import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.LeadParaEntrada;
import com.synapse.crm.core.application.lead.ListarLeadsUseCase;
import com.synapse.crm.core.application.lead.ObterLeadUseCase;
import com.synapse.crm.core.domain.campocustomizado.DadosCustomizadosInvalidosException;
import com.synapse.crm.core.domain.lead.CodigoInvalidoException;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;

/**
 * Leitura e edicao de leads.
 *
 * <p>O recorte por papel acontece no repositorio, nunca aqui e nunca no frontend. "Nao e seu" e
 * "nao existe" respondem igual — 404 — de proposito.
 */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Consulta e edição de leads sob as regras de visibilidade comercial.")
@SecurityRequirement(name = "bearerAuth")
class LeadController {

    private final ListarLeadsUseCase listar;
    private final ObterLeadUseCase obter;
    private final AtualizarLeadUseCase atualizar;
    private final BuscarLeadParaEntradaUseCase buscarParaEntrada;

    LeadController(ListarLeadsUseCase listar, ObterLeadUseCase obter, AtualizarLeadUseCase atualizar, BuscarLeadParaEntradaUseCase buscarParaEntrada) {
        this.listar = listar;
        this.obter = obter;
        this.atualizar = atualizar;
        this.buscarParaEntrada = buscarParaEntrada;
    }

    @Operation(summary="Buscar contato para pedir entrada", description="Retorna somente nome, empresa e responsável de contatos de colegas; não expõe ficha, histórico ou etapa.")
    @GetMapping("/busca-entrada")
    List<LeadParaEntrada> buscarParaEntrada(@RequestParam String termo) { return buscarParaEntrada.executar(termo); }

    /** Listagem: devolve o resumo, que nao carrega notas nem resumo de IA. */
    @Operation(
            summary = "Listar leads",
            description = "Retorna somente o resumo dos leads visíveis; notas, resumo de IA e dados customizados não saem na listagem.",
            responses = @ApiResponse(responseCode = "200", description = "Leads visíveis."))
    @GetMapping
    List<LeadDaLista> listar(
            @Parameter(description = "Busca textual por dados básicos do lead.", example = "Maria")
                    @RequestParam(required = false) String busca,
            @Parameter(description = "Status básico do lead.")
                    @RequestParam(required = false) StatusBasicoLead status) {
        // Sem tags: esta listagem alimenta seletores simples (lembrete, mensagem programada),
        // que nunca as exibem — buscar em lote so para descartar seria trabalho de banco atoa.
        return listar.executar(new FiltroLead(busca, status)).stream()
                .map(lead -> LeadDaLista.de(lead, List.of()))
                .toList();
    }

    @Operation(
            summary = "Obter ficha do lead",
            description = "Retorna a ficha completa quando o lead está dentro do recorte de visibilidade do usuário.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha completa do lead."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente ou não visível.")
            })
    @GetMapping("/{id}")
    FichaDoLead porId(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id) {
        return obter.executar(id).map(FichaDoLead::de).orElseThrow(LeadController::naoEncontrado);
    }

    @Operation(
            summary = "Atualizar ficha do lead",
            description = "Atualiza apenas os campos enviados. Não permite trocar o responsável nem escrever o resumo de IA.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Ficha atualizada."),
                @ApiResponse(responseCode = "400", description = "Dados básicos ou customizados inválidos."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente ou não visível.")
            })
    @PutMapping("/{id}")
    FichaDoLead atualizar(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id,
            @Valid @RequestBody AtualizacaoRequisicao requisicao) {
        return atualizar
                .executar(id, requisicao.paraDados())
                .map(FichaDoLead::de)
                .orElseThrow(LeadController::naoEncontrado);
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead nao encontrado");
    }

    /** Chave de campo customizado nao cadastrada, tipo incompativel, ou obrigatorio ausente. */
    @ExceptionHandler(DadosCustomizadosInvalidosException.class)
    ProblemDetail aoReceberDadosCustomizadosInvalidos(DadosCustomizadosInvalidosException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Dados customizados invalidos");
        return problema;
    }

    @ExceptionHandler(EtapaNaoEncontradaException.class)
    ProblemDetail aoReceberEtapaInexistente(EtapaNaoEncontradaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Etapa invalida");
        return problema;
    }

    @ExceptionHandler(TelefoneInvalidoException.class)
    ProblemDetail aoReceberTelefoneInvalido(TelefoneInvalidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Telefone invalido");
        return problema;
    }

    @ExceptionHandler(CodigoInvalidoException.class)
    ProblemDetail aoReceberCodigoInvalido(CodigoInvalidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Codigo invalido");
        return problema;
    }

    /** Ficha completa, so na consulta por id. */
    record FichaDoLead(
            UUID id,
            String nome,
            String fotoUrl,
            String telefone,
            String email,
            String cpf,
            String empresa,
            String codigo,
            String localizacao,
            UUID canalOrigemId,
            StatusBasicoLead status,
            UUID etapaAtendimentoId,
            UUID atendenteResponsavelId,
            String notas,
            String resumoIa,
            int numAtendimentos,
            int numMensagens,
            Instant criadoEm,
            Map<String, Object> dadosCustomizados) {

        static FichaDoLead de(Lead lead) {
            return new FichaDoLead(
                    lead.id(), lead.nome(), lead.fotoUrl(), lead.telefone(), lead.email(), lead.cpf(),
                    lead.empresa(), lead.codigo(), lead.localizacao(), lead.canalOrigemId(), lead.statusBasico(),
                    lead.etapaAtendimentoId(), lead.atendenteResponsavelId(), lead.notas(),
                    lead.resumoIa(), lead.numAtendimentos(), lead.numMensagens(), lead.criadoEm(),
                    lead.dadosCustomizados());
        }
    }

    /**
     * Corpo da edicao de ficha. Campo ausente significa "nao mexa".
     *
     * <p>Nao aceita {@code atendenteResponsavelId}: trocar o dono e transferencia de lead, nao
     * edicao de ficha, e teria virado uma forma de largar um lead dificil ou puxar o de um colega.
     * Nao aceita {@code resumoIa} porque quem escreve e a Automacao.
     */
    record AtualizacaoRequisicao(
            @Schema(description = "Nome; ausente preserva o valor atual.", example = "Maria Silva", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 150) String nome,
            @Schema(description = "URL da foto; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    String fotoUrl,
            @Schema(description = "Telefone; ausente preserva o valor atual.", example = "+5561999999999", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 30) String telefone,
            @Schema(description = "E-mail; ausente preserva o valor atual.", example = "maria@example.invalid", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 200) String email,
            @Schema(description = "CPF; ausente preserva o valor atual.", example = "00000000000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 14) String cpf,
            @Schema(description = "Empresa; ausente preserva o valor atual.", example = "Empresa Exemplo", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 150) String empresa,
            @Schema(description = "Código numérico interno; ausente preserva o valor atual. Vazio limpa o campo.", example = "00421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 20) String codigo,
            @Schema(description = "Localização; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 200) String localizacao,
            @Schema(description = "Canal de origem; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    UUID canalOrigemId,
            @Schema(description = "Status básico; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    StatusBasicoLead status,
            @Schema(description = "Etapa do funil; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    UUID etapaAtendimentoId,
            @Schema(description = "Notas internas; ausente preserva o valor atual.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    String notas,
            @Schema(description = "Mapa validado contra o catálogo de campos customizados.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    Map<String, Object> dadosCustomizados) {

        DadosDeAtualizacaoLead paraDados() {
            return new DadosDeAtualizacaoLead(
                    nome, fotoUrl, telefone, email, cpf, empresa, codigo, localizacao, canalOrigemId, status,
                    etapaAtendimentoId, notas, dadosCustomizados);
        }
    }
}
