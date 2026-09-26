package com.synapse.crm.equipe.domain.permissao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade.Motivo;
import com.synapse.crm.equipe.domain.permissao.EstadoDaCapacidade.Origem;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class PoliticaDePermissoesTest {

    private static final Set<String> FLAGS = Set.of("dashboard");
    private static final ConfiguracaoDePermissoes VAZIA = ConfiguracaoDePermissoes.vazia();

    private static ConfiguracaoDePermissoes acoes(Map<Capacidade, Boolean> acoes) {
        return new ConfiguracaoDePermissoes(Map.of(), acoes);
    }

    @ParameterizedTest
    @EnumSource(PapelUsuario.class)
    @DisplayName("sem nada salvo, o efetivo reproduz exatamente o hasAnyRole de antes (menos as delegacoes novas)")
    void padraoPreservaAcessoOperacional(PapelUsuario papel) {
        PermissoesEfetivas efetivas = PoliticaDePermissoes.calcular(papel, VAZIA, VAZIA, FLAGS);
        for (Capacidade c : Capacidade.values()) {
            boolean esperado = c.estrutural() ? c.noTetoDe(papel) : c.permitidaPorPadraoPara(papel);
            assertThat(efetivas.permite(c)).as(c.id() + " para " + papel).isEqualTo(esperado);
        }
    }

    @Test
    @DisplayName("delegacoes ao SUBGESTOR nascem desligadas; GESTOR as tem por ser fixo")
    void delegacoesNascemDesligadas() {
        PermissoesEfetivas sub = PoliticaDePermissoes.calcular(PapelUsuario.SUBGESTOR, VAZIA, VAZIA, FLAGS);
        PermissoesEfetivas gestor = PoliticaDePermissoes.calcular(PapelUsuario.GESTOR, VAZIA, VAZIA, FLAGS);
        for (Capacidade c : Set.of(Capacidade.EQUIPE_CRIAR, Capacidade.EQUIPE_DESATIVAR,
                Capacidade.EQUIPE_SENHA_PROVISORIA, Capacidade.EQUIPE_EXCECOES_ATENDENTES, Capacidade.EQUIPE_EDITAR)) {
            assertThat(sub.permite(c)).as(c.id()).isFalse();
            assertThat(gestor.permite(c)).as(c.id()).isTrue();
        }
        assertThat(sub.permite(Capacidade.EQUIPE_VER)).isTrue();
        assertThat(sub.permite(Capacidade.EQUIPE_PERFIS)).isFalse();
        assertThat(sub.estado(Capacidade.EQUIPE_PERFIS).motivo()).isEqualTo(Motivo.TETO_DO_PAPEL);
    }

    @Test
    @DisplayName("ATENDENTE fica em Meus e nunca alcanca Todos; GESTOR nunca recebe operacao de ADMINISTRADOR")
    void atendenteEmMeus() {
        assertThat(Capacidade.ATENDIMENTOS_VER.alcancePara(PapelUsuario.ATENDENTE)).isEqualTo(Capacidade.Alcance.MEUS);
        assertThat(Capacidade.ATENDIMENTOS_VER.alcancePara(PapelUsuario.SUBGESTOR)).isEqualTo(Capacidade.Alcance.TODOS);
        for (Capacidade c : Capacidade.values()) {
            assertThat(c.noTetoDe(PapelUsuario.ADMINISTRADOR) || !c.noTetoDe(PapelUsuario.GESTOR)).as(c.id()).isTrue();
        }
    }

    @Test
    @DisplayName("perfil muda herdeiros; excecao explicita substitui; restaurar volta a herdar")
    void heranca() {
        ConfiguracaoDePermissoes perfilSemTags = acoes(Map.of(Capacidade.TAGS_APLICAR, false));
        assertThat(PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, perfilSemTags, VAZIA, FLAGS)
                .permite(Capacidade.TAGS_APLICAR)).isFalse();

        ConfiguracaoDePermissoes excecao = acoes(Map.of(Capacidade.TAGS_APLICAR, true));
        EstadoDaCapacidade comExcecao = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, perfilSemTags, excecao, FLAGS)
                .estado(Capacidade.TAGS_APLICAR);
        assertThat(comExcecao.permitido()).isTrue();
        assertThat(comExcecao.origem()).isEqualTo(Origem.EXCECAO);

        ConfiguracaoDePermissoes negada = acoes(Map.of(Capacidade.RESUMO_IA_SOLICITAR, false));
        assertThat(PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, VAZIA, negada, FLAGS)
                .permite(Capacidade.RESUMO_IA_SOLICITAR)).isFalse();
        // restaurar = excecoes vazias
        assertThat(PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, VAZIA, VAZIA, FLAGS)
                .permite(Capacidade.RESUMO_IA_SOLICITAR)).isTrue();
    }

    @Test
    @DisplayName("excecao salva fora do teto (papel mudou, catalogo mudou) nunca amplia nada")
    void excecaoNaoSuperaTeto() {
        ConfiguracaoDePermissoes adulterada = new ConfiguracaoDePermissoes(
                Map.of(Modulo.EQUIPE, NivelDeAcesso.GERENCIAR),
                Map.of(Capacidade.TAGS_CRIAR, true, Capacidade.EQUIPE_PERFIS, true));
        PermissoesEfetivas efetivas = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, VAZIA, adulterada, FLAGS);
        assertThat(efetivas.permite(Capacidade.TAGS_CRIAR)).isFalse();
        assertThat(efetivas.estado(Capacidade.TAGS_CRIAR).motivo()).isEqualTo(Motivo.TETO_DO_PAPEL);
        assertThat(efetivas.permite(Capacidade.EQUIPE_PERFIS)).isFalse();
        assertThat(efetivas.nivel(Modulo.EQUIPE)).isEqualTo(NivelDeAcesso.SEM_ACESSO);
    }

    @Test
    @DisplayName("nivel do modulo e limite: acao acima dele fica bloqueada mesmo com interruptor ligado")
    void nivelELimite() {
        ConfiguracaoDePermissoes somenteVer = new ConfiguracaoDePermissoes(
                Map.of(Modulo.MENSAGENS_RAPIDAS, NivelDeAcesso.VER), Map.of());
        PermissoesEfetivas efetivas = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, somenteVer, VAZIA, FLAGS);
        assertThat(efetivas.permite(Capacidade.MENSAGENS_RAPIDAS_USAR)).isTrue();
        assertThat(efetivas.estado(Capacidade.MENSAGENS_RAPIDAS_CRIAR).motivo()).isEqualTo(Motivo.NIVEL_DO_MODULO);
    }

    @Test
    @DisplayName("dependencia negada bloqueia a dependente, com motivo explicito")
    void dependencia() {
        ConfiguracaoDePermissoes semUsar = acoes(Map.of(Capacidade.MENSAGENS_RAPIDAS_USAR, false));
        EstadoDaCapacidade criar = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, semUsar, VAZIA, FLAGS)
                .estado(Capacidade.MENSAGENS_RAPIDAS_CRIAR);
        assertThat(criar.permitido()).isFalse();
        assertThat(criar.motivo()).isEqualTo(Motivo.DEPENDENCIA);
    }

    @Test
    @DisplayName("modulo com flag desligada nao concede nada, nem para GESTOR")
    void flagDesligada() {
        PermissoesEfetivas gestor = PoliticaDePermissoes.calcular(PapelUsuario.GESTOR, VAZIA, VAZIA, Set.of());
        assertThat(gestor.permite(Capacidade.DASHBOARD_VER)).isFalse();
        assertThat(gestor.estado(Capacidade.DASHBOARD_VER).motivo()).isEqualTo(Motivo.FLAG_DESLIGADA);
    }

    @Test
    @DisplayName("validacao recusa estrutural, fora do teto, flag desligada, nivel fora do limite e incoerencia — tudo junto")
    void validacaoAcumulaViolacoes() {
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        niveis.put(Modulo.TAGS, NivelDeAcesso.GERENCIAR);
        niveis.put(Modulo.LEMBRETES, NivelDeAcesso.VER);
        niveis.put(Modulo.DASHBOARD, NivelDeAcesso.SEM_ACESSO);
        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        acoes.put(Capacidade.ATENDIMENTOS_VER, true);
        acoes.put(Capacidade.TAGS_CRIAR, true);
        acoes.put(Capacidade.DASHBOARD_VER, true);
        acoes.put(Capacidade.LEMBRETES_CRIAR, true);

        assertThatThrownBy(() -> PoliticaDePermissoes.validarPerfil(
                        PapelUsuario.ATENDENTE, new ConfiguracaoDePermissoes(niveis, acoes), Set.of()))
                .isInstanceOfSatisfying(PermissaoInvalidaException.class, e -> assertThat(e.violacoes())
                        .extracting(Violacao::codigo)
                        .contains(Violacao.Codigo.NIVEL_FORA_DO_LIMITE, Violacao.Codigo.ESTRUTURAL,
                                Violacao.Codigo.FORA_DO_TETO, Violacao.Codigo.FLAG_DESLIGADA,
                                Violacao.Codigo.NIVEL_INSUFICIENTE));
    }

    @Test
    @DisplayName("perfil fixo nao e configuravel; identificador desconhecido e recusado na leitura do payload")
    void fixoEDesconhecido() {
        assertThatThrownBy(() -> PoliticaDePermissoes.validarPerfil(PapelUsuario.GESTOR, VAZIA, FLAGS))
                .isInstanceOfSatisfying(PermissaoInvalidaException.class,
                        e -> assertThat(e.violacoes().get(0).codigo()).isEqualTo(Violacao.Codigo.PERFIL_FIXO));
        assertThatThrownBy(() -> ConfiguracaoDePermissoes.interpretar(
                        Map.of("tags", "GERENCIAR", "banco_arquivos", "VER"),
                        Map.of("atendimentos.assumir_de_colega", true)))
                .isInstanceOfSatisfying(PermissaoInvalidaException.class, e -> assertThat(e.violacoes())
                        .extracting(Violacao::codigo)
                        .containsOnly(Violacao.Codigo.DESCONHECIDA));
    }

    @Test
    @DisplayName("contagem N de M usa o catalogo real, sem numero fixo")
    void contagem() {
        PermissoesEfetivas atendente = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, VAZIA, VAZIA, FLAGS);
        long configuraveis = java.util.Arrays.stream(Capacidade.values())
                .filter(c -> !c.estrutural() && c.noTetoDe(PapelUsuario.ATENDENTE)).count();
        assertThat(atendente.totalConfiguravel()).isEqualTo(configuraveis);
        assertThat(atendente.totalPermitido()).isEqualTo(configuraveis);
    }
}
