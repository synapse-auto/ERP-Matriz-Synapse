package com.synapse.crm.atendimento.interfaces;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.midia.ListarMidiasDoLeadUseCase;
import com.synapse.crm.atendimento.application.midia.MidiaDoLead;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

@RestController
@RequestMapping("/api/v1/leads/{leadId}/midias")
@Tag(name = "Mídias do lead", description = "Anexos dos atendimentos visíveis do lead.")
@SecurityRequirement(name = "bearerAuth")
class MidiasDoLeadController {
    private final ListarMidiasDoLeadUseCase listar;
    private final ArmazenamentoDeMidia armazenamento;

    MidiasDoLeadController(ListarMidiasDoLeadUseCase listar, ArmazenamentoDeMidia armazenamento) {
        this.listar = listar;
        this.armazenamento = armazenamento;
    }

    @Operation(summary = "Listar mídias do lead", description = "Lista metadados sem transferir bytes; somente mensagens de atendimentos alcançáveis pelo usuário.", responses = @ApiResponse(responseCode = "200", description = "Mídias paginadas."))
    @GetMapping
    List<Resposta> listar(@PathVariable UUID leadId, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return listar.executar(leadId, pagina, tamanho).stream().map(m -> Resposta.de(leadId, m)).toList();
    }

    @Operation(summary = "Baixar mídia do lead", description = "Valida lead, atendimento e mensagem no backend antes de devolver o conteúdo.")
    @GetMapping("/{mensagemId}/download")
    ResponseEntity<byte[]> baixar(@Parameter(required = true) @PathVariable UUID leadId,
            @PathVariable UUID mensagemId) {
        MidiaDoLead midia = listar.executar(leadId, mensagemId);
        byte[] bytes = armazenamento.baixar(midia.referenciaStorage());
        MediaType tipo = MediaType.APPLICATION_OCTET_STREAM;
        try { tipo = MediaType.parseMediaType(midia.mimetype()); } catch (Exception ignored) { }
        String nome = midia.nome() == null ? "arquivo" : midia.nome();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(tipo);
        headers.setContentLength(bytes.length);
        var disposition = "DOCUMENTO".equals(midia.tipo()) ? ContentDisposition.attachment()
                : ContentDisposition.inline();
        headers.setContentDisposition(disposition.filename(nome, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    record Resposta(UUID mensagemId, UUID atendimentoId, String tipo, String nome, String mimetype,
            long tamanho, String legenda, String urlDownload, String enviadoEm, String origem) {
        static Resposta de(UUID leadId, MidiaDoLead m) {
            return new Resposta(m.mensagemId(), m.atendimentoId(), m.tipo(), m.nome(), m.mimetype(), m.tamanho(),
                    m.legenda(), "/api/v1/leads/" + leadId + "/midias/" + m.mensagemId() + "/download",
                    m.enviadoEm().toString(), m.origem());
        }
    }
}
