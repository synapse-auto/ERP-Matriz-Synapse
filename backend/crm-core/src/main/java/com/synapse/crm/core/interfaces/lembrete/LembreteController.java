package com.synapse.crm.core.interfaces.lembrete;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lembrete.AtualizarLembreteUseCase;
import com.synapse.crm.core.application.lembrete.CriarLembreteUseCase;
import com.synapse.crm.core.application.lembrete.FiltroLembretes;
import com.synapse.crm.core.application.lembrete.ListarLembretesUseCase;
import com.synapse.crm.core.application.lembrete.PaginaLembretes;
import com.synapse.crm.core.application.lembrete.RemoverLembreteUseCase;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;

@RestController
@RequestMapping("/api/v1/lembretes")
@Tag(name = "Lembretes", description = "Agenda paginada de lembretes vinculados aos leads visíveis.")
@SecurityRequirement(name = "bearerAuth")
class LembreteController {
    private final CriarLembreteUseCase criar;
    private final ListarLembretesUseCase listar;
    private final AtualizarLembreteUseCase atualizar;
    private final RemoverLembreteUseCase remover;
    private final int tamanhoPagina;

    LembreteController(CriarLembreteUseCase criar, ListarLembretesUseCase listar,
            AtualizarLembreteUseCase atualizar, RemoverLembreteUseCase remover,
            @Value("${synapse.suporte.tamanho-pagina}") int tamanhoPagina) {
        this.criar = criar;
        this.listar = listar;
        this.atualizar = atualizar;
        this.remover = remover;
        this.tamanhoPagina = tamanhoPagina;
    }

    @Operation(
            summary = "Listar lembretes",
            description = "Filtra lembretes por período e status. A página é baseada em zero e o tamanho vem da configuração da instância.",
            responses = @ApiResponse(responseCode = "200", description = "Página de lembretes."))
    @GetMapping
    PaginaResposta listar(
            @Parameter(description = "Início inclusivo do período em UTC.", example = "2026-08-05T08:00:00Z")
                    @RequestParam(required = false) Instant inicio,
            @Parameter(description = "Fim inclusivo do período em UTC.", example = "2026-08-05T18:30:00Z")
                    @RequestParam(required = false) Instant fim,
            @Parameter(description = "Status do lembrete.") @RequestParam(required = false) StatusLembrete status,
            @Parameter(description = "Lembretes de um lead só — usada pela seção do painel de atendimento (E17).")
                    @RequestParam(required = false) UUID leadId,
            @Parameter(description = "Índice da página, começando em zero.", example = "0")
                    @RequestParam(defaultValue = "0") int pagina) {
        return PaginaResposta.de(
                listar.executar(new FiltroLembretes(inicio, fim, status, leadId, pagina, tamanhoPagina)));
    }

    @Operation(
            summary = "Criar lembrete",
            description = "Cria um lembrete manual para um lead dentro do recorte de visibilidade do usuário.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Lembrete criado."),
                @ApiResponse(responseCode = "404", description = "Lead não encontrado ou não visível.")
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Resposta criar(@Valid @RequestBody Criacao requisicao) {
        return criar.executar(requisicao.leadId(), requisicao.texto(), requisicao.dataHora())
                .map(Resposta::de).orElseThrow(LembreteController::naoEncontrado);
    }

    @Operation(
            summary = "Atualizar lembrete",
            description = "Substitui texto, horário e status de um lembrete acessível ao usuário.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Lembrete atualizado."),
                @ApiResponse(responseCode = "404", description = "Lembrete não encontrado ou não visível.")
            })
    @PutMapping("/{id}")
    Resposta atualizar(
            @Parameter(description = "Identificador do lembrete.", required = true) @PathVariable UUID id,
            @Valid @RequestBody Alteracao requisicao) {
        return atualizar.executar(id, requisicao.texto(), requisicao.dataHora(), requisicao.status())
                .map(Resposta::de).orElseThrow(LembreteController::naoEncontrado);
    }

    @Operation(
            summary = "Remover lembrete",
            description = "Remove um lembrete acessível ao usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Lembrete removido."),
                @ApiResponse(responseCode = "404", description = "Lembrete não encontrado ou não visível.")
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(
            @Parameter(description = "Identificador do lembrete.", required = true) @PathVariable UUID id) {
        if (!remover.executar(id)) throw naoEncontrado();
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lembrete ou lead nao encontrado");
    }

    record Criacao(
            @Schema(description = "Lead visível ao qual o lembrete pertence.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID leadId,
            @Schema(description = "Texto do lembrete.", example = "Retornar com o orçamento", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String texto,
            @Schema(description = "Data e hora em UTC.", example = "2026-08-06T13:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull Instant dataHora) {}
    record Alteracao(
            @Schema(description = "Texto do lembrete.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String texto,
            @Schema(description = "Data e hora em UTC.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull Instant dataHora,
            @Schema(description = "Novo status.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull StatusLembrete status) {}
    record Resposta(UUID id, UUID leadId, String leadNome, UUID atendenteId, String atendenteNome,
            String texto, Instant dataHora, boolean origemAutomatica, StatusLembrete status) {
        static Resposta de(Lembrete l) {
            return new Resposta(l.id(), l.leadId(), l.leadNome(), l.atendenteId(), l.atendenteNome(),
                    l.texto(), l.dataHora(), l.origemAutomatica(), l.status());
        }
    }
    record PaginaResposta(List<Resposta> lembretes, int pagina, boolean temMais) {
        static PaginaResposta de(PaginaLembretes p) {
            return new PaginaResposta(p.lembretes().stream().map(Resposta::de).toList(), p.pagina(), p.temMais());
        }
    }
}
