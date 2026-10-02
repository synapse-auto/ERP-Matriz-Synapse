package com.synapse.crm.campanhas.interfaces;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.campanhas.application.AtualizarRascunhoUseCase;
import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhasUseCases;
import com.synapse.crm.campanhas.application.ConsultasDeCampanhaUseCases;
import com.synapse.crm.campanhas.application.ControleDaCampanhaUseCases;
import com.synapse.crm.campanhas.application.CriarCampanhaUseCase;
import com.synapse.crm.campanhas.application.EnviarTesteDeCampanhaUseCase;
import com.synapse.crm.campanhas.application.ExportarDestinatariosCsvUseCase;
import com.synapse.crm.campanhas.application.IniciarCampanhaUseCase;
import com.synapse.crm.campanhas.application.ListarExcluidosUseCase;
import com.synapse.crm.campanhas.application.OptOutUseCases;
import com.synapse.crm.campanhas.application.PreverPublicoUseCase;
import com.synapse.crm.campanhas.application.ProjetarEnvioUseCase;
import com.synapse.crm.campanhas.domain.Campanha;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.CampanhaRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.CampanhaResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ConfiguracaoRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ConfiguracaoResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.DestinatarioResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.DetalheResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ExcluidoResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ExcluidosRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.InterruptorRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.LimiteRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ListaResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.OptOutRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.OptOutResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.PaginaResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.PreviaRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.PreviaResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ProjecaoRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.ProjecaoResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.TemplateResposta;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.TesteRequisicao;
import com.synapse.crm.campanhas.interfaces.CampanhaDtos.TesteResposta;

/**
 * Campanhas de template em massa (E220). Leitura para gestor, subgestor e administrador; disparar, pausar,
 * cancelar, alterar limite e configuracao so o administrador. Sem a funcionalidade habilitada ou com um canal
 * que nao administra templates, toda rota responde 404, como se nao existisse.
 */
@RestController
@RequestMapping("/api/v1/campanhas")
@Tag(name = "Campanhas", description = "Envio de template em massa, em ondas, com limite diario")
class CampanhaController {

    private final CriarCampanhaUseCase criar;
    private final AtualizarRascunhoUseCase atualizarRascunho;
    private final PreverPublicoUseCase preverPublico;
    private final ListarExcluidosUseCase listarExcluidos;
    private final ProjetarEnvioUseCase projetar;
    private final IniciarCampanhaUseCase iniciar;
    private final ControleDaCampanhaUseCases controle;
    private final EnviarTesteDeCampanhaUseCase enviarTeste;
    private final ConsultasDeCampanhaUseCases consultas;
    private final ExportarDestinatariosCsvUseCase exportar;
    private final ConfiguracaoDeCampanhasUseCases configuracao;
    private final OptOutUseCases optOuts;

    CampanhaController(
            CriarCampanhaUseCase criar,
            AtualizarRascunhoUseCase atualizarRascunho,
            PreverPublicoUseCase preverPublico,
            ListarExcluidosUseCase listarExcluidos,
            ProjetarEnvioUseCase projetar,
            IniciarCampanhaUseCase iniciar,
            ControleDaCampanhaUseCases controle,
            EnviarTesteDeCampanhaUseCase enviarTeste,
            ConsultasDeCampanhaUseCases consultas,
            ExportarDestinatariosCsvUseCase exportar,
            ConfiguracaoDeCampanhasUseCases configuracao,
            OptOutUseCases optOuts) {
        this.criar = criar;
        this.atualizarRascunho = atualizarRascunho;
        this.preverPublico = preverPublico;
        this.listarExcluidos = listarExcluidos;
        this.projetar = projetar;
        this.iniciar = iniciar;
        this.controle = controle;
        this.enviarTeste = enviarTeste;
        this.consultas = consultas;
        this.exportar = exportar;
        this.configuracao = configuracao;
        this.optOuts = optOuts;
    }

    // --- leitura --------------------------------------------------------------------------------------

    @Operation(summary = "Lista campanhas e os indicadores do topo da tela")
    @GetMapping
    ListaResposta listar(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamanho) {
        return ListaResposta.de(consultas.listar(pagina, tamanho));
    }

    @Operation(
            summary = "Templates da conta para o assistente",
            description = "Todos os templates, com a marca de elegivel e as restricoes (midia no cabecalho, "
                    + "variavel no cabecalho, botao com parametro) que os tornam nao suportados nesta versao.")
    @GetMapping("/templates")
    List<TemplateResposta> templates() {
        return consultas.templates().stream().map(TemplateResposta::de).toList();
    }

    @Operation(summary = "Detalhe da campanha: funil, serie por dia e limite efetivo de hoje")
    @GetMapping("/{id}")
    DetalheResposta detalhe(@PathVariable UUID id) {
        return DetalheResposta.de(consultas.obter(id));
    }

    @Operation(summary = "Destinatarios paginados, com filtro por status e motivo")
    @GetMapping("/{id}/destinatarios")
    PaginaResposta<DestinatarioResposta> destinatarios(
            @PathVariable UUID id,
            @RequestParam(required = false) StatusDoDestinatario status,
            @RequestParam(required = false) MotivoDoDestinatario motivo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanho) {
        return PaginaResposta.de(
                consultas.destinatarios(id, status, motivo, pagina, tamanho), DestinatarioResposta::de);
    }

    @Operation(
            summary = "Conferencia manual",
            description = "Enfileirados sem confirmacao e envios cujo resultado nao se sabe. Nunca sao reenviados sozinhos.")
    @GetMapping("/{id}/conferencia")
    PaginaResposta<DestinatarioResposta> conferencia(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanho) {
        return PaginaResposta.de(consultas.conferencia(id, pagina, tamanho), DestinatarioResposta::de);
    }

    @Operation(summary = "Resultado da campanha em CSV, uma linha por destinatario")
    @GetMapping(value = "/{id}/destinatarios.csv", produces = "text/csv")
    ResponseEntity<byte[]> csv(@PathVariable UUID id) {
        StringBuilder saida = new StringBuilder();
        exportar.executar(id, saida);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campanha-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(saida.toString().getBytes(StandardCharsets.UTF_8));
    }

    // --- assistente -----------------------------------------------------------------------------------

    @Operation(summary = "Previa do publico: so contagens, com os excluidos por motivo")
    @PostMapping("/previa")
    PreviaResposta previa(@Valid @RequestBody PreviaRequisicao requisicao) {
        return PreviaResposta.de(preverPublico.executar(requisicao.filtroDominio()));
    }

    @Operation(summary = "Lista (ate 200) os contatos excluidos por um motivo")
    @PostMapping("/previa/excluidos")
    List<ExcluidoResposta> excluidos(@Valid @RequestBody ExcluidosRequisicao requisicao) {
        MotivoDoDestinatario motivo = motivo(requisicao.motivo());
        FiltroOuVazio filtro = new FiltroOuVazio(requisicao.filtro());
        return listarExcluidos.executar(filtro.dominio(), motivo).stream().map(ExcluidoResposta::de).toList();
    }

    @Operation(summary = "Estimativa de termino e mensagens por dia (mini-calendario)")
    @PostMapping("/projecao")
    ProjecaoResposta projecao(@Valid @RequestBody ProjecaoRequisicao requisicao) {
        return ProjecaoResposta.de(projetar.executar(requisicao.paraDominio()));
    }

    @Operation(summary = "Cria a campanha como rascunho", description = "Nao envia nada.")
    @PostMapping
    ResponseEntity<CampanhaResposta> criar(@Valid @RequestBody CampanhaRequisicao requisicao) {
        Campanha campanha = criar.executar(requisicao.paraDominio());
        return ResponseEntity.created(URI.create("/api/v1/campanhas/" + campanha.id()))
                .body(CampanhaResposta.de(campanha));
    }

    @Operation(summary = "Salva o rascunho (so campanha ainda RASCUNHO)")
    @PutMapping("/{id}")
    CampanhaResposta atualizar(@PathVariable UUID id, @Valid @RequestBody CampanhaRequisicao requisicao) {
        return CampanhaResposta.de(atualizarRascunho.executar(id, requisicao.paraDominio()));
    }

    @Operation(summary = "Envia o template a um contato de teste ja cadastrado, sem contar no limite")
    @PostMapping("/{id}/teste")
    TesteResposta teste(@PathVariable UUID id, @Valid @RequestBody TesteRequisicao requisicao) {
        EnviarTesteDeCampanhaUseCase.Resultado resultado =
                enviarTeste.executar(id, requisicao.telefone(), requisicao.destinatarioAutorizou());
        return new TesteResposta(
                resultado.leadId(), resultado.mensagemId(), resultado.enviadoEm(), resultado.corpoRenderizado());
    }

    // --- controle (administrador) ---------------------------------------------------------------------

    @Operation(summary = "Inicia ou agenda a campanha", description = "Materializa os destinatarios e comeca a enviar na janela.")
    @PostMapping("/{id}/iniciar")
    CampanhaResposta iniciar(@PathVariable UUID id) {
        return CampanhaResposta.de(iniciar.executar(id));
    }

    @Operation(summary = "Pausa a campanha")
    @PostMapping("/{id}/pausar")
    CampanhaResposta pausar(@PathVariable UUID id) {
        return CampanhaResposta.de(controle.pausar(id));
    }

    @Operation(summary = "Retoma a campanha pausada (inclusive a pausada automaticamente)")
    @PostMapping("/{id}/retomar")
    CampanhaResposta retomar(@PathVariable UUID id) {
        return CampanhaResposta.de(controle.retomar(id));
    }

    @Operation(summary = "Cancela a campanha; o que ainda esta pendente nunca sera enviado")
    @PostMapping("/{id}/cancelar")
    CampanhaResposta cancelar(@PathVariable UUID id) {
        return CampanhaResposta.de(controle.cancelar(id));
    }

    @Operation(summary = "Altera o limite diario (e o ritmo); vale no proximo ciclo")
    @PutMapping("/{id}/limite")
    CampanhaResposta limite(@PathVariable UUID id, @Valid @RequestBody LimiteRequisicao requisicao) {
        return CampanhaResposta.de(controle.alterarLimite(id, requisicao.limiteDiario(), requisicao.ritmoPorMinuto()));
    }

    @Operation(summary = "Interruptor da campanha: para o envio imediatamente, sem mudar o status")
    @PutMapping("/{id}/interruptor")
    CampanhaResposta interruptor(@PathVariable UUID id, @RequestBody InterruptorRequisicao requisicao) {
        return CampanhaResposta.de(controle.alterarInterruptor(id, requisicao.desligada()));
    }

    @Operation(summary = "Marca um item da conferencia manual como conferido (nao reenvia)")
    @PostMapping("/{id}/conferencia/{destinatarioId}/conferido")
    ResponseEntity<Void> conferido(@PathVariable UUID id, @PathVariable UUID destinatarioId) {
        return controle.resolverConferencia(id, destinatarioId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // --- configuracao e opt-out -----------------------------------------------------------------------

    @Operation(summary = "Configuracoes de campanhas da instancia")
    @GetMapping("/configuracao")
    ConfiguracaoResposta configuracao() {
        return ConfiguracaoResposta.de(configuracao.obter());
    }

    @Operation(summary = "Altera teto diario, limiares de pausa automatica e o limite informado da Meta")
    @PutMapping("/configuracao")
    ConfiguracaoResposta atualizarConfiguracao(@Valid @RequestBody ConfiguracaoRequisicao requisicao) {
        return ConfiguracaoResposta.de(configuracao.atualizar(requisicao.paraDominio()));
    }

    @Operation(summary = "Contatos que pediram para nao receber campanhas")
    @GetMapping("/optouts")
    PaginaResposta<OptOutResposta> optOuts(
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamanho) {
        OptOutUseCases.Lista lista = optOuts.listar(pagina, tamanho);
        return new PaginaResposta<>(
                lista.itens().stream().map(OptOutResposta::de).toList(), lista.pagina(), lista.tamanho(), lista.total());
    }

    @Operation(summary = "Registra opt-out de um lead: nenhuma campanha o alcanca mais")
    @PutMapping("/optouts/{leadId}")
    ResponseEntity<Void> registrarOptOut(
            @PathVariable UUID leadId, @RequestBody(required = false) OptOutRequisicao requisicao) {
        optOuts.registrar(leadId, requisicao == null ? null : requisicao.motivo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desfaz o opt-out de um lead (administrador)")
    @DeleteMapping("/optouts/{leadId}")
    ResponseEntity<Void> removerOptOut(@PathVariable UUID leadId) {
        return optOuts.remover(leadId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // --- apoio ----------------------------------------------------------------------------------------

    private static MotivoDoDestinatario motivo(String nome) {
        try {
            return MotivoDoDestinatario.valueOf(nome);
        } catch (IllegalArgumentException desconhecido) {
            throw new CampanhaInvalidaException("MOTIVO_INVALIDO", "motivo desconhecido: " + nome);
        }
    }

    /** O assistente pode omitir o filtro: Agenda inteira. */
    private record FiltroOuVazio(CampanhaDtos.FiltroDto filtro) {

        com.synapse.crm.campanhas.domain.FiltroDePublico dominio() {
            return filtro == null
                    ? com.synapse.crm.campanhas.domain.FiltroDePublico.agendaInteira()
                    : filtro.paraDominio();
        }
    }
}
