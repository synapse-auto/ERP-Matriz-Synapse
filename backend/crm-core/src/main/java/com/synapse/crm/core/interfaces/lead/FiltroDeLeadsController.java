package com.synapse.crm.core.interfaces.lead;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.lead.ContarLeadsFiltradosUseCase;
import com.synapse.crm.core.application.lead.FiltrarLeadsUseCase;
import com.synapse.crm.core.application.lead.PaginaDeLeads;
import com.synapse.crm.core.application.tag.ListarTagsDosLeadsUseCase;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.filtro.CampoFiltravel;
import com.synapse.crm.core.domain.filtro.Criterio;
import com.synapse.crm.core.domain.filtro.CriterioComposto;
import com.synapse.crm.core.domain.filtro.CriterioCustomizado;
import com.synapse.crm.core.domain.filtro.CriterioSimples;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.filtro.FiltroInvalidoException;

/**
 * Filtro modular de leads (E03b): resultado e contagem em tempo real.
 *
 * <p>POST, e nao GET com query string, porque o corpo e uma arvore. Serializar arvore em parametro de
 * URL cabe ate a segunda tentativa e depois estoura limite de tamanho — e nao ha cache a preservar
 * numa consulta que muda a cada tecla.
 *
 * <p>Este controller nao interpreta nada. Ele converte JSON em objeto de dominio e deixa o dominio
 * recusar o que nao reconhece; o recorte por papel continua acontecendo no repositorio.
 */
@RestController
@RequestMapping("/api/v1/leads/filtrar")
@Tag(name = "Filtro de leads", description = "Consultas de leads por árvores AND/OR de critérios.")
@SecurityRequirement(name = "bearerAuth")
class FiltroDeLeadsController {

    /** Teto de linhas por pagina — independente do que o cliente pedir em {@code tamanho}. */
    private static final int TAMANHO_MAXIMO_DE_PAGINA = 200;

    private final FiltrarLeadsUseCase filtrar;
    private final ContarLeadsFiltradosUseCase contar;
    private final CampoCustomizadoRepositorio camposCustomizados;
    private final ListarTagsDosLeadsUseCase tagsDosLeads;
    private final int tamanhoPaginaPadrao;

    FiltroDeLeadsController(
            FiltrarLeadsUseCase filtrar,
            ContarLeadsFiltradosUseCase contar,
            CampoCustomizadoRepositorio camposCustomizados,
            ListarTagsDosLeadsUseCase tagsDosLeads,
            @Value("${synapse.leads.tamanho-pagina}") int tamanhoPaginaPadrao) {
        this.filtrar = filtrar;
        this.contar = contar;
        this.camposCustomizados = camposCustomizados;
        this.tagsDosLeads = tagsDosLeads;
        this.tamanhoPaginaPadrao = tamanhoPaginaPadrao;
    }

    /**
     * Descreve os campos filtraveis e seus operadores (E16 §Bloco 1): a barra de filtros da agenda
     * monta a si mesma a partir daqui — campo novo no enum {@link CampoFiltravel} ou linha nova em
     * {@code campo_customizado} com {@code filtravel = true} aparece na tela sem tocar em React.
     *
     * <p>Estaticos primeiro, depois customizados, na mesma ordem de prioridade que {@link
     * CriterioRequisicao#criterioSimplesOuCustomizado} ja usa para resolver um apelido.
     */
    @Operation(
            summary = "Campos filtraveis",
            description = "Lista os campos que o filtro modular aceita, com rótulo, tipo e operadores compatíveis.",
            responses = @ApiResponse(responseCode = "200", description = "Campos estáticos e customizados filtráveis."))
    @GetMapping("/campos")
    List<CampoFiltravelResposta> campos() {
        Stream<CampoFiltravelResposta> estaticos =
                java.util.Arrays.stream(CampoFiltravel.values()).map(CampoFiltravelResposta::de);
        Stream<CampoFiltravelResposta> customizados = camposCustomizados.listarTodos().stream()
                .filter(CampoCustomizado::filtravel)
                .map(CampoFiltravelResposta::de);
        return Stream.concat(estaticos, customizados)
                .sorted(Comparator.comparing(CampoFiltravelResposta::rotulo))
                .toList();
    }

    @Operation(
            summary = "Filtrar leads",
            description = "Executa a árvore de critérios sobre o mesmo recorte de visibilidade usado nas demais consultas de lead, paginado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página de leads correspondentes."),
                @ApiResponse(responseCode = "400", description = "Campo, operador, valor, estrutura da árvore ou paginação inválidos.")
            })
    @PostMapping
    PaginaLeadsResposta filtrar(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "Árvore de critérios simples ou compostos, com paginação opcional.",
                            required = true,
                            content = @Content(examples = @ExampleObject(
                                    name = "Status e empresa",
                                    value = """
                                            {"criterio":{"tipo":"COMPOSTO","conector":"AND","criterios":[{"tipo":"SIMPLES","campo":"status","operador":"IGUAL","valor":"ATIVO"},{"tipo":"SIMPLES","campo":"empresa","operador":"CONTEM","valor":"Exemplo"}]}}
                                            """)))
                    @Valid @RequestBody FiltroRequisicao requisicao) {
        int pagina = paginaValidada(requisicao.pagina());
        int tamanho = tamanhoValidado(requisicao.tamanho());

        PaginaDeLeads paginaDeLeads =
                filtrar.executar(requisicao.paraDominio(camposCustomizados), pagina, tamanho);

        List<UUID> idsDaPagina = paginaDeLeads.leads().stream().map(lead -> lead.id()).toList();
        Map<UUID, List<com.synapse.crm.core.domain.tag.Tag>> tagsPorLead =
                tagsDosLeads.executar(idsDaPagina);

        List<LeadDaLista> leads = paginaDeLeads.leads().stream()
                .map(lead -> LeadDaLista.de(lead, tagsPorLead.getOrDefault(lead.id(), List.of())))
                .toList();
        return new PaginaLeadsResposta(leads, paginaDeLeads.pagina(), paginaDeLeads.temMais());
    }

    /** Total sob o filtro montado, antes de salvar. A tela chama a cada mudanca de criterio. */
    @Operation(
            summary = "Contar leads filtrados",
            description = "Conta os leads correspondentes sem salvar o filtro; útil para pré-visualização na montagem da árvore.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Total dentro do recorte de visibilidade."),
                @ApiResponse(responseCode = "400", description = "Campo, operador, valor ou estrutura da árvore inválido.")
            })
    @PostMapping("/contagem")
    Contagem contar(@Valid @RequestBody FiltroRequisicao requisicao) {
        return new Contagem(contar.executar(requisicao.paraDominio(camposCustomizados)));
    }

    /**
     * Campo fora da allowlist, operador incompativel, valor que nao converte, arvore funda demais:
     * tudo 400, com a razao em texto.
     *
     * <p>400 e nao 422: o corpo esta sintaticamente correto e semanticamente recusado, e o cliente
     * consegue corrigir sozinho com a mensagem. A lista de campos permitidos vai junto para que quem
     * integra descubra o vocabulario sem abrir a documentacao.
     */
    @ExceptionHandler(FiltroInvalidoException.class)
    ProblemDetail aoReceberFiltroInvalido(FiltroInvalidoException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Filtro invalido");
        problema.setProperty("camposPermitidos", CampoFiltravel.apelidos());
        return problema;
    }

    private int paginaValidada(Integer pagina) {
        int valor = pagina == null ? 0 : pagina;
        if (valor < 0) {
            throw new FiltroInvalidoException("pagina deve ser maior ou igual a zero");
        }
        return valor;
    }

    private int tamanhoValidado(Integer tamanho) {
        int valor = tamanho == null ? tamanhoPaginaPadrao : tamanho;
        if (valor < 1 || valor > TAMANHO_MAXIMO_DE_PAGINA) {
            throw new FiltroInvalidoException(
                    "tamanho deve estar entre 1 e " + TAMANHO_MAXIMO_DE_PAGINA);
        }
        return valor;
    }

    /**
     * Envelope da requisicao.
     *
     * <p>Um objeto em volta do criterio, e nao o criterio na raiz, para que ordenacao e paginacao
     * caibam depois sem quebrar quem ja integrou.
     */
    record FiltroRequisicao(
            @Schema(description = "Nó raiz da árvore.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull CriterioRequisicao criterio,
            @Schema(description = "Página, começando em zero. Padrão: 0.") Integer pagina,
            @Schema(description = "Tamanho da página. Padrão: config do servidor.") Integer tamanho) {

        FiltroDeLeads paraDominio(CampoCustomizadoRepositorio camposCustomizados) {
            return new FiltroDeLeads(criterio.paraDominio(1, camposCustomizados));
        }
    }

    /**
     * Um no da arvore como ele chega no JSON: tudo texto, nada convertido.
     *
     * <p>Sem {@code @JsonTypeInfo} de proposito. Polimorfismo do Jackson resolveria o discriminador
     * lendo um nome de tipo do proprio JSON, que e a familia de recursos que ja produziu
     * desserializacao insegura em varios projetos. Aqui o discriminador e um campo comum que este
     * codigo compara com duas constantes suas.
     *
     * <p>O record e recursivo, entao a conversao tambem e — mas carrega a profundidade na mao e para
     * assim que passa do limite, sem nunca descer mais de {@link FiltroDeLeads#PROFUNDIDADE_MAXIMA}
     * niveis. Antes disso o proprio parser do Jackson ja recusa aninhamento absurdo.
     */
    record CriterioRequisicao(
            @Schema(description = "SIMPLES ou COMPOSTO.", allowableValues = {"SIMPLES", "COMPOSTO"}) String tipo,
            @Schema(description = "Apelido de campo fixo ou chave customizada; usado em nó SIMPLES.") String campo,
            @Schema(description = "Operador compatível com o campo; usado em nó SIMPLES.") String operador,
            @Schema(description = "Valor único; mutuamente exclusivo com valores.") String valor,
            @Schema(description = "Lista para operadores EM e ENTRE; mutuamente exclusiva com valor.") List<String> valores,
            @Schema(description = "AND ou OR; usado em nó COMPOSTO.", allowableValues = {"AND", "OR"}) String conector,
            @Schema(description = "Filhos do nó COMPOSTO.") List<CriterioRequisicao> criterios) {

        private static final String SIMPLES = "SIMPLES";
        private static final String COMPOSTO = "COMPOSTO";

        Criterio paraDominio(int profundidade, CampoCustomizadoRepositorio camposCustomizados) {
            if (profundidade > FiltroDeLeads.PROFUNDIDADE_MAXIMA) {
                throw new FiltroInvalidoException(
                        "filtro aninhado alem de " + FiltroDeLeads.PROFUNDIDADE_MAXIMA + " niveis");
            }
            String rotulo = tipo == null ? "" : tipo.trim().toUpperCase();
            return switch (rotulo) {
                case SIMPLES -> criterioSimplesOuCustomizado(camposCustomizados);
                case COMPOSTO -> new CriterioComposto(
                        CriterioComposto.Conector.de(conector), filhos(profundidade, camposCustomizados));
                default -> throw new FiltroInvalidoException("tipo de criterio nao permitido: "
                        + FiltroInvalidoException.eco(tipo) + ". Permitidos: [SIMPLES, COMPOSTO]");
            };
        }

        /**
         * {@code campo} tenta primeiro a allowlist estatica ({@link CampoFiltravel}); so quando nao
         * casa la e que consulta {@code campo_customizado} (E06b) — nesta ordem, para que um filho que
         * um dia cadastre um campo customizado com o mesmo apelido de um campo fixo nunca sobreponha o
         * fixo. A chave so vira {@link CriterioCustomizado} depois de resolvida contra o banco e
         * confirmada {@code filtravel}; nunca a partir do texto cru desta requisicao.
         */
        private Criterio criterioSimplesOuCustomizado(CampoCustomizadoRepositorio camposCustomizados) {
            if (CampoFiltravel.tentar(campo).isPresent()) {
                return CriterioSimples.deTextos(campo, operador, valoresUnificados());
            }
            CampoCustomizado resolvido = campo == null
                    ? null
                    : camposCustomizados.porChave(campo.trim()).filter(CampoCustomizado::filtravel).orElse(null);
            if (resolvido == null) {
                throw new FiltroInvalidoException("campo nao permitido: "
                        + FiltroInvalidoException.eco(campo) + ". Permitidos: " + CampoFiltravel.apelidos());
            }
            return CriterioCustomizado.deTextos(resolvido, operador, valoresUnificados());
        }

        private List<Criterio> filhos(int profundidade, CampoCustomizadoRepositorio camposCustomizados) {
            if (criterios == null) {
                throw new FiltroInvalidoException("criterio COMPOSTO sem a lista 'criterios'");
            }
            return criterios.stream()
                    .map(filho -> {
                        if (filho == null) {
                            throw new FiltroInvalidoException("criterio nulo dentro de 'criterios'");
                        }
                        return filho.paraDominio(profundidade + 1, camposCustomizados);
                    })
                    .toList();
        }

        /**
         * {@code valor} para operador de um valor so, {@code valores} para {@code EM} e {@code ENTRE}.
         *
         * <p>Os dois ao mesmo tempo sao recusados. Escolher um deles em silencio significaria que a
         * consulta executada nao e a que o cliente descreveu — e ninguem descobriria pelo resultado.
         */
        private List<String> valoresUnificados() {
            boolean temValor = valor != null;
            boolean temLista = valores != null && !valores.isEmpty();

            if (temValor && temLista) {
                throw new FiltroInvalidoException(
                        "informe 'valor' ou 'valores', nunca os dois no mesmo criterio");
            }
            if (temValor) {
                return List.of(valor);
            }
            return valores == null ? List.of() : valores;
        }
    }

    /** Resposta da contagem. Objeto, e nao numero cru, para caber metadado depois. */
    record Contagem(long total) {}

    /** Pagina de leads (E16 §Bloco 1): mesma forma de {@code PaginaDeLeads}, so que serializavel. */
    record PaginaLeadsResposta(
            @Schema(description = "Leads desta página.") List<LeadDaLista> leads,
            @Schema(description = "Página devolvida, começando em zero.") int pagina,
            @Schema(description = "Se existe pelo menos mais uma página além desta.") boolean temMais) {}

    /**
     * Um campo filtravel como a tela precisa para se montar sozinha (E16 §Bloco 1): rotulo para
     * exibir, tipo para escolher o widget certo, operadores para o menu de comparacao, e as opcoes
     * fechadas quando o tipo for {@code LISTA} (so em campo customizado).
     */
    record CampoFiltravelResposta(
            @Schema(description = "Apelido usado no corpo do filtro.") String apelido,
            @Schema(description = "Nome legível para a barra de filtros.") String rotulo,
            @Schema(description = "TEXTO, NUMERO, DATA, STATUS, REFERENCIA, BOOLEANO ou LISTA.") String tipo,
            @Schema(description = "Operadores compatíveis com este campo.") List<String> operadores,
            @Schema(description = "Opções fechadas, apenas quando tipo = LISTA.") List<String> opcoes) {

        static CampoFiltravelResposta de(CampoFiltravel campo) {
            return new CampoFiltravelResposta(
                    campo.apelido(),
                    campo.rotulo(),
                    campo.tipo().name(),
                    campo.operadores().stream().map(Enum::name).sorted().toList(),
                    List.of());
        }

        static CampoFiltravelResposta de(CampoCustomizado campo) {
            return new CampoFiltravelResposta(
                    campo.chave(),
                    campo.rotulo(),
                    campo.tipo().name(),
                    campo.tipo().operadoresPermitidos().stream().map(Enum::name).sorted().toList(),
                    campo.opcoes());
        }
    }
}
