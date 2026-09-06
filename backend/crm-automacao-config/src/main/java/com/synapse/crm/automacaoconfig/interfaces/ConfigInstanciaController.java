package com.synapse.crm.automacaoconfig.interfaces;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.automacaoconfig.application.AtualizarLogoDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.AtualizarTemaDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.ObterLogoDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.ObterTemaDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.automacaoconfig.domain.MarcaDaInstanciaInvalidaException;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

/**
 * Configuracao da instancia para o frontend (E07 §4) — a fundacao de frontend que a E10 depende
 * existir: feature flags, tema e textos.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuração da instância", description = "Feature flags, tema e catálogo de textos consumidos pelo frontend.")
class ConfigInstanciaController {

    private final FeatureService features;
    private final ConfiguracaoDeInstanciaResources recursos;
    private final CanalGateway canal;
    private final ObterTemaDaInstanciaUseCase obterTema;
    private final ObterLogoDaInstanciaUseCase obterLogo;
    private final AtualizarTemaDaInstanciaUseCase atualizarTema;
    private final AtualizarLogoDaInstanciaUseCase atualizarLogo;

    ConfigInstanciaController(
            FeatureService features,
            ConfiguracaoDeInstanciaResources recursos,
            CanalGateway canal,
            ObterTemaDaInstanciaUseCase obterTema,
            ObterLogoDaInstanciaUseCase obterLogo,
            AtualizarTemaDaInstanciaUseCase atualizarTema,
            AtualizarLogoDaInstanciaUseCase atualizarLogo) {
        this.features = features;
        this.recursos = recursos;
        this.canal = canal;
        this.obterTema = obterTema;
        this.obterLogo = obterLogo;
        this.atualizarTema = atualizarTema;
        this.atualizarLogo = atualizarLogo;
    }

    @Operation(
            summary = "Listar features habilitadas",
            description = "Retorna somente os nomes das capacidades habilitadas para a instância.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Nomes das features habilitadas."))
    @GetMapping("/features")
    List<String> features() {
        return features.habilitadas();
    }

    @Operation(
            summary = "Obter capacidades do canal",
            description = "Retorna as capacidades do canal ativo que orientam a composição da primeira mensagem.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Capacidades do canal ativo."))
    @GetMapping("/canal")
    CapacidadeDoCanal canal() {
        return new CapacidadeDoCanal(canal.exigeTemplateForaDaJanela());
    }

    @Operation(
            summary = "Obter tema",
            description = "Retorna os design tokens necessários inclusive para a tela de login; não contém credenciais.",
            responses = @ApiResponse(responseCode = "200", description = "Documento JSON do tema."))
    @GetMapping("/tema")
    JsonNode tema() {
        return obterTema.executar();
    }

    @Operation(
            summary = "Obter catálogo de textos",
            description = "Retorna os textos configuráveis necessários inclusive antes da autenticação.",
            responses = @ApiResponse(responseCode = "200", description = "Documento JSON de textos."))
    @GetMapping("/textos")
    JsonNode textos() {
        return recursos.textos();
    }

    @Operation(
            summary = "Obter a marca da instância",
            description = "Retorna logo.png quando o filho tem marca própria configurada; 404 quando não tem — caso normal, o frontend cai no fallback.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Imagem da marca."),
                @ApiResponse(responseCode = "404", description = "Instância sem logo configurado.")
            })
    @GetMapping("/logo")
    ResponseEntity<byte[]> logo() {
        byte[] logo = obterLogo.executar();
        if (logo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // A logo pode ser trocada em runtime; exigir revalidacao evita que o cache do
                // navegador esconda a nova marca por 30 dias (o max-age anterior era de deploy).
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .body(logo);
    }

    @Operation(
            summary = "Atualizar tema da instância",
            description = "Substitui o tema pós-login da instância. A leitura seguinte já usa o valor salvo, sem reiniciar o processo.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                @ApiResponse(responseCode = "200", description = "Tema atualizado."),
                @ApiResponse(responseCode = "400", description = "Tema ausente."),
                @ApiResponse(responseCode = "403", description = "Papel sem permissão de gestão.")
            })
    @PutMapping("/marca/tema")
    JsonNode atualizarTema(@RequestBody JsonNode tema) {
        atualizarTema.executar(tema);
        return obterTema.executar();
    }

    @Operation(
            summary = "Atualizar logo da instância",
            description = "Recebe uma logo PNG, detectada por assinatura de bytes, e troca a referência do storage após persistir a nova marca.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                @ApiResponse(responseCode = "204", description = "Logo atualizada."),
                @ApiResponse(responseCode = "400", description = "Arquivo ausente ou tipo inválido."),
                @ApiResponse(responseCode = "403", description = "Papel sem permissão de gestão.")
            })
    @PutMapping(value = "/marca/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void atualizarLogo(@RequestPart("arquivo") MultipartFile arquivo) {
        try {
            atualizarLogo.executar(arquivo.getBytes(), arquivo.getOriginalFilename());
        } catch (java.io.IOException erro) {
            throw new MarcaDaInstanciaInvalidaException("nao foi possivel ler o arquivo da logo");
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MarcaDaInstanciaInvalidaException.class)
    ProblemDetail marcaInvalida(MarcaDaInstanciaInvalidaException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    record CapacidadeDoCanal(boolean exigeTemplateForaDaJanela) {}
}
