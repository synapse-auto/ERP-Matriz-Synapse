package com.synapse.crm.equipe.interfaces;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.permissao.ConsultarPermissoesDeUsuariosUseCase;
import com.synapse.crm.equipe.application.permissao.ListarPerfisDePermissaoUseCase;
import com.synapse.crm.equipe.application.permissao.ObterCatalogoDePermissoesUseCase;
import com.synapse.crm.equipe.application.permissao.ObterMinhasPermissoesUseCase;
import com.synapse.crm.equipe.application.permissao.PreverCopiaDePermissoesUseCase;
import com.synapse.crm.equipe.application.permissao.RestaurarPadraoDoUsuarioUseCase;
import com.synapse.crm.equipe.application.permissao.SalvarExcecoesDeUsuarioUseCase;
import com.synapse.crm.equipe.application.permissao.SalvarPerfilDePermissaoUseCase;
import com.synapse.crm.equipe.application.permissao.Visoes;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.PoliticaDePermissoes;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Gestao: catalogo, perfis, excecoes por usuario, copia e efetivas do autenticado (docs/47).
 *
 * <p>A autorizacao esta nos casos de uso; aqui so ha traducao HTTP. Erros saem como RFC 7807 via
 * {@link ProblemasDeGestao}: 422 payload invalido (com a lista de violacoes), 403 concessao fora da
 * alcada, 409 revisao desatualizada, 404 alvo inexistente.
 */
@RestController
@RequestMapping("/api/v1/gestao/permissoes")
@Tag(name = "Gestão — permissões", description = "Perfis, exceções por usuário e permissões efetivas.")
@SecurityRequirement(name = "bearerAuth")
class GestaoPermissoesController {

    private final ObterCatalogoDePermissoesUseCase catalogo;
    private final ListarPerfisDePermissaoUseCase perfis;
    private final SalvarPerfilDePermissaoUseCase salvarPerfil;
    private final ConsultarPermissoesDeUsuariosUseCase usuarios;
    private final SalvarExcecoesDeUsuarioUseCase salvarExcecoes;
    private final RestaurarPadraoDoUsuarioUseCase restaurar;
    private final PreverCopiaDePermissoesUseCase copia;
    private final ObterMinhasPermissoesUseCase minhas;

    GestaoPermissoesController(ObterCatalogoDePermissoesUseCase catalogo, ListarPerfisDePermissaoUseCase perfis,
            SalvarPerfilDePermissaoUseCase salvarPerfil, ConsultarPermissoesDeUsuariosUseCase usuarios,
            SalvarExcecoesDeUsuarioUseCase salvarExcecoes, RestaurarPadraoDoUsuarioUseCase restaurar,
            PreverCopiaDePermissoesUseCase copia, ObterMinhasPermissoesUseCase minhas) {
        this.catalogo = catalogo;
        this.perfis = perfis;
        this.salvarPerfil = salvarPerfil;
        this.usuarios = usuarios;
        this.salvarExcecoes = salvarExcecoes;
        this.restaurar = restaurar;
        this.copia = copia;
        this.minhas = minhas;
    }

    @Operation(summary = "Minhas permissões efetivas", description = "Efetivas do usuário autenticado, calculadas no backend, em uma única resposta.")
    @GetMapping("/minhas")
    MinhasResposta minhas() {
        return MinhasResposta.de(minhas.executar());
    }

    @Operation(summary = "Catálogo disponível", description = "Módulos e capacidades implementados cuja feature flag está ligada, com limites por papel, dependências, sensibilidade e delegabilidade.")
    @GetMapping("/catalogo")
    CatalogoResposta catalogo() {
        return CatalogoResposta.de(catalogo.executar());
    }

    @Operation(summary = "Perfis", description = "Gestor (fixo), Subgestor e Atendente com contagem real de usuários ativos, configuração salva e efetivo calculado.")
    @GetMapping("/perfis")
    List<PerfilResposta> perfis() {
        return perfis.executar().stream().map(PerfilResposta::de).toList();
    }

    @Operation(summary = "Salvar perfil", description = "Substitui níveis e interruptores do perfil numa transação. Exige a revisão lida.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil salvo."),
                @ApiResponse(responseCode = "403", description = "Somente Gestor/Administrador editam perfis."),
                @ApiResponse(responseCode = "409", description = "Outra pessoa salvou este perfil depois da leitura."),
                @ApiResponse(responseCode = "422", description = "Identificador desconhecido, estrutural, fora do teto, fora da flag ou combinação incoerente.")
            })
    @PutMapping("/perfis/{papel}")
    GravacaoResposta salvarPerfil(@Parameter(description = "SUBGESTOR ou ATENDENTE.") @PathVariable PapelUsuario papel,
            @Valid @RequestBody PerfilRequisicao requisicao) {
        return GravacaoResposta.de(salvarPerfil.executar(papel, requisicao.revisaoEsperada(),
                requisicao.niveis(), requisicao.acoes(), requisicao.copiadoDe()));
    }

    @Operation(summary = "Prévia de cópia entre perfis", description = "Calcula, sem salvar, o perfil resultante de copiar a origem para o destino, recortado pelo teto do destino, com o que muda e o que é impedido.")
    @PostMapping("/perfis/{papel}/copia/previa")
    PreviaResposta previaDePerfil(@PathVariable PapelUsuario papel, @Valid @RequestBody CopiaDePerfilRequisicao requisicao) {
        return PreviaResposta.de(copia.paraPerfil(papel, requisicao.origem()));
    }

    @Operation(summary = "Integrantes e exceções", description = "Integrantes visíveis para quem pede, com quantidade de exceções persistidas válidas.")
    @GetMapping("/usuarios")
    List<ResumoResposta> usuarios() {
        return usuarios.listar().stream().map(ResumoResposta::de).toList();
    }

    @Operation(summary = "Permissões de um usuário", description = "Padrão do perfil, exceção explícita e efetivo por módulo e ação.",
            responses = {@ApiResponse(responseCode = "403", description = "Alvo fora da alçada."), @ApiResponse(responseCode = "404", description = "Usuário inexistente.")})
    @GetMapping("/usuarios/{id}")
    UsuarioResposta usuario(@PathVariable UUID id) {
        return UsuarioResposta.de(usuarios.obter(id));
    }

    @Operation(summary = "Salvar exceções", description = "Substitui todas as exceções do usuário (chave ausente = herdar). Exige a revisão lida.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Exceções salvas."),
                @ApiResponse(responseCode = "403", description = "Concessão fora da alçada (delegação, alvo, conjunto delegável ou acima da própria permissão)."),
                @ApiResponse(responseCode = "409", description = "Revisão desatualizada."),
                @ApiResponse(responseCode = "422", description = "Payload inválido.")
            })
    @PutMapping("/usuarios/{id}/excecoes")
    GravacaoResposta salvarExcecoes(@PathVariable UUID id, @Valid @RequestBody ExcecoesRequisicao requisicao) {
        return GravacaoResposta.de(salvarExcecoes.executar(id, requisicao.revisaoEsperada(),
                requisicao.niveis(), requisicao.acoes(), requisicao.copiadoDe()));
    }

    @Operation(summary = "Voltar ao padrão do perfil", description = "Remove todas as exceções do usuário. Exige a revisão lida.")
    @DeleteMapping("/usuarios/{id}/excecoes")
    GravacaoResposta restaurar(@PathVariable UUID id,
            @Parameter(description = "Revisão lida das exceções.", required = true) @RequestParam long revisaoEsperada) {
        return GravacaoResposta.de(restaurar.executar(id, revisaoEsperada));
    }

    @Operation(summary = "Prévia de cópia entre usuários", description = "Calcula, sem salvar, as exceções que tornam o destino igual à origem dentro do teto do destino e da alçada de quem copia.")
    @PostMapping("/usuarios/{id}/copia/previa")
    PreviaResposta previaDeUsuario(@PathVariable UUID id, @Valid @RequestBody CopiaDeUsuarioRequisicao requisicao) {
        return PreviaResposta.de(copia.paraUsuario(id, requisicao.origemUsuarioId()));
    }

    // --- requisicoes ----------------------------------------------------------------------------

    record PerfilRequisicao(
            @Schema(description = "Revisão lida do perfil.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero Long revisaoEsperada,
            @Schema(description = "Nível por módulo (id do módulo → SEM_ACESSO/VER/EDITAR/GERENCIAR).") Map<String, String> niveis,
            @Schema(description = "Interruptor por capacidade (id → true/false).") Map<String, Boolean> acoes,
            @Schema(description = "Perfil de origem quando a gravação confirma uma cópia.") PapelUsuario copiadoDe) {}

    record ExcecoesRequisicao(
            @Schema(description = "Revisão lida das exceções.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @PositiveOrZero Long revisaoEsperada,
            @Schema(description = "Exceção de nível por módulo; ausente = herdar.") Map<String, String> niveis,
            @Schema(description = "Exceção por capacidade; ausente = herdar.") Map<String, Boolean> acoes,
            @Schema(description = "Usuário de origem quando a gravação confirma uma cópia.") UUID copiadoDe) {}

    record CopiaDePerfilRequisicao(@NotNull PapelUsuario origem) {}

    record CopiaDeUsuarioRequisicao(@NotNull UUID origemUsuarioId) {}

    // --- respostas ------------------------------------------------------------------------------

    record ModuloDoCatalogo(String id, String nivelMinimoPermitido, String flag, Map<String, String> nivelMaximoPorPapel,
            Map<String, String> nivelPadraoPorPapel) {}

    record CapacidadeDoCatalogo(String id, String modulo, String nivelMinimo, String tipo, boolean sensivel,
            List<String> teto, boolean delegavel, List<String> dependencias, Map<String, String> alcancePorPapel) {}

    record CatalogoResposta(List<ModuloDoCatalogo> modulos, List<CapacidadeDoCatalogo> capacidades) {
        static CatalogoResposta de(ObterCatalogoDePermissoesUseCase.Catalogo c) {
            return new CatalogoResposta(
                    c.modulos().stream().map(m -> new ModuloDoCatalogo(m.id(), m.nivelMinimoPermitido().name(), m.flag(),
                            porPapel(p -> PoliticaDePermissoes.nivelMaximo(p, m).name()),
                            porPapel(p -> PoliticaDePermissoes.nivelPadrao(p, m).name()))).toList(),
                    c.capacidades().stream().map(cap -> new CapacidadeDoCatalogo(cap.id(), cap.modulo().id(),
                            cap.nivelMinimo().name(), cap.tipo().name(), cap.sensivel(),
                            cap.teto().stream().sorted().map(Enum::name).toList(), cap.delegavel(),
                            cap.dependencias().stream().map(Capacidade::id).toList(),
                            cap.estrutural() ? porPapel(p -> cap.alcancePara(p).name()) : Map.of())).toList());
        }
    }

    record ModuloResposta(String id, String doPerfil, String excecao, String efetivo, String minimo, String maximo) {
        static ModuloResposta de(Visoes.LinhaDeModulo l) {
            return new ModuloResposta(l.modulo().id(), nome(l.doPerfil()), nome(l.excecao()), nome(l.efetivo()),
                    nome(l.minimo()), nome(l.maximo()));
        }
    }

    record CapacidadeResposta(String id, Boolean doPerfil, Boolean excecao, boolean permitido, String motivo,
            String origem, String alcance, boolean alteravel) {
        static CapacidadeResposta de(Visoes.LinhaDeCapacidade l) {
            return new CapacidadeResposta(l.capacidade().id(), l.doPerfil(), l.excecao(), l.efetivo().permitido(),
                    l.efetivo().motivo().name(), l.efetivo().origem().name(), nome(l.alcance()), l.alteravelPorQuemPede());
        }
    }

    record PerfilResposta(String papel, boolean fixo, long revisao, int usuarios, long permitidas, long total,
            boolean editavel, List<ModuloResposta> modulos, List<CapacidadeResposta> capacidades) {
        static PerfilResposta de(Visoes.Perfil p) {
            return new PerfilResposta(p.papel().name(), p.fixo(), p.revisao(), p.usuarios(), p.permitidas(), p.total(),
                    p.editavel(), p.modulos().stream().map(ModuloResposta::de).toList(),
                    p.capacidades().stream().map(CapacidadeResposta::de).toList());
        }
    }

    record ResumoResposta(UUID id, String nome, String email, String papel, boolean ativo, String fotoUrl,
            int excecoes, boolean editavel) {
        static ResumoResposta de(Visoes.ResumoDeUsuario r) {
            return new ResumoResposta(r.id(), r.nome(), r.email(), r.papel().name(), r.ativo(),
                    r.fotoReferencia() == null ? null : "/api/v1/me/foto/" + r.id(), r.excecoes(), r.editavel());
        }
    }

    record UsuarioResposta(ResumoResposta usuario, long revisao, long revisaoDoPerfil, boolean fixo,
            List<ModuloResposta> modulos, List<CapacidadeResposta> capacidades) {
        static UsuarioResposta de(Visoes.Usuario u) {
            return new UsuarioResposta(ResumoResposta.de(u.resumo()), u.revisao(), u.revisaoDoPerfil(), u.fixo(),
                    u.modulos().stream().map(ModuloResposta::de).toList(),
                    u.capacidades().stream().map(CapacidadeResposta::de).toList());
        }
    }

    record EfetivoResposta(boolean permitido, String motivo, String alcance) {}

    record MinhasResposta(UUID usuarioId, String papel, long revisao, boolean acessaGestao, boolean editaPerfis,
            boolean editaExcecoes, Map<String, EfetivoResposta> capacidades) {
        static MinhasResposta de(Visoes.Minhas m) {
            Map<String, EfetivoResposta> capacidades = new LinkedHashMap<>();
            m.capacidades().forEach(l -> capacidades.put(l.capacidade().id(), new EfetivoResposta(
                    l.efetivo().permitido(), l.efetivo().motivo().name(), nome(l.alcance()))));
            return new MinhasResposta(m.usuarioId(), m.papel().name(), m.revisao(), m.acessaGestao(),
                    m.editaPerfis(), m.editaExcecoes(), capacidades);
        }
    }

    record AlteracaoResposta(String chave, String antes, String depois) {}

    record ImpedimentoResposta(String chave, String motivo) {}

    record PreviaResposta(Map<String, String> niveis, Map<String, Boolean> acoes, List<AlteracaoResposta> alteracoes,
            List<ImpedimentoResposta> impedidos) {
        static PreviaResposta de(Visoes.PreviaDeCopia p) {
            return new PreviaResposta(p.niveis(), p.acoes(),
                    p.alteracoes().stream().map(a -> new AlteracaoResposta(a.chave(), a.antes(), a.depois())).toList(),
                    p.impedidos().stream().map(i -> new ImpedimentoResposta(i.chave(), i.motivo().name())).toList());
        }
    }

    record GravacaoResposta(String operacao, long revisaoAnterior, long revisao, int excecoes) {
        static GravacaoResposta de(Visoes.Gravacao g) {
            return new GravacaoResposta(g.operacao().name(), g.revisaoAnterior(), g.revisao(), g.excecoes());
        }
    }

    private static String nome(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private static Map<String, String> porPapel(java.util.function.Function<PapelUsuario, String> valor) {
        Map<String, String> mapa = new LinkedHashMap<>();
        Arrays.stream(PapelUsuario.values()).forEach(p -> mapa.put(p.name(), valor.apply(p)));
        return mapa;
    }
}
