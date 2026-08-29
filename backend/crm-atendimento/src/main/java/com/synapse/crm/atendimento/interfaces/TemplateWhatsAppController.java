package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import com.synapse.crm.atendimento.application.template.CanalRecusouTemplateException;
import com.synapse.crm.atendimento.application.template.CriarTemplateWhatsAppUseCase;
import com.synapse.crm.atendimento.application.template.ListarTemplatesWhatsAppUseCase;
import com.synapse.crm.atendimento.application.template.PedidoDeTemplateInvalidoException;
import com.synapse.crm.atendimento.domain.canal.CanalIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

@RestController
@RequestMapping("/api/v1/whatsapp/templates")
@Tag(
        name = "Templates do WhatsApp",
        description = "Cadastro e listagem de templates de mensagem no provedor da instancia.")
@SecurityRequirement(name = "bearerAuth")
class TemplateWhatsAppController {

    private final ListarTemplatesWhatsAppUseCase listar;
    private final CriarTemplateWhatsAppUseCase criar;

    TemplateWhatsAppController(
            ListarTemplatesWhatsAppUseCase listar, CriarTemplateWhatsAppUseCase criar) {
        this.listar = listar;
        this.criar = criar;
    }

    @Operation(
            summary = "Listar templates do WhatsApp",
            description = "Consulta o provedor ativo. Fora do caminho de envio e recebimento de mensagem.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Templates do provedor."),
                @ApiResponse(responseCode = "503", description = "Provedor indisponível.")
            })
    @GetMapping
    List<Resposta> listar() {
        return listar.executar().stream().map(Resposta::de).toList();
    }

    @Operation(
            summary = "Criar template de texto no WhatsApp",
            description = "Submete um template de corpo textual à aprovação do provedor. A aprovação é assíncrona.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Template aceito pelo provedor."),
                @ApiResponse(responseCode = "400", description = "Pedido inválido."),
                @ApiResponse(responseCode = "422", description = "Provedor recusou o pedido."),
                @ApiResponse(responseCode = "503", description = "Provedor indisponível.")
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Resposta criar(@Valid @RequestBody Requisicao requisicao) {
        return Resposta.de(criar.executar(
                requisicao.nome(), requisicao.idioma(), requisicao.categoria(), requisicao.corpo()));
    }

    @ExceptionHandler(PedidoDeTemplateInvalidoException.class)
    ProblemDetail pedidoInvalido(PedidoDeTemplateInvalidoException erro) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Pedido de template invalido");
        return problema;
    }

    @ExceptionHandler(CanalRecusouTemplateException.class)
    ProblemDetail recusado(CanalRecusouTemplateException erro) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, erro.getMessage());
        problema.setTitle("Provedor recusou o template");
        return problema;
    }

    @ExceptionHandler(CanalIndisponivelException.class)
    ProblemDetail indisponivel(CanalIndisponivelException erro) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, erro.getMessage());
        problema.setTitle("Provedor de canal indisponivel");
        return problema;
    }

    @ExceptionHandler({RestClientException.class, CallNotPermittedException.class})
    ProblemDetail falhaDoProvedor(Exception erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "provedor de templates indisponivel: " + erro.getClass().getSimpleName());
        problema.setTitle("Provedor de canal indisponivel");
        return problema;
    }

    record Requisicao(
            @NotBlank String nome,
            String idioma,
            @NotNull TemplateDoCanal.Categoria categoria,
            @NotBlank String corpo) {}

    record Resposta(
            String nome,
            String idioma,
            TemplateDoCanal.Categoria categoria,
            TemplateDoCanal.Status status,
            String corpo,
            int quantidadeDeParametros) {

        static Resposta de(TemplateDoCanal template) {
            return new Resposta(
                    template.nome(),
                    template.idioma(),
                    template.categoria(),
                    template.status(),
                    template.corpo(),
                    template.quantidadeDeParametros());
        }
    }
}
