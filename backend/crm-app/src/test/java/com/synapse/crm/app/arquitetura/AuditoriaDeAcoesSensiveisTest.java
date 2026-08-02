package com.synapse.crm.app.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.automacaoconfig.application.AtualizarConfiguracaoAutomacaoUseCase;
import com.synapse.crm.core.application.tag.GestaoDeTagsUseCases;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * Prova que toda acao sensivel declarada tem cobertura de auditoria (E09a).
 *
 * <p>No espirito das outras protecoes do projeto (ArquiteturaTest, TransacaoObrigatoria): uma
 * protecao so vale alguma coisa se um teste a violar de proposito e confirmar que ela reprova. Sem
 * isso, a auditoria degrada em silencio — um caso de uso sensivel novo simplesmente nao aparece no
 * log, e ninguem descobre ate precisar dele numa investigacao.
 *
 * <p>Cobertura por dois mecanismos, os dois validos (ver decisao registrada na E09a): {@code
 * @Auditable} (checado aqui via reflexao) ou Domain Event com listener de auditoria dedicado (nao
 * verificavel por reflexao generica — documentado explicitamente na lista abaixo, e coberto pelos
 * testes de integracao existentes de {@code AuditoriaDeAtendimentoListener}/{@code
 * AuditoriaDeConfiguracaoAutomacaoListener}). O teste abaixo garante que a lista de acoes sensiveis
 * nao cresce sem que alguem escolha, conscientemente, um dos dois mecanismos.
 */
class AuditoriaDeAcoesSensiveisTest {

    private enum Mecanismo {
        AUDITABLE,
        DOMAIN_EVENT
    }

    private record AcaoSensivel(Class<?> classe, String metodo, Class<?>[] parametros, Mecanismo mecanismo, String nota) {

        static AcaoSensivel viaAuditable(Class<?> classe, String metodo, Class<?>... parametros) {
            return new AcaoSensivel(classe, metodo, parametros, Mecanismo.AUDITABLE, null);
        }

        static AcaoSensivel viaDomainEvent(Class<?> classe, String metodo, String nota, Class<?>... parametros) {
            return new AcaoSensivel(classe, metodo, parametros, Mecanismo.DOMAIN_EVENT, nota);
        }

        String descricao() {
            return classe.getSimpleName() + "#" + metodo;
        }
    }

    /**
     * Lista curada e viva. Todo caso de uso sensivel novo (RN-CRM ou requisito interno de auditoria)
     * entra aqui ao ser escrito — e so entra depois de decidir como sera auditado.
     */
    private static final List<AcaoSensivel> ACOES_SENSIVEIS = List.of(
            AcaoSensivel.viaAuditable(
                    GestaoDeTagsUseCases.class, "criar", String.class, String.class, String.class),
            AcaoSensivel.viaAuditable(
                    GestaoDeTagsUseCases.class,
                    "atualizar",
                    UUID.class,
                    String.class,
                    String.class,
                    String.class),
            AcaoSensivel.viaAuditable(GestaoDeTagsUseCases.class, "remover", UUID.class),
            AcaoSensivel.viaDomainEvent(
                    TransferirAtendimentoUseCase.class,
                    "executar",
                    "AuditoriaDeAtendimentoListener cobre EventoDeAtendimento.AtendimentoTransferido",
                    UUID.class,
                    UUID.class,
                    UUID.class),
            AcaoSensivel.viaDomainEvent(
                    AtualizarConfiguracaoAutomacaoUseCase.class,
                    "executar",
                    "AuditoriaDeConfiguracaoAutomacaoListener cobre ConfiguracaoAutomacaoAtualizada",
                    String.class,
                    String.class));

    @Test
    @DisplayName("toda acao sensivel marcada como AUDITABLE tem @Auditable no metodo")
    void acoesViaAuditable_temAAnotacao() {
        List<String> semCobertura = ACOES_SENSIVEIS.stream()
                .filter(acao -> acao.mecanismo() == Mecanismo.AUDITABLE)
                .filter(acao -> !metodoTemAuditable(acao))
                .map(AcaoSensivel::descricao)
                .toList();

        assertThat(semCobertura)
                .as("acoes sensiveis declaradas como AUDITABLE mas sem @Auditable no metodo")
                .isEmpty();
    }

    @Test
    @DisplayName("toda acao sensivel marcada como DOMAIN_EVENT documenta o listener que a cobre")
    void acoesViaDomainEvent_documentamOListener() {
        List<String> semNota = ACOES_SENSIVEIS.stream()
                .filter(acao -> acao.mecanismo() == Mecanismo.DOMAIN_EVENT)
                .filter(acao -> acao.nota() == null || acao.nota().isBlank())
                .map(AcaoSensivel::descricao)
                .toList();

        assertThat(semNota).as("acoes DOMAIN_EVENT sem listener documentado").isEmpty();
    }

    /**
     * A protecao em si so vale alguma coisa se este teste reprovar quando deveria. Sem isto, a
     * checagem acima poderia estar sempre verde por engano (metodo errado, classe errada) e ninguem
     * notaria — o mesmo padrao de "falhar alto" que o projeto ja exige em outras protecoes.
     */
    @Test
    @DisplayName("a checagem realmente reprova: metodo sem @Auditable e pego")
    void checagem_pegaMetodoSemAuditable() {
        AcaoSensivel semAnotacao = AcaoSensivel.viaAuditable(MetodoSemAuditableDeExemplo.class, "acaoQualquer");

        assertThat(metodoTemAuditable(semAnotacao)).isFalse();
    }

    private static boolean metodoTemAuditable(AcaoSensivel acao) {
        try {
            Method metodo = acao.classe().getDeclaredMethod(acao.metodo(), acao.parametros());
            return metodo.isAnnotationPresent(Auditable.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "acao sensivel declarada com assinatura que nao existe mais: " + acao.descricao(), e);
        }
    }

    /** Helper so para {@link #checagem_pegaMetodoSemAuditable()} — de proposito, sem @Auditable. */
    private static final class MetodoSemAuditableDeExemplo {
        void acaoQualquer() {}
    }
}
