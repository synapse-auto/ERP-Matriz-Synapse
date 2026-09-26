package com.synapse.crm.equipe.domain.permissao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException.Codigo;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class PoliticaDeConcessaoTest {

    private static final Set<String> FLAGS = Set.of("dashboard");
    private static final ConfiguracaoDePermissoes VAZIA = ConfiguracaoDePermissoes.vazia();
    private static final UUID ALVO = UUID.randomUUID();

    private static PoliticaDeConcessao.Ator ator(PapelUsuario papel, ConfiguracaoDePermissoes excecoes) {
        return new PoliticaDeConcessao.Ator(UUID.randomUUID(), papel,
                PoliticaDePermissoes.calcular(papel, VAZIA, excecoes, FLAGS));
    }

    private static final ConfiguracaoDePermissoes DELEGADO = new ConfiguracaoDePermissoes(
            Map.of(Modulo.EQUIPE, NivelDeAcesso.GERENCIAR),
            Map.of(Capacidade.EQUIPE_EXCECOES_ATENDENTES, true));

    private static ConfiguracaoDePermissoes acao(Capacidade c, boolean v) {
        return new ConfiguracaoDePermissoes(Map.of(), Map.of(c, v));
    }

    @Test
    @DisplayName("SUBGESTOR sem delegacao explicita nao edita excecao de ninguem")
    void semDelegacao() {
        assertThatThrownBy(() -> PoliticaDeConcessao.exigirConcessao(ator(PapelUsuario.SUBGESTOR, VAZIA), ALVO,
                        PapelUsuario.ATENDENTE, VAZIA, acao(Capacidade.TAGS_APLICAR, false)))
                .isInstanceOfSatisfying(ConcessaoNegadaException.class, e -> org.assertj.core.api.Assertions
                        .assertThat(e.codigo()).isEqualTo(Codigo.SEM_DELEGACAO));
    }

    @Test
    @DisplayName("SUBGESTOR delegado: so ATENDENTE, nunca a si, par ou superior")
    void alvosDoSubgestor() {
        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, DELEGADO);
        assertThatCode(() -> PoliticaDeConcessao.exigirAlcadaSobre(sub, ALVO, PapelUsuario.ATENDENTE)).doesNotThrowAnyException();
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(sub, sub.id(), PapelUsuario.SUBGESTOR), Codigo.ALVO_PROPRIO);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(sub, ALVO, PapelUsuario.SUBGESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(sub, ALVO, PapelUsuario.GESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(sub, ALVO, PapelUsuario.ADMINISTRADOR), Codigo.ALVO_FORA_DA_ALCADA);
    }

    @Test
    @DisplayName("SUBGESTOR delegado nao mexe em nivel, em acao nao delegavel, nem liga o que nao tem")
    void conjuntoDelegavel() {
        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, DELEGADO);
        codigo(() -> PoliticaDeConcessao.exigirConcessao(sub, ALVO, PapelUsuario.ATENDENTE, VAZIA,
                new ConfiguracaoDePermissoes(Map.of(Modulo.TAGS, NivelDeAcesso.VER), Map.of())), Codigo.NIVEL_NAO_DELEGAVEL);
        codigo(() -> PoliticaDeConcessao.exigirConcessao(sub, ALVO, PapelUsuario.ATENDENTE, VAZIA,
                acao(Capacidade.ATENDIMENTOS_FINALIZAR_LOTE, false)), Codigo.FORA_DO_CONJUNTO_DELEGAVEL);

        ConfiguracaoDePermissoes subSemResumo = new ConfiguracaoDePermissoes(DELEGADO.niveis(),
                Map.of(Capacidade.EQUIPE_EXCECOES_ATENDENTES, true, Capacidade.RESUMO_IA_SOLICITAR, false));
        PoliticaDeConcessao.Ator limitado = ator(PapelUsuario.SUBGESTOR, subSemResumo);
        codigo(() -> PoliticaDeConcessao.exigirConcessao(limitado, ALVO, PapelUsuario.ATENDENTE, VAZIA,
                acao(Capacidade.RESUMO_IA_SOLICITAR, true)), Codigo.ACIMA_DA_PROPRIA_PERMISSAO);
        // negar o que e delegavel continua permitido
        assertThatCode(() -> PoliticaDeConcessao.exigirConcessao(limitado, ALVO, PapelUsuario.ATENDENTE, VAZIA,
                acao(Capacidade.RESUMO_IA_SOLICITAR, false))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ATENDENTE nao concede nada; GESTOR nao edita GESTOR nem ADMINISTRADOR; so superiores editam perfis")
    void outrosPapeis() {
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(ator(PapelUsuario.ATENDENTE, VAZIA), ALVO, PapelUsuario.ATENDENTE),
                Codigo.SEM_DELEGACAO);
        PoliticaDeConcessao.Ator gestor = ator(PapelUsuario.GESTOR, VAZIA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(gestor, ALVO, PapelUsuario.GESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(gestor, ALVO, PapelUsuario.ADMINISTRADOR), Codigo.ALVO_FORA_DA_ALCADA);
        assertThatCode(() -> PoliticaDeConcessao.exigirConcessao(gestor, ALVO, PapelUsuario.SUBGESTOR, VAZIA,
                new ConfiguracaoDePermissoes(Map.of(Modulo.EQUIPE, NivelDeAcesso.GERENCIAR),
                        Map.of(Capacidade.EQUIPE_EXCECOES_ATENDENTES, true)))).doesNotThrowAnyException();
        codigo(() -> PoliticaDeConcessao.exigirEdicaoDePerfil(ator(PapelUsuario.SUBGESTOR, DELEGADO)),
                Codigo.PERFIL_SO_PARA_SUPERIORES);
        assertThatCode(() -> PoliticaDeConcessao.exigirEdicaoDePerfil(gestor)).doesNotThrowAnyException();
    }

    private static void codigo(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada, Codigo esperado) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(ConcessaoNegadaException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo(esperado));
    }
}
