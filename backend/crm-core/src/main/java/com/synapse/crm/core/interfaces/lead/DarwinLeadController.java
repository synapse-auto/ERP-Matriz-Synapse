package com.synapse.crm.core.interfaces.lead;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.synapse.crm.core.application.darwin.*;

@RestController
@RequestMapping("/api/v1/leads")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated() and @capacidades.permite('clientes.acessar') and @capacidades.permite('contatos.editar')")
class DarwinLeadController {
    private final ConsultarDarwinUseCase consultar;
    private final AplicarDarwinUseCase aplicar;
    private final DarwinGateway darwin;
    private final LerLeadParaDarwinUseCase acesso;
    private final ColetaDarwin coleta;
    DarwinLeadController(ConsultarDarwinUseCase consultar, AplicarDarwinUseCase aplicar, DarwinGateway darwin,
            LerLeadParaDarwinUseCase acesso, ColetaDarwin coleta) {
        this.consultar = consultar; this.aplicar = aplicar; this.darwin = darwin; this.acesso=acesso; this.coleta=coleta;
    }

    @GetMapping("/{id}/darwin/coleta")
    @Operation(summary="Ler estado da coleta cadastral Darwin", description="Exige acesso ao lead, clientes.acessar e contatos.editar. Retorna somente estado sanitizado do cache; não consulta o provedor nem devolve CPF ou configuração.")
    ColetaDarwin.Situacao estadoColeta(@PathVariable UUID id) {
        acesso.executar(id);
        return coleta.situacao(id);
    }

    @PostMapping("/{id}/darwin/coleta")
    @Operation(summary="Solicitar nova coleta cadastral Darwin", description="Agenda consulta assíncrona por CPF já persistido, sem exigir CPF no corpo. Exige visibilidade e contatos.editar. Respeita lease e intervalo mínimo; 202 não comprova conclusão. Não modifica dados na Darwin.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void tentarColeta(@PathVariable UUID id) {
        acesso.executar(id);
        darwin.exigirDisponivel();
        coleta.solicitar(id,true);
    }
    record Disponibilidade(boolean habilitada, DadosDarwin.EstadoIntegracao estado) {}
    record Confirmacao(@NotBlank @Size(max = 8192) String confirmacao, boolean nome, boolean cpf, boolean email) {}
    record Consulta(@Size(max = 14) String cpf) {}

    @GetMapping("/integracoes/darwin")
    @Operation(summary = "Disponibilidade da consulta Darwin", description = "Informa se a integração está desligada, pronta ou com configuração incompleta; não devolve configuração ou segredo nem chama o provedor. Desligada por padrão.")
    Disponibilidade disponibilidade() { return new Disponibilidade(darwin.habilitado(), darwin.estadoIntegracao()); }

    @PostMapping("/{id}/darwin/consultas")
    @Operation(summary = "Consultar cadastro no Darwin médico por CPF conhecido", description = "Exige contatos.editar e acesso ao lead por Specification/RLS. CPF opcional no corpo: utiliza o CPF do lead quando omitido. Não descobre CPF por telefone nem grava a ficha. Somente oferece dados se o CPF retornado for válido e igual ao consultado. Provedor indisponível retorna 502/503, nunca 401/403 de sessão CRM.")
    DadosDarwin.Previa consultar(@PathVariable UUID id, @Valid @RequestBody(required = false) Consulta dados) {
        return consultar.executar(id, dados == null ? null : dados.cpf());
    }

    @PostMapping("/{id}/darwin/aplicacoes")
    @Operation(summary = "Aplicar campos confirmados da Darwin", description = "Confirmação assinada e expirável vinculada a usuário, lead, cadastro/CPF, origem médica e valores anteriores. Somente nome, CPF e e-mail selecionados. Concorrência retorna 409 sem sobrescrever. Não consulta novamente o provedor; nenhuma escrita na Darwin.")
    DadosDarwin.Aplicacao aplicar(@PathVariable UUID id, @Valid @RequestBody Confirmacao dados) {
        return aplicar.executar(id, dados.confirmacao(), dados.nome(), dados.cpf(), dados.email());
    }
    @ExceptionHandler(FalhaDarwin.class)
    ProblemDetail falha(FalhaDarwin erro) {
        HttpStatus status = switch (erro.codigo()) {
            case DESABILITADA -> HttpStatus.FORBIDDEN;
            case CONCORRENCIA -> HttpStatus.CONFLICT;
            case CONSULTA_INVALIDA, CPF_INVALIDO -> HttpStatus.BAD_REQUEST;
            case AUTENTICACAO, RESPOSTA_INVALIDA, IDENTIDADE_DIVERGENTE -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        var problema = ProblemDetail.forStatusAndDetail(status, "Não foi possível concluir a operação Darwin.");
        problema.setProperty("codigo", "DARWIN_" + erro.codigo().name());
        return problema;
    }
}
