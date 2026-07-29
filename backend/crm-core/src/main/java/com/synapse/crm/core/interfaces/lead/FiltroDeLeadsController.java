package com.synapse.crm.core.interfaces.lead;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.lead.ContarLeadsFiltradosUseCase;
import com.synapse.crm.core.application.lead.FiltrarLeadsUseCase;
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
class FiltroDeLeadsController {

    private final FiltrarLeadsUseCase filtrar;
    private final ContarLeadsFiltradosUseCase contar;
    private final CampoCustomizadoRepositorio camposCustomizados;

    FiltroDeLeadsController(
            FiltrarLeadsUseCase filtrar,
            ContarLeadsFiltradosUseCase contar,
            CampoCustomizadoRepositorio camposCustomizados) {
        this.filtrar = filtrar;
        this.contar = contar;
        this.camposCustomizados = camposCustomizados;
    }

    @PostMapping
    List<LeadDaLista> filtrar(@Valid @RequestBody FiltroRequisicao requisicao) {
        return filtrar.executar(requisicao.paraDominio(camposCustomizados)).stream()
                .map(LeadDaLista::de)
                .toList();
    }

    /** Total sob o filtro montado, antes de salvar. A tela chama a cada mudanca de criterio. */
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

    /**
     * Envelope da requisicao.
     *
     * <p>Um objeto em volta do criterio, e nao o criterio na raiz, para que ordenacao e paginacao
     * caibam depois sem quebrar quem ja integrou.
     */
    record FiltroRequisicao(@NotNull CriterioRequisicao criterio) {

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
            String tipo,
            String campo,
            String operador,
            String valor,
            List<String> valores,
            String conector,
            List<CriterioRequisicao> criterios) {

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
}
