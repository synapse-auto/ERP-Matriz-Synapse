package com.synapse.crm.atendimento.interfaces;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.application.AtendenteDestinoInvalidoException;
import com.synapse.crm.atendimento.application.ContatoIndisponivelParaInicioException;
import com.synapse.crm.atendimento.application.EncaminharMensagemUseCase;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentoUseCase;
import com.synapse.crm.atendimento.application.FinalizarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.IniciarNovoContatoUseCase;
import com.synapse.crm.atendimento.application.PedidoDeNovoContatoInvalidoException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.RegistrarAvaliacaoUseCase;
import com.synapse.crm.atendimento.application.TransferenciaDePotencialProibidaException;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.application.midia.AnexoExcedeuLimiteException;
import com.synapse.crm.atendimento.application.midia.EnviarMidiaUseCase;
import com.synapse.crm.atendimento.application.midia.ObterConfiguracaoComposerUseCase;
import com.synapse.crm.atendimento.application.midia.ResolverLeadDoAtendimentoUseCase;
import com.synapse.crm.atendimento.application.midia.TipoDeMidiaNaoPermitidoException;
import com.synapse.crm.atendimento.application.participacao.GerenciarParticipacaoAtendimentoUseCase;
import com.synapse.crm.atendimento.application.participacao.ParticipanteAtendimento;
import com.synapse.crm.atendimento.application.participacao.PedidoEntradaAtendimento;
import com.synapse.crm.atendimento.application.referencia.AlvoDeResposta;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoAindaAbertoParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoSemAtendenteParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.Avaliacao;
import com.synapse.crm.atendimento.domain.avaliacao.AvaliacaoJaRegistradaException;
import com.synapse.crm.atendimento.domain.avaliacao.NotaDeAvaliacaoInvalidaException;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.mensagem.EncaminhamentoIncompativelException;
import com.synapse.crm.atendimento.domain.mensagem.RespostaAoCanalIndevidaException;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Enviar, transferir e finalizar — as tres acoes que faltavam ter controller. Os casos de uso ja
 * existiam prontos em {@code application/}; este controller so os expoe.
 *
 * <p>Primeiro controller do modulo a injetar {@link UsuarioContext} diretamente: {@code
 * EnviarMensagemUseCase} resolve o remetente sozinho, mas {@code TransferirAtendimentoUseCase} e
 * {@code FinalizarAtendimentoUseCase} recebem "quem pediu" como parametro explicito — nao ha como
 * fugir disso aqui.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
@Tag(name = "Ações de atendimento", description = "Envio, transferência e finalização de conversas visíveis.")
@SecurityRequirement(name = "bearerAuth")
class AtendimentoAcoesController {

    private final EnviarMensagemUseCase enviar;
    private final EncaminharMensagemUseCase encaminharMensagem;
    private final EnviarMidiaUseCase enviarMidia;
    private final ObterConfiguracaoComposerUseCase obterConfiguracaoComposer;
    private final ResolverLeadDoAtendimentoUseCase resolverLead;
    private final TransferirAtendimentoUseCase transferir;
    private final FinalizarAtendimentoUseCase finalizar;
    private final FinalizarAtendimentosVisiveisUseCase finalizarLote;
    private final RegistrarAvaliacaoUseCase avaliacoes;
    private final IniciarNovoContatoUseCase novoContato;
    private final UsuarioContext usuarioContext;
    private final GerenciarParticipacaoAtendimentoUseCase participacao;

    AtendimentoAcoesController(
            EnviarMensagemUseCase enviar,
            EncaminharMensagemUseCase encaminharMensagem,
            EnviarMidiaUseCase enviarMidia,
            ObterConfiguracaoComposerUseCase obterConfiguracaoComposer,
            ResolverLeadDoAtendimentoUseCase resolverLead,
            TransferirAtendimentoUseCase transferir,
            FinalizarAtendimentoUseCase finalizar,
            FinalizarAtendimentosVisiveisUseCase finalizarLote,
            RegistrarAvaliacaoUseCase avaliacoes,
            IniciarNovoContatoUseCase novoContato,
            UsuarioContext usuarioContext,
            GerenciarParticipacaoAtendimentoUseCase participacao) {
        this.enviar = enviar;
        this.encaminharMensagem = encaminharMensagem;
        this.enviarMidia = enviarMidia;
        this.obterConfiguracaoComposer = obterConfiguracaoComposer;
        this.resolverLead = resolverLead;
        this.transferir = transferir;
        this.finalizar = finalizar;
        this.finalizarLote = finalizarLote;
        this.avaliacoes = avaliacoes;
        this.novoContato = novoContato;
        this.usuarioContext = usuarioContext;
        this.participacao = participacao;
    }

    @Operation(
            summary = "Obter limites do composer",
            description = "Retorna os limites configurados que o navegador aplica antes de enviar uma gravação de áudio.",
            responses = @ApiResponse(responseCode = "200", description = "Limites vigentes da instância."))
    @GetMapping("/configuracao-composer")
    ConfiguracaoComposerResposta obterConfiguracaoComposer() {
        ObterConfiguracaoComposerUseCase.Resultado resultado = obterConfiguracaoComposer.executar();
        return new ConfiguracaoComposerResposta(
                resultado.tamanhoMaximoAudioBytes(), resultado.duracaoMaximaAudioSegundos(),
                resultado.tempoNotificacaoSegundos());
    }

    @Operation(
            summary = "Iniciar novo contato WhatsApp",
            description = "Cria ou reusa o lead visível deste telefone e abre a conversa. Texto livre só sai dentro da janela de 24h aberta pelo cliente; fora dela, use template aprovado. Telefone de colega responde 404, igual a lead inexistente.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Conversa aberta; mensagem enfileirada se o pedido trouxe texto ou template."),
                @ApiResponse(responseCode = "404", description = "Lead do telefone não existe ou não é visível."),
                @ApiResponse(responseCode = "422", description = "Pedido inválido, telefone ilegível ou canal fora da janela de texto livre.")
            })
    @PostMapping("/novo-contato")
    NovoContatoResposta iniciarNovoContato(@RequestBody NovoContatoRequisicao requisicao) {
        NovoContatoRequisicao pedido = requisicao == null
                ? new NovoContatoRequisicao(null, null, null, null)
                : requisicao;
        IniciarNovoContatoUseCase.Resultado resultado = novoContato.executar(pedido.paraCasoDeUso());
        return NovoContatoResposta.de(resultado);
    }

    @Operation(
            summary = "Abrir atendimento novo para lead existente",
            description = "Cria ou reutiliza o atendimento aberto do lead visível em modo humano, transfere a propriedade para quem pediu e não envia mensagem. O histórico finalizado permanece intacto.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento humano aberto sem envio de mensagem."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente ou não visível.")
            })
    @PostMapping("/leads/{leadId}/novo")
    NovoContatoResposta abrirParaLeadExistente(
            @Parameter(description = "Lead visível que receberá o atendimento novo.", required = true)
                    @PathVariable UUID leadId) {
        return NovoContatoResposta.de(novoContato.abrirParaLeadExistente(leadId));
    }

    @Operation(
            summary = "Enviar mensagem de texto",
            description = "Persiste a mensagem e a outbox sem bloquear no provedor; enviar manualmente transfere o lead para quem enviou.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem aceita para entrega."),
                @ApiResponse(responseCode = "404", description = "Lead ou atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "422", description = "Canal fora da janela de texto livre.")
            })
    @PostMapping("/mensagens")
    EnvioResposta enviar(@Valid @RequestBody EnviarMensagemRequisicao requisicao) {
        AlvoDeResposta resposta = requisicao.alvoDeResposta();
        EnviarMensagemUseCase.Resultado resultado = resposta == null
                ? enviar.executar(requisicao.leadId(), requisicao.conteudo())
                : enviar.executar(
                        requisicao.leadId(),
                        new ConteudoDeEnvio.MensagemLivre(requisicao.conteudo()),
                        resposta);
        return EnvioResposta.de(resultado);
    }

    @Operation(
            summary = "Enviar template do WhatsApp",
            description = "Envia um template já aprovado. Não exige janela de 24h; como toda ação humana de envio, assume o lead e coloca o atendimento em modo humano antes de enfileirar a entrega. O provedor recusa se o modelo não estiver aprovado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Template aceito para entrega."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente ou não visível.")
            })
    @PostMapping("/mensagens/template")
    EnvioResposta enviarTemplate(@Valid @RequestBody EnviarTemplateRequisicao requisicao) {
        EnviarMensagemUseCase.Resultado resultado = enviar.executar(
                requisicao.leadId(),
                new ConteudoDeEnvio.MensagemTemplate(
                        requisicao.nome(), requisicao.idioma(), requisicao.parametros()));
        return EnvioResposta.de(resultado);
    }

    /**
     * Anexo do atendente (E11b). {@code id} e o atendimento, igual as rotas irmas
     * {@code /transferir} e {@code /finalizar} abaixo — resolvido para o lead aqui, porque
     * {@link EnviarMidiaUseCase} (como {@link EnviarMensagemUseCase}) trabalha em cima de lead.
     */
    @Operation(
            summary = "Enviar mensagem com mídia",
            description = "Valida e armazena o arquivo, persiste a mensagem e agenda a entrega assíncrona pelo canal.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mídia aceita para entrega."),
                @ApiResponse(responseCode = "400", description = "Arquivo não pôde ser lido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "413", description = "Arquivo excede o limite configurado."),
                @ApiResponse(responseCode = "422", description = "Tipo de mídia não permitido ou canal fora da janela.")
            })
    @PostMapping(value = "/{id}/mensagens/midia", consumes = "multipart/form-data")
    EnvioResposta enviarMidia(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(
                            description = "Arquivo binário. Tipo e limite são validados pela configuração da instância.",
                            required = true,
                            content = @Content(schema = @Schema(type = "string", format = "binary")))
                    @RequestPart("arquivo") MultipartFile arquivo,
            @Parameter(description = "Legenda opcional da mídia.")
                    @RequestParam(required = false) String legenda,
            @Parameter(description = "Mensagem de origem quando o anexo é uma resposta.")
                    @RequestParam(required = false) UUID mensagemOrigemId,
            @Parameter(description = "Instante da origem, chave de partição da mensagem.")
                    @RequestParam(required = false) Instant origemEnviadaEm) {
        UUID leadId = resolverLead.executar(id);
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "falha ao ler o arquivo enviado");
        }
        AlvoDeResposta resposta = alvoOpcional(mensagemOrigemId, origemEnviadaEm);
        EnviarMensagemUseCase.Resultado resultado = enviarMidia.executar(
                leadId, conteudo, arquivo.getOriginalFilename(), legenda, resposta);
        return EnvioResposta.de(resultado);
    }

    @Operation(
            summary = "Encaminhar mensagem",
            description = "Cria uma mensagem nova no destino visível, citando a origem. A origem não é alterada. Um destino por envio.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem encaminhada e enfileirada."),
                @ApiResponse(responseCode = "404", description = "Origem ou destino inexistente ou não visível."),
                @ApiResponse(responseCode = "422", description = "Tipo incompatível, destino inválido ou canal fora da janela.")
            })
    @PostMapping("/{id}/mensagens/{mensagemId}/encaminhamentos")
    EnvioResposta encaminhar(
            @PathVariable UUID id,
            @PathVariable UUID mensagemId,
            @RequestParam Instant enviadoEm,
            @Valid @RequestBody EncaminharRequisicao requisicao) {
        return EnvioResposta.de(encaminharMensagem.executar(
                id, mensagemId, enviadoEm, requisicao.destinoAtendimentoId()));
    }

    /** {@code paraAtendenteId} ausente devolve o atendimento para a IA. */
    @Operation(
            summary = "Transferir atendimento",
            description = "Transfere para o atendente informado; corpo ausente ou paraAtendenteId nulo devolve para a IA. Atendente transfere a conversa que enxerga para um colega ativo; em Potenciais, só assume para si ou devolve.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento transferido."),
                @ApiResponse(responseCode = "403", description = "Atendente tentou escolher o destino de um Potencial."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "422", description = "Destino inexistente, inativo ou com papel diferente de ATENDENTE ou SUBGESTOR."),
                @ApiResponse(responseCode = "409", description = "Atendimento já finalizado.")
            })
    @PostMapping("/{id}/transferir")
    AtendimentoResumo transferir(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @RequestBody(required = false) TransferenciaRequisicao requisicao) {
        UUID paraAtendenteId = requisicao == null ? null : requisicao.paraAtendenteId();
        UUID quemPediu = usuarioContext.atual().id();
        Atendimento atualizado = transferir.executar(id, paraAtendenteId, quemPediu);
        return AtendimentoResumo.de(atualizado);
    }

    @Operation(
            summary = "Finalizar atendimento",
            description = "Finaliza uma conversa visível em nome do usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento finalizado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "409", description = "Atendimento já estava finalizado.")
            })
    @PostMapping("/{id}/finalizar")
    AtendimentoResumo finalizar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id) {
        Atendimento atualizado = finalizar.executar(id, usuarioContext.atual().id());
        return AtendimentoResumo.de(atualizado);
    }

    @Operation(
            summary = "Consultar avaliação do atendimento",
            description = "Devolve a nota 0–10 se já existir. Ausência e atendimento invisível respondem 404 iguais.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Avaliação existente."),
                @ApiResponse(responseCode = "404", description = "Atendimento invisível ou ainda sem nota.")
            })
    @GetMapping("/{id}/avaliacao")
    AvaliacaoResposta consultarAvaliacao(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id) {
        return avaliacoes
                .consultar(id)
                .map(AvaliacaoResposta::de)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("avaliacao", id));
    }

    @Operation(
            summary = "Registrar avaliação do atendimento",
            description = "Grava uma única nota 0–10 no atendente dono da conversa já finalizada. Não substitui nota existente.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Avaliação gravada."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível."),
                @ApiResponse(responseCode = "409", description = "Já existe avaliação neste atendimento."),
                @ApiResponse(responseCode = "422", description = "Atendimento aberto, sem atendente ou nota fora da faixa.")
            })
    @PostMapping("/{id}/avaliacao")
    @ResponseStatus(HttpStatus.CREATED)
    AvaliacaoResposta registrarAvaliacao(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Valid @RequestBody AvaliacaoRequisicao requisicao) {
        return AvaliacaoResposta.de(avaliacoes.executar(id, requisicao.nota(), requisicao.comentario()));
    }

    @Operation(
            summary = "Contar atendimentos abertos finalizáveis",
            description = "Retorna quantos atendimentos abertos o usuário autenticado alcança; a contagem serve para confirmação da finalização em lote.",
            responses = @ApiResponse(responseCode = "200", description = "Quantidade de atendimentos visíveis."))
    @GetMapping("/finalizar-lote")
    FinalizacaoEmLotePrevia contarFinalizacaoEmLote() {
        return new FinalizacaoEmLotePrevia(finalizarLote.quantidade());
    }

    @Operation(
            summary = "Finalizar atendimentos visíveis em lote",
            description = "Finaliza todos os atendimentos abertos visíveis ao usuário autenticado. Cada item reaproveita a mesma autorização, regra de estado terminal e evento da finalização individual.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Resultado com itens finalizados e recusados."),
                @ApiResponse(responseCode = "401", description = "Usuário não autenticado.")
            })
    @PostMapping("/finalizar-lote")
    FinalizacaoEmLoteResposta finalizarEmLote() {
        FinalizarAtendimentosVisiveisUseCase.Resultado resultado = finalizarLote.executar();
        return new FinalizacaoEmLoteResposta(
                resultado.solicitados(), resultado.finalizados(), resultado.recusados());
    }

    @Operation(summary = "Pedir entrada em atendimento", description = "Solicita ao responsável acesso colaborativo sem mudar o dono comercial. O pedido não libera histórico nem altera o atendente.", responses = {
            @ApiResponse(responseCode = "200", description = "Pedido criado ou pedido pendente já existente."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente, finalizado ou não visível.")})
    @PostMapping("/{id}/pedir-entrada")
    PedidoEntradaResposta pedirEntrada(@PathVariable UUID id) { return new PedidoEntradaResposta(participacao.solicitar(id)); }

    @Operation(summary = "Pedir entrada pelo contato da Agenda", description = "Localiza o atendimento aberto do lead e cria um pedido estreito, sem expor histórico, ficha ou etapa ao solicitante.", responses = {
            @ApiResponse(responseCode = "200", description = "Pedido criado ou pedido pendente já existente."),
            @ApiResponse(responseCode = "404", description = "Lead sem atendimento aberto ou não visível.")})
    @PostMapping("/pedir-entrada")
    PedidoEntradaResposta pedirEntradaPorLead(@RequestParam UUID leadId) { return new PedidoEntradaResposta(participacao.solicitarPorLead(leadId)); }

    @Operation(summary = "Entrar diretamente em atendimento", description = "Disponível somente para papéis com alçada ampla; não altera o responsável comercial e registra a entrada colaborativa.", responses = {
            @ApiResponse(responseCode = "204", description = "Usuário entrou ou já estava no atendimento."),
            @ApiResponse(responseCode = "403", description = "Papel sem alçada para entrada direta."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")})
    @PostMapping("/{id}/entrar")
    void entrar(@PathVariable UUID id) { participacao.entrar(id); }

    @Operation(summary = "Sair de atendimento colaborativo", description = "Encerra a participação do usuário autenticado sem alterar o responsável comercial.", responses = {
            @ApiResponse(responseCode = "204", description = "Participação encerrada ou já encerrada."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")})
    @PostMapping("/{id}/sair")
    void sair(@PathVariable UUID id) { participacao.sair(id); }

    @Operation(summary = "Aprovar pedido de entrada", description = "O responsável aprova o pedido e adiciona o solicitante como participante, sem transferir a propriedade do atendimento.", responses = {
            @ApiResponse(responseCode = "204", description = "Pedido aprovado."),
            @ApiResponse(responseCode = "403", description = "Somente o responsável pode aprovar."),
            @ApiResponse(responseCode = "404", description = "Pedido inexistente ou expirado.")})
    @PostMapping("/pedidos-entrada/{pedidoId}/aprovar")
    void aprovar(@PathVariable UUID pedidoId) { participacao.aprovar(pedidoId); }

    @Operation(summary = "Recusar pedido de entrada", description = "O responsável recusa o pedido sem alterar o responsável nem conceder acesso ao atendimento.", responses = {
            @ApiResponse(responseCode = "204", description = "Pedido recusado."),
            @ApiResponse(responseCode = "403", description = "Somente o responsável pode recusar."),
            @ApiResponse(responseCode = "404", description = "Pedido inexistente ou expirado.")})
    @PostMapping("/pedidos-entrada/{pedidoId}/recusar")
    void recusar(@PathVariable UUID pedidoId) { participacao.recusar(pedidoId); }

    @Operation(summary = "Listar participantes do atendimento", description = "Lista participantes ativos e históricos conforme a autorização do atendimento; não altera a propriedade comercial.", responses = {
            @ApiResponse(responseCode = "200", description = "Participantes do atendimento."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")})
    @GetMapping("/{id}/participantes")
    java.util.List<ParticipanteAtendimento> participantes(@PathVariable UUID id) { return participacao.participantes(id); }

    @Operation(summary = "Listar pedidos de entrada pendentes", description = "Lista pedidos pendentes que o responsável pode avaliar, sem expor dados de histórico ao solicitante.", responses = {
            @ApiResponse(responseCode = "200", description = "Pedidos pendentes."),
            @ApiResponse(responseCode = "403", description = "Usuário não pode administrar pedidos deste atendimento."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")})
    @GetMapping("/{id}/pedidos-entrada")
    java.util.List<PedidoEntradaAtendimento> pedidos(@PathVariable UUID id) { return participacao.pendentes(id); }

    @Operation(summary = "Consultar meu pedido de entrada", description = "Retorna somente o pedido do usuário autenticado para o atendimento informado.", responses = {
            @ApiResponse(responseCode = "200", description = "Pedido atual ou resposta vazia quando não existe."),
            @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")})
    @GetMapping("/{id}/pedido-entrada/meu")
    java.util.Optional<PedidoEntradaAtendimento> meuPedido(@PathVariable UUID id) { return participacao.meuPedido(id); }

    private static AlvoDeResposta alvoOpcional(UUID mensagemOrigemId, Instant origemEnviadaEm) {
        if (mensagemOrigemId == null && origemEnviadaEm == null) {
            return null;
        }
        if (mensagemOrigemId == null || origemEnviadaEm == null) {
            throw new RespostaAoCanalIndevidaException(
                    "resposta exige mensagemOrigemId e origemEnviadaEm juntos");
        }
        return new AlvoDeResposta(mensagemOrigemId, origemEnviadaEm);
    }

    record PedidoEntradaResposta(UUID pedidoId) {}

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Nao encontrado");
        return problema;
    }

    @ExceptionHandler(ContatoIndisponivelParaInicioException.class)
    ProblemDetail aoRecusarInicioDeContato(ContatoIndisponivelParaInicioException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Contato indisponivel");
        return problema;
    }

    @ExceptionHandler(AtendenteDestinoInvalidoException.class)
    ProblemDetail aoRecusarDestino(AtendenteDestinoInvalidoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Destino invalido");
        problema.setProperty("atendenteId", e.atendenteId());
        problema.setProperty("motivo", e.motivo().descricao());
        return problema;
    }

    @ExceptionHandler(TransferenciaDePotencialProibidaException.class)
    ProblemDetail aoRecusarDistribuicaoDePotencial(TransferenciaDePotencialProibidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problema.setTitle("Transferencia de potencial proibida");
        return problema;
    }

    @ExceptionHandler(AtendimentoJaFinalizadoException.class)
    ProblemDetail aoJaEstarFinalizado(AtendimentoJaFinalizadoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Atendimento ja finalizado");
        return problema;
    }

    @ExceptionHandler(AvaliacaoJaRegistradaException.class)
    ProblemDetail aoJaEstarAvaliado(AvaliacaoJaRegistradaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Avaliacao ja registrada");
        return problema;
    }

    @ExceptionHandler({
        AtendimentoAindaAbertoParaAvaliacaoException.class,
        AtendimentoSemAtendenteParaAvaliacaoException.class,
        NotaDeAvaliacaoInvalidaException.class
    })
    ProblemDetail aoRecusarAvaliacao(RuntimeException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Avaliacao recusada");
        return problema;
    }

    @ExceptionHandler(ForaDaJanelaException.class)
    ProblemDetail aoEstarForaDaJanela(ForaDaJanelaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Fora da janela de 24 horas");
        return problema;
    }

    @ExceptionHandler(RespostaAoCanalIndevidaException.class)
    ProblemDetail aoRecusarResposta(RespostaAoCanalIndevidaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Resposta indevida");
        return problema;
    }

    @ExceptionHandler(EncaminhamentoIncompativelException.class)
    ProblemDetail aoRecusarEncaminhamento(EncaminhamentoIncompativelException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Encaminhamento incompativel");
        return problema;
    }

    @ExceptionHandler(PedidoDeNovoContatoInvalidoException.class)
    ProblemDetail aoRecusarNovoContato(PedidoDeNovoContatoInvalidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Pedido de novo contato invalido");
        return problema;
    }

    @ExceptionHandler(TelefoneInvalidoException.class)
    ProblemDetail aoReceberTelefoneInvalido(TelefoneInvalidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Telefone invalido");
        return problema;
    }

    @ExceptionHandler(TipoDeMidiaNaoPermitidoException.class)
    ProblemDetail aoRecusarTipoDeMidia(TipoDeMidiaNaoPermitidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Tipo de arquivo nao permitido");
        return problema;
    }

    @ExceptionHandler(AnexoExcedeuLimiteException.class)
    ProblemDetail aoExcederLimiteDeAnexo(AnexoExcedeuLimiteException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        problema.setTitle("Anexo excede o tamanho maximo");
        return problema;
    }

    record NovoContatoRequisicao(
            @Schema(description = "Nome do contato.", example = "Maria Silva", requiredMode = Schema.RequiredMode.REQUIRED)
                    String nome,
            @Schema(
                            description = "Telefone com DDD. Aceita máscara brasileira; o CRM normaliza para dígitos com DDI.",
                            example = "(83) 99999-9999",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String telefone,
            @Schema(description = "Texto livre opcional. Incompatível com template.", example = "Olá, sou da Estrutural.")
                    String primeiraMensagem,
            @Schema(description = "Template pré-aprovado opcional. Incompatível com texto livre.")
                    TemplateNovoContatoRequisicao template) {

        IniciarNovoContatoUseCase.Pedido paraCasoDeUso() {
            return new IniciarNovoContatoUseCase.Pedido(
                    nome,
                    telefone,
                    primeiraMensagem,
                    template == null
                            ? null
                            : new IniciarNovoContatoUseCase.Pedido.Template(
                                    template.nome(), template.idioma(), template.parametros()));
        }
    }

    record TemplateNovoContatoRequisicao(String nome, String idioma, List<String> parametros) {
        TemplateNovoContatoRequisicao {
            parametros = parametros == null ? List.of() : List.copyOf(parametros);
        }
    }

    record NovoContatoResposta(UUID leadId, UUID atendimentoId, UUID mensagemId, boolean leadCriado) {

        static NovoContatoResposta de(IniciarNovoContatoUseCase.Resultado resultado) {
            return new NovoContatoResposta(
                    resultado.leadId(),
                    resultado.atendimento().id(),
                    resultado.mensagem() == null ? null : resultado.mensagem().id(),
                    resultado.leadCriado());
        }
    }

    record EnviarMensagemRequisicao(
            @Schema(description = "Lead visível que receberá a mensagem.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID leadId,
            @Schema(description = "Conteúdo textual.", example = "Olá! Posso ajudar?", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String conteudo,
            @Schema(description = "Mensagem de origem quando esta é uma resposta.")
                    UUID mensagemOrigemId,
            @Schema(description = "Instante da origem, chave de partição.")
                    Instant origemEnviadaEm) {

        AlvoDeResposta alvoDeResposta() {
            return alvoOpcional(mensagemOrigemId, origemEnviadaEm);
        }
    }

    record EncaminharRequisicao(
            @NotNull UUID destinoAtendimentoId) {}

    record EnviarTemplateRequisicao(
            @NotNull UUID leadId,
            @NotBlank String nome,
            @NotBlank String idioma,
            List<String> parametros) {
        EnviarTemplateRequisicao {
            parametros = parametros == null ? List.of() : List.copyOf(parametros);
        }
    }

    record ConfiguracaoComposerResposta(
            long tamanhoMaximoAudioBytes, long duracaoMaximaAudioSegundos,
            long tempoNotificacaoSegundos) {}

    record TransferenciaRequisicao(
            @Schema(description = "Destino; nulo devolve o atendimento para a IA.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    UUID paraAtendenteId) {}

    record EnvioResposta(
            UUID atendimentoId,
            UUID mensagemId,
            String statusEntrega,
            Instant enviadoEm,
            boolean transferiuOLead) {

        static EnvioResposta de(EnviarMensagemUseCase.Resultado resultado) {
            return new EnvioResposta(
                    resultado.atendimento().id(),
                    resultado.mensagem().id(),
                    resultado.mensagem().statusEntrega().name(),
                    resultado.mensagem().enviadoEm(),
                    resultado.transferiuOLead());
        }
    }

    record AtendimentoResumo(UUID id, String status, UUID atendenteId) {
        static AtendimentoResumo de(Atendimento atendimento) {
            return new AtendimentoResumo(
                    atendimento.id(), atendimento.status().name(), atendimento.atendenteId());
        }
    }

    record FinalizacaoEmLotePrevia(int quantidade) {}

    record FinalizacaoEmLoteResposta(int solicitados, int finalizados, int recusados) {}

    record AvaliacaoRequisicao(
            @Schema(description = "Nota de 0 a 10.", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull Integer nota,
            @Schema(description = "Comentário opcional do cliente ou do atendente.", example = "Atendimento rápido")
                    @Size(max = 2000) String comentario) {}

    record AvaliacaoResposta(
            UUID id, UUID atendimentoId, UUID atendenteId, int nota, String comentario, Instant criadoEm) {
        static AvaliacaoResposta de(Avaliacao avaliacao) {
            return new AvaliacaoResposta(
                    avaliacao.id(),
                    avaliacao.atendimentoId(),
                    avaliacao.atendenteId(),
                    avaliacao.nota(),
                    avaliacao.comentario(),
                    avaliacao.criadoEm());
        }
    }
}
