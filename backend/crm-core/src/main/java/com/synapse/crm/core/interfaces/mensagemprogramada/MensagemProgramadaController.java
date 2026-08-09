package com.synapse.crm.core.interfaces.mensagemprogramada;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

import com.synapse.crm.core.application.mensagemprogramada.*;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

@RestController
@RequestMapping("/api/v1/mensagens-programadas")
@Tag(name = "Mensagens programadas", description = "Agendamento e cancelamento de mensagens futuras.")
@SecurityRequirement(name = "bearerAuth")
class MensagemProgramadaController {
    private final CriarMensagemProgramadaUseCase criar; private final ListarMensagensProgramadasUseCase listar;
    private final AtualizarMensagemProgramadaUseCase atualizar; private final CancelarMensagemProgramadaUseCase cancelar;
    private final int tamanho;
    MensagemProgramadaController(CriarMensagemProgramadaUseCase criar,ListarMensagensProgramadasUseCase listar,
            AtualizarMensagemProgramadaUseCase atualizar,CancelarMensagemProgramadaUseCase cancelar,
            @Value("${synapse.suporte.tamanho-pagina}")int tamanho){this.criar=criar;this.listar=listar;this.atualizar=atualizar;this.cancelar=cancelar;this.tamanho=tamanho;}
    @Operation(summary = "Listar mensagens programadas", description = "Filtra agendamentos por período e status, com página baseada em zero.", responses = @ApiResponse(responseCode = "200", description = "Página de mensagens programadas."))
    @GetMapping PaginaResposta listar(
            @Parameter(description = "Início inclusivo do período em UTC.") @RequestParam(required=false)Instant inicio,
            @Parameter(description = "Fim inclusivo do período em UTC.") @RequestParam(required=false)Instant fim,
            @Parameter(description = "Status do agendamento.") @RequestParam(required=false)StatusMensagemProgramada status,
            @Parameter(description = "Mensagens de um lead só — seção do painel de atendimento (E17).") @RequestParam(required=false)UUID leadId,
            @Parameter(description = "Índice da página, começando em zero.", example = "0") @RequestParam(defaultValue="0")int pagina){
        return PaginaResposta.de(listar.executar(new FiltroMensagensProgramadas(inicio,fim,status,leadId,pagina,tamanho)));}
    @Operation(summary = "Programar mensagem", description = "Agenda uma mensagem futura para um lead visível.", responses = {@ApiResponse(responseCode = "201", description = "Mensagem programada."), @ApiResponse(responseCode = "404", description = "Lead não encontrado ou não visível.")})
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Resposta criar(@Valid @RequestBody Requisicao r){return criar.executar(r.leadId(),r.conteudo(),r.dataEnvio()).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @Operation(summary = "Atualizar mensagem programada", description = "Altera conteúdo e data de um agendamento ainda editável.", responses = {@ApiResponse(responseCode = "200", description = "Agendamento atualizado."), @ApiResponse(responseCode = "404", description = "Agendamento não encontrado ou não visível."), @ApiResponse(responseCode = "409", description = "Agendamento não pode mais ser editado.")})
    @PutMapping("/{id}") Resposta atualizar(@Parameter(description = "Identificador do agendamento.", required = true) @PathVariable UUID id,@Valid @RequestBody Alteracao r){return atualizar.executar(id,r.conteudo(),r.dataEnvio()).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @Operation(summary = "Cancelar mensagem programada", description = "Cancela um agendamento que ainda permite alteração.", responses = {@ApiResponse(responseCode = "200", description = "Agendamento cancelado."), @ApiResponse(responseCode = "404", description = "Agendamento não encontrado ou não visível."), @ApiResponse(responseCode = "409", description = "Agendamento não pode mais ser cancelado.")})
    @PostMapping("/{id}/cancelar") Resposta cancelar(@Parameter(description = "Identificador do agendamento.", required = true) @PathVariable UUID id){return cancelar.executar(id).map(Resposta::de).orElseThrow(MensagemProgramadaController::naoEncontrada);}
    @ExceptionHandler(MensagemProgramadaNaoEditavelException.class) ProblemDetail conflito(MensagemProgramadaNaoEditavelException e){ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());p.setTitle("Mensagem programada nao editavel");return p;}
    private static ResponseStatusException naoEncontrada(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Mensagem programada ou lead nao encontrado");}
    record Requisicao(
            @Schema(description = "Lead visível que receberá a mensagem.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID leadId,
            @Schema(description = "Conteúdo textual.", example = "Posso retomar seu atendimento amanhã?", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String conteudo,
            @Schema(description = "Instante futuro em UTC.", example = "2026-08-06T13:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Future Instant dataEnvio){}
    record Alteracao(
            @Schema(description = "Novo conteúdo textual.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String conteudo,
            @Schema(description = "Novo instante futuro em UTC.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Future Instant dataEnvio){}
    record Resposta(UUID id,UUID leadId,String leadNome,UUID atendenteId,String atendenteNome,String conteudo,Instant dataEnvio,StatusMensagemProgramada status){static Resposta de(MensagemProgramada m){return new Resposta(m.id(),m.leadId(),m.leadNome(),m.atendenteId(),m.atendenteNome(),m.conteudo(),m.dataEnvio(),m.status());}}
    record PaginaResposta(List<Resposta> mensagens,int pagina,boolean temMais){static PaginaResposta de(PaginaMensagensProgramadas p){return new PaginaResposta(p.mensagens().stream().map(Resposta::de).toList(),p.pagina(),p.temMais());}}
}
