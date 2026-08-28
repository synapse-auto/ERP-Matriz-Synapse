package com.synapse.crm.equipe.interfaces;

import java.time.Duration;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.chat.AbrirConversaDiretaUseCase;
import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;
import com.synapse.crm.equipe.application.chat.ChatSemAcessoException;
import com.synapse.crm.equipe.application.chat.EnviarMensagemChatUseCase;
import com.synapse.crm.equipe.application.chat.EnviarMidiaChatUseCase;
import com.synapse.crm.equipe.application.chat.ListarContatosChatUseCase;
import com.synapse.crm.equipe.application.chat.ListarConversasChatUseCase;
import com.synapse.crm.equipe.application.chat.ListarMensagensChatUseCase;
import com.synapse.crm.equipe.application.chat.MarcarConversaChatComoLidaUseCase;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

@RestController
@RequestMapping("/api/v1/chat-interno")
@Tag(name = "Chat interno", description = "Conversas diretas de texto entre integrantes da equipe.")
@SecurityRequirement(name = "bearerAuth")
public class ChatInternoController {
    private final ListarConversasChatUseCase listar;
    private final ListarContatosChatUseCase contatos;
    private final AbrirConversaDiretaUseCase abrir;
    private final ListarMensagensChatUseCase mensagens;
    private final EnviarMensagemChatUseCase enviar;
    private final EnviarMidiaChatUseCase enviarMidia;
    private final MarcarConversaChatComoLidaUseCase ler;
    private final ArmazenamentoDeMidia armazenamento;

    ChatInternoController(ListarConversasChatUseCase listar, ListarContatosChatUseCase contatos, AbrirConversaDiretaUseCase abrir,
            ListarMensagensChatUseCase mensagens, EnviarMensagemChatUseCase enviar, EnviarMidiaChatUseCase enviarMidia,
            MarcarConversaChatComoLidaUseCase ler, ArmazenamentoDeMidia armazenamento) {
        this.listar = listar; this.contatos = contatos; this.abrir = abrir; this.mensagens = mensagens; this.enviar = enviar; this.enviarMidia = enviarMidia; this.ler = ler; this.armazenamento = armazenamento;
    }

    @Operation(summary = "Listar conversas", description = "Lista as conversas internas das quais o usuário autenticado participa, com última mensagem e contador individual de não lidas.", responses = @ApiResponse(responseCode = "200", description = "Conversas das quais o usuário participa."))
    @GetMapping("/conversas")
    List<ConversaResposta> listar() {
        return listar.executar().stream().map(ConversaResposta::de).toList();
    }

    @Operation(summary = "Listar contatos do chat", description = "Lista integrantes ativos disponíveis para iniciar uma conversa direta, sem expor credenciais ou dados pessoais desnecessários.", responses = @ApiResponse(responseCode = "200", description = "Integrantes ativos, sem dados de contato pessoais."))
    @GetMapping("/contatos")
    List<ContatoResposta> contatos() {
        return contatos.executar().stream().map(c -> new ContatoResposta(c.id(), c.nome(), c.fotoUrl())).toList();
    }

    @Operation(summary = "Abrir conversa direta", description = "Cria ou reutiliza uma conversa direta; a operação é idempotente para o mesmo par.", responses = {
            @ApiResponse(responseCode = "200", description = "Conversa pronta."), @ApiResponse(responseCode = "400", description = "Usuário de destino inválido.")})
    @PostMapping("/conversas/direta")
    ConversaCriada abrir(@Valid @RequestBody AbrirRequisicao requisicao) {
        return new ConversaCriada(abrir.executar(requisicao.usuarioId()));
    }

    @Operation(summary = "Listar mensagens", description = "Consulta o histórico paginado da conversa em ordem cronológica; o cursor permite buscar mensagens anteriores sem acessar conversas alheias.", responses = {
            @ApiResponse(responseCode = "200", description = "Mensagens em ordem cronológica."),
            @ApiResponse(responseCode = "403", description = "O usuário não participa da conversa.")})
    @GetMapping("/conversas/{id}/mensagens")
    PaginaResposta mensagens(@Parameter(required = true) @PathVariable UUID id,
            @RequestParam(required = false) Instant antesDe,
            @RequestParam(defaultValue = "50") int limite) {
        return PaginaResposta.de(mensagens.executar(id, antesDe, limite), armazenamento);
    }

    @Operation(summary = "Enviar mensagem de texto", description = "Persiste uma mensagem textual para os participantes da conversa e publica a notificação em tempo real.", responses = {
            @ApiResponse(responseCode = "201", description = "Mensagem persistida."),
            @ApiResponse(responseCode = "403", description = "O usuário não participa da conversa.")})
    @PostMapping("/conversas/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResposta enviar(@PathVariable UUID id, @Valid @RequestBody MensagemRequisicao requisicao) {
        return MensagemResposta.de(enviar.executar(id, requisicao.conteudo()), armazenamento);
    }

    @Operation(summary = "Enviar mídia", description = "Faz o upload de uma mídia (imagem, áudio, vídeo, documento) e a envia como mensagem no chat interno.", responses = {
            @ApiResponse(responseCode = "201", description = "Mensagem com mídia persistida."),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou muito grande."),
            @ApiResponse(responseCode = "403", description = "O usuário não participa da conversa.")})
    @PostMapping(value = "/conversas/{id}/mensagens/midia", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResposta enviarMidia(
            @PathVariable UUID id,
            @RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo,
            @RequestParam(value = "legenda", required = false) String legenda) throws java.io.IOException {
        return MensagemResposta.de(
                enviarMidia.executar(id, arquivo.getOriginalFilename(), legenda, arquivo.getBytes()),
                armazenamento
        );
    }

    @Operation(summary = "Marcar conversa como lida", description = "Atualiza somente o marcador de leitura do usuário autenticado; a leitura é individual e não altera a fila de outro participante.", responses = @ApiResponse(responseCode = "204", description = "Leitura individual atualizada."))
    @PostMapping("/conversas/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void marcarComoLida(@PathVariable UUID id) { ler.executar(id); }

    @ExceptionHandler(ChatSemAcessoException.class)
    ProblemDetail semAcesso(ChatSemAcessoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    record AbrirRequisicao(@NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID usuarioId) {}
    record MensagemRequisicao(@NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 10000) String conteudo) {}
    record ConversaCriada(UUID id) {}
    public record ContatoResposta(UUID id, String nome, String fotoUrl) {}
    public record ConversaResposta(UUID id, String tipo, String participantes, String ultimaMensagem,
            Instant ultimaMensagemEm, long naoLidas, String fotoUrl) {
        static ConversaResposta de(ChatInternoRepositorio.ConversaResumo r) {
            return new ConversaResposta(r.id(), r.tipo().name(), r.participantes(), r.ultimaMensagem(),
                    r.ultimaMensagemEm(), r.naoLidas(), r.fotoUrl());
        }
    }
    public record PaginaResposta(List<MensagemResposta> mensagens, Instant proximoCursor) {
        static PaginaResposta de(ChatInternoRepositorio.PaginaMensagens p, ArmazenamentoDeMidia armazenamento) {
            return new PaginaResposta(p.mensagens().stream().map(m -> MensagemResposta.de(m, armazenamento)).toList(), p.proximoCursor());
        }
    }
    public record MensagemResposta(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome,
            String tipo, String conteudo, String midiaUrl, Object midiaMetadados, Instant enviadoEm) {
        static MensagemResposta de(ChatInternoRepositorio.MensagemResumo r, ArmazenamentoDeMidia armazenamento) {
            String midiaUrl = r.midiaUrl() == null ? null : armazenamento.urlAssinada(r.midiaUrl(), Duration.ofHours(1));
            return new MensagemResposta(r.id(), r.conversaId(), r.remetenteId(), r.remetenteNome(), r.tipo(), r.conteudo(), midiaUrl, r.midiaMetadados(), r.enviadoEm());
        }
    }
}
