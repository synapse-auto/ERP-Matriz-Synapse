package com.synapse.crm.atendimento.interfaces;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.midia.ListarMidiasDoLeadUseCase;
import com.synapse.crm.atendimento.application.midia.MidiaDoLead;
import com.synapse.crm.atendimento.application.midia.MidiaDoLeadNaoEncontradaException;
import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

@RestController
@RequestMapping("/api/v1/leads/{leadId}/midias")
@Tag(name = "Mídias do lead", description = "Anexos dos atendimentos visíveis do lead.")
@SecurityRequirement(name = "bearerAuth")
class MidiasDoLeadController {
    private final ListarMidiasDoLeadUseCase listar;
    private final ArmazenamentoDeMidia armazenamento;
    private final MidiaProperties midiaPropriedades;

    MidiasDoLeadController(
            ListarMidiasDoLeadUseCase listar,
            ArmazenamentoDeMidia armazenamento,
            MidiaProperties midiaPropriedades) {
        this.listar = listar;
        this.armazenamento = armazenamento;
        this.midiaPropriedades = midiaPropriedades;
    }

    @Operation(
            summary = "Listar mídias do lead",
            description = "Lista metadados sem transferir bytes e sem URL de download. A URL assinada "
                    + "é emitida sob demanda em GET .../url, para não gastar TTL com o painel aberto.",
            responses = @ApiResponse(responseCode = "200", description = "Mídias paginadas."))
    @GetMapping
    List<Resposta> listar(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return listar.executar(leadId, pagina, tamanho).stream().map(Resposta::de).toList();
    }

    @Operation(
            summary = "Emitir URL assinada da mídia",
            description = "Valida lead, atendimento e mensagem e devolve uma URL de curta duração para o "
                    + "navegador. Quem não alcança o lead recebe 404.",
            responses = {
                @ApiResponse(responseCode = "200", description = "URL assinada."),
                @ApiResponse(responseCode = "404", description = "Mídia inexistente ou não visível.")
            })
    @GetMapping("/{mensagemId}/url")
    ResponseEntity<UrlAssinada> emitirUrl(
            @Parameter(required = true) @PathVariable UUID leadId, @PathVariable UUID mensagemId) {
        MidiaDoLead midia = listar.executar(leadId, mensagemId);
        String url = armazenamento.urlAssinada(
                midia.referenciaStorage(), midiaPropriedades.expiracaoLeitura());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new UrlAssinada(url));
    }

    @Operation(
            summary = "Baixar mídia do lead",
            description = "Valida lead, atendimento e mensagem no backend antes de devolver o conteúdo. "
                    + "Caminho JWT para quem busca com Authorization; a tela não usa este path em src/href.")
    @GetMapping("/{mensagemId}/download")
    ResponseEntity<byte[]> baixar(
            @Parameter(required = true) @PathVariable UUID leadId, @PathVariable UUID mensagemId) {
        MidiaDoLead midia = listar.executar(leadId, mensagemId);
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        MediaType tipo = MediaType.APPLICATION_OCTET_STREAM;
        try {
            tipo = MediaType.parseMediaType(midia.mimetype());
        } catch (Exception ignored) {
        }
        String nome = midia.nome() == null ? "arquivo" : midia.nome();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(tipo);
        headers.setContentLength(bytes.length);
        var disposition = "DOCUMENTO".equals(midia.tipo())
                ? ContentDisposition.attachment()
                : ContentDisposition.inline();
        headers.setContentDisposition(disposition.filename(nome, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @ExceptionHandler(MidiaDoLeadNaoEncontradaException.class)
    ProblemDetail aoNaoEncontrar(MidiaDoLeadNaoEncontradaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Nao encontrado");
        return problema;
    }

    record UrlAssinada(String url) {}

    record Resposta(
            UUID mensagemId,
            UUID atendimentoId,
            String tipo,
            String nome,
            String mimetype,
            long tamanho,
            String legenda,
            String enviadoEm,
            String origem) {
        static Resposta de(MidiaDoLead m) {
            return new Resposta(
                    m.mensagemId(),
                    m.atendimentoId(),
                    m.tipo(),
                    m.nome(),
                    m.mimetype(),
                    m.tamanho(),
                    m.legenda(),
                    m.enviadoEm().toString(),
                    m.origem());
        }
    }
}
