package com.synapse.crm.equipe.domain.permissao;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static ConfiguracaoDePermissoes nivel(Modulo m, NivelDeAcesso n) {
        return new ConfiguracaoDePermissoes(Map.of(m, n), Map.of());
    }

    /** Excecoes de um ATENDENTE que herda o perfil padrao. */
    private static void excecoes(PoliticaDeConcessao.Ator ator, ConfiguracaoDePermissoes atuais, ConfiguracaoDePermissoes novas) {
        PoliticaDeConcessao.exigirConcessao(ator, ALVO, PapelUsuario.ATENDENTE, VAZIA, atuais, novas, FLAGS);
    }

    @Test
    @DisplayName("SUBGESTOR sem delegacao explicita nao edita excecao de ninguem")
    void semDelegacao() {
        codigo(() -> excecoes(ator(PapelUsuario.SUBGESTOR, VAZIA), VAZIA, acao(Capacidade.TAGS_APLICAR, false)),
                Codigo.SEM_DELEGACAO);
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
    @DisplayName("SUBGESTOR delegado: so o delegavel muda, e nunca liga o que nao tem — por interruptor, nivel ou dependencia")
    void conjuntoDelegavel() {
        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, DELEGADO);
        codigo(() -> excecoes(sub, VAZIA, acao(Capacidade.ATENDIMENTOS_FINALIZAR_LOTE, false)), Codigo.FORA_DO_CONJUNTO_DELEGAVEL);
        // nivel e livre quando o efeito e delegavel: Tags em Ver so desliga tags.aplicar
        assertThatCode(() -> excecoes(sub, VAZIA, nivel(Modulo.TAGS, NivelDeAcesso.VER))).doesNotThrowAnyException();
        // ...mas Atendimentos em Editar desligaria o lote, que nao e delegavel
        codigo(() -> excecoes(sub, VAZIA, nivel(Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR)), Codigo.FORA_DO_CONJUNTO_DELEGAVEL);
        // ...e desligar "finalizar" derrubaria o lote pela dependencia
        codigo(() -> excecoes(sub, VAZIA, acao(Capacidade.ATENDIMENTOS_FINALIZAR, false)), Codigo.FORA_DO_CONJUNTO_DELEGAVEL);

        ConfiguracaoDePermissoes subSemResumo = new ConfiguracaoDePermissoes(DELEGADO.niveis(),
                Map.of(Capacidade.EQUIPE_EXCECOES_ATENDENTES, true, Capacidade.RESUMO_IA_SOLICITAR, false));
        PoliticaDeConcessao.Ator limitado = ator(PapelUsuario.SUBGESTOR, subSemResumo);
        codigo(() -> excecoes(limitado, VAZIA, acao(Capacidade.RESUMO_IA_SOLICITAR, true)), Codigo.ACIMA_DA_PROPRIA_PERMISSAO);
        // negar o que e delegavel continua permitido, mesmo sem te-lo
        assertThatCode(() -> excecoes(limitado, VAZIA, acao(Capacidade.RESUMO_IA_SOLICITAR, false))).doesNotThrowAnyException();
        // baixar o nivel de Resumo desliga "gerar resumo" (permitido); subir de volta o religaria pelo
        // efeito, sem tocar no interruptor herdado — e isso ele nao tem
        ConfiguracaoDePermissoes resumoSoLeitura = nivel(Modulo.RESUMO_IA, NivelDeAcesso.VER);
        assertThatCode(() -> excecoes(limitado, VAZIA, resumoSoLeitura)).doesNotThrowAnyException();
        codigo(() -> excecoes(limitado, resumoSoLeitura, VAZIA), Codigo.ACIMA_DA_PROPRIA_PERMISSAO);
    }

    @Test
    @DisplayName("ATENDENTE nao concede nada; GESTOR nao edita GESTOR nem ADMINISTRADOR")
    void outrosPapeis() {
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(ator(PapelUsuario.ATENDENTE, VAZIA), ALVO, PapelUsuario.ATENDENTE),
                Codigo.SEM_DELEGACAO);
        PoliticaDeConcessao.Ator gestor = ator(PapelUsuario.GESTOR, VAZIA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(gestor, ALVO, PapelUsuario.GESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobre(gestor, ALVO, PapelUsuario.ADMINISTRADOR), Codigo.ALVO_FORA_DA_ALCADA);
        assertThatCode(() -> PoliticaDeConcessao.exigirConcessao(gestor, ALVO, PapelUsuario.SUBGESTOR, VAZIA, VAZIA,
                new ConfiguracaoDePermissoes(Map.of(Modulo.EQUIPE, NivelDeAcesso.GERENCIAR),
                        Map.of(Capacidade.EQUIPE_EXCECOES_ATENDENTES, true)), FLAGS)).doesNotThrowAnyException();
    }

    // --- perfis ----------------------------------------------------------------------------------

    private static final ConfiguracaoDePermissoes EDITA_PERFIS = new ConfiguracaoDePermissoes(
            Map.of(Modulo.EQUIPE, NivelDeAcesso.GERENCIAR),
            Map.of(Capacidade.EQUIPE_PERFIS, true));

    private static ConfiguracaoDePermissoes perfilAtendente(ConfiguracaoDePermissoes armazenado) {
        return PoliticaDePermissoes.perfilCompleto(PapelUsuario.ATENDENTE, armazenado);
    }

    private static void noPerfil(PoliticaDeConcessao.Ator ator, ConfiguracaoDePermissoes atual, ConfiguracaoDePermissoes novo) {
        PoliticaDeConcessao.exigirConcessaoNoPerfil(ator, PapelUsuario.ATENDENTE, atual, novo, FLAGS);
    }

    @Test
    @DisplayName("perfis: superiores editam os configuraveis; SUBGESTOR so com delegacao, e so o de ATENDENTE")
    void alcadaSobrePerfis() {
        PoliticaDeConcessao.Ator gestor = ator(PapelUsuario.GESTOR, VAZIA);
        assertThat(PoliticaDeConcessao.podeEditarPerfil(gestor, PapelUsuario.SUBGESTOR)).isTrue();
        assertThat(PoliticaDeConcessao.podeEditarPerfil(gestor, PapelUsuario.ATENDENTE)).isTrue();
        assertThat(PoliticaDeConcessao.podeEditarPerfil(gestor, PapelUsuario.GESTOR)).isFalse();

        // delegacao de excecoes nao e delegacao de perfis
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(ator(PapelUsuario.SUBGESTOR, DELEGADO), PapelUsuario.ATENDENTE),
                Codigo.SEM_DELEGACAO);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(ator(PapelUsuario.ATENDENTE, EDITA_PERFIS), PapelUsuario.ATENDENTE),
                Codigo.SEM_DELEGACAO);

        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, EDITA_PERFIS);
        assertThatCode(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(sub, PapelUsuario.ATENDENTE)).doesNotThrowAnyException();
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(sub, PapelUsuario.SUBGESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(sub, PapelUsuario.GESTOR), Codigo.ALVO_FORA_DA_ALCADA);
        codigo(() -> PoliticaDeConcessao.exigirAlcadaSobrePerfil(sub, PapelUsuario.ADMINISTRADOR), Codigo.ALVO_FORA_DA_ALCADA);
        assertThat(PoliticaDeConcessao.podeEditarPerfil(sub, PapelUsuario.ATENDENTE)).isTrue();
        assertThat(PoliticaDeConcessao.podeEditarPerfil(sub, PapelUsuario.SUBGESTOR)).isFalse();
    }

    @Test
    @DisplayName("perfil ATENDENTE pelo SUBGESTOR: nivel pelo efeito, so o delegavel, nunca liga o que nao tem")
    void conjuntoDelegavelNoPerfil() {
        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, EDITA_PERFIS);
        ConfiguracaoDePermissoes atual = perfilAtendente(VAZIA);

        assertThatCode(() -> noPerfil(sub, atual, perfilAtendente(acao(Capacidade.TAGS_APLICAR, false))))
                .doesNotThrowAnyException();
        // preset de nivel como a tela envia: Tags em Ver desliga tags.aplicar (delegavel)
        assertThatCode(() -> noPerfil(sub, atual, perfilAtendente(new ConfiguracaoDePermissoes(
                Map.of(Modulo.TAGS, NivelDeAcesso.VER), Map.of(Capacidade.TAGS_APLICAR, false))))).doesNotThrowAnyException();
        codigo(() -> noPerfil(sub, atual, perfilAtendente(new ConfiguracaoDePermissoes(
                Map.of(Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR), Map.of(Capacidade.ATENDIMENTOS_FINALIZAR_LOTE, false)))),
                Codigo.FORA_DO_CONJUNTO_DELEGAVEL);
        codigo(() -> noPerfil(sub, atual, perfilAtendente(acao(Capacidade.ATENDIMENTOS_FINALIZAR_LOTE, false))),
                Codigo.FORA_DO_CONJUNTO_DELEGAVEL);

        ConfiguracaoDePermissoes subSemResumo = new ConfiguracaoDePermissoes(EDITA_PERFIS.niveis(),
                Map.of(Capacidade.EQUIPE_PERFIS, true, Capacidade.RESUMO_IA_SOLICITAR, false));
        PoliticaDeConcessao.Ator limitado = ator(PapelUsuario.SUBGESTOR, subSemResumo);
        ConfiguracaoDePermissoes semResumo = perfilAtendente(acao(Capacidade.RESUMO_IA_SOLICITAR, false));
        codigo(() -> noPerfil(limitado, semResumo, perfilAtendente(VAZIA)), Codigo.ACIMA_DA_PROPRIA_PERMISSAO);
        // desligar o que e delegavel continua permitido, mesmo sem te-lo
        assertThatCode(() -> noPerfil(limitado, perfilAtendente(VAZIA), semResumo)).doesNotThrowAnyException();
        // subir o nivel de Resumo com o preset religaria "gerar resumo", que ele nao tem
        ConfiguracaoDePermissoes resumoSoLeitura = perfilAtendente(new ConfiguracaoDePermissoes(
                Map.of(Modulo.RESUMO_IA, NivelDeAcesso.VER), Map.of(Capacidade.RESUMO_IA_SOLICITAR, false)));
        codigo(() -> noPerfil(limitado, resumoSoLeitura, perfilAtendente(VAZIA)), Codigo.ACIMA_DA_PROPRIA_PERMISSAO);
    }

    @Test
    @DisplayName("perfil pelo superior: sem recorte de delegacao; copia so de origem na alcada")
    void superiorNoPerfilECopia() {
        PoliticaDeConcessao.Ator gestor = ator(PapelUsuario.GESTOR, VAZIA);
        assertThatCode(() -> noPerfil(gestor, perfilAtendente(VAZIA), perfilAtendente(new ConfiguracaoDePermissoes(
                Map.of(Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR), Map.of(Capacidade.ATENDIMENTOS_FINALIZAR_LOTE, false)))))
                .doesNotThrowAnyException();

        PoliticaDeConcessao.Ator sub = ator(PapelUsuario.SUBGESTOR, EDITA_PERFIS);
        assertThat(PoliticaDeConcessao.origemDeCopiaNaAlcada(gestor, PapelUsuario.SUBGESTOR)).isTrue();
        assertThat(PoliticaDeConcessao.origemDeCopiaNaAlcada(sub, PapelUsuario.ATENDENTE)).isTrue();
        assertThat(PoliticaDeConcessao.origemDeCopiaNaAlcada(sub, PapelUsuario.SUBGESTOR)).isFalse();
    }

    private static void codigo(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada, Codigo esperado) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(ConcessaoNegadaException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo(esperado));
    }
}
