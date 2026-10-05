package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.finalizacaomassa.FinalizacaoEmMassaException;
import com.synapse.crm.atendimento.domain.finalizacaomassa.PeriodoDeFinalizacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Valida de novo, no backend, tudo o que o frontend ja checou: o payload nunca e confiavel.
 *
 * <p>Duas etapas de proposito: {@link #montar} e pura (formato, periodo, limites) e serve tambem para
 * comparar a impressao de um pedido repetido; {@link #exigirAtendentesAcessiveis} consulta o banco e so
 * roda quando o pedido e novo.
 */
@Component
public class ValidadorDeFiltroDeFinalizacao {

    static final int MAXIMO_DE_ATENDENTES = 100;

    private final ParametrosDeFinalizacaoEmMassa parametros;
    private final FinalizacaoEmMassaRepositorio repositorio;
    private final UsuarioContext usuarioContext;
    private final ZoneId fuso;

    public ValidadorDeFiltroDeFinalizacao(
            ParametrosDeFinalizacaoEmMassa parametros,
            FinalizacaoEmMassaRepositorio repositorio,
            UsuarioContext usuarioContext,
            ZoneId fuso) {
        this.parametros = parametros;
        this.repositorio = repositorio;
        this.usuarioContext = usuarioContext;
        this.fuso = fuso;
    }

    /** Formato, periodo e limites. Atendentes repetidos sao unificados e a ordem e normalizada. */
    public FiltroDeFinalizacao montar(PedidoDeFinalizacao pedido) {
        if (pedido == null || pedido.atendenteIds() == null || pedido.atendenteIds().isEmpty()) {
            throw FinalizacaoEmMassaException.invalida("ATENDENTES_VAZIOS", "selecione ao menos um atendente");
        }
        if (pedido.atendenteIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw FinalizacaoEmMassaException.invalida("ATENDENTE_INVALIDO", "identificador de atendente ausente");
        }
        List<UUID> ids = pedido.atendenteIds().stream().distinct().sorted().toList();
        if (ids.size() > MAXIMO_DE_ATENDENTES) {
            throw FinalizacaoEmMassaException.invalida(
                    "ATENDENTES_DEMAIS", "no maximo " + MAXIMO_DE_ATENDENTES + " atendentes por operacao");
        }
        PeriodoDeFinalizacao periodo = PeriodoDeFinalizacao.de(
                pedido.de(), pedido.ate(), pedido.horaInicio(), pedido.horaFim(), fuso, parametros.periodoMaximoEmDias());
        return new FiltroDeFinalizacao(ids, periodo);
    }

    /** Todos existem, e quem nao enxerga todos os leads so pode escolher a si mesmo. */
    public void exigirAtendentesAcessiveis(FiltroDeFinalizacao filtro) {
        Set<UUID> existentes = repositorio.usuariosExistentes(filtro.atendenteIds());
        List<UUID> inexistentes = filtro.atendenteIds().stream().filter(id -> !existentes.contains(id)).toList();
        if (!inexistentes.isEmpty()) {
            throw FinalizacaoEmMassaException.invalida(
                    "ATENDENTE_INEXISTENTE", inexistentes.size() + " atendente(s) informado(s) nao existem");
        }
        var executor = usuarioContext.atual();
        boolean escolheuOutro = filtro.atendenteIds().stream().anyMatch(id -> !id.equals(executor.id()));
        if (escolheuOutro && !executor.enxergaTodosOsLeads()) {
            throw new FinalizacaoEmMassaException(
                    FinalizacaoEmMassaException.Categoria.PROIBIDA,
                    "ATENDENTE_FORA_DO_ESCOPO",
                    "seu perfil so pode finalizar os proprios atendimentos");
        }
    }
}
