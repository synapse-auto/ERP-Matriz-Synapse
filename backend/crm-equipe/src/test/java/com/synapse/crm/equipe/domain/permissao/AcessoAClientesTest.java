package com.synapse.crm.equipe.domain.permissao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class AcessoAClientesTest {
    private static final Set<String> FLAGS = Set.of("dashboard", "campanhas", "cancelamentos");
    private static final ConfiguracaoDePermissoes VAZIA = ConfiguracaoDePermissoes.vazia();

    private static Capacidade acesso() {
        return Capacidade.porId("clientes.acessar").orElseThrow();
    }

    @ParameterizedTest
    @EnumSource(PapelUsuario.class)
    void preservaLeituraAtualSemNegacaoExplicita(PapelUsuario papel) {
        var efetivas = PoliticaDePermissoes.calcular(papel, VAZIA, VAZIA, FLAGS);
        assertThat(efetivas.permite(acesso())).isTrue();
        assertThat(efetivas.permite(Capacidade.ATENDIMENTOS_VER)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "OPERADOR", "SUBGESTOR"})
    void negacaoBloqueiaLeituraEAcoesMesmoComAcaoLigada(PapelUsuario papel) {
        var bloqueio = new ConfiguracaoDePermissoes(Map.of(), Map.of(acesso(), false));
        PoliticaDePermissoes.validarPerfil(papel, bloqueio, FLAGS);
        var efetivas = PoliticaDePermissoes.calcular(papel, bloqueio, VAZIA, FLAGS);
        for (Capacidade capacidade : Set.of(Capacidade.ATENDIMENTOS_VER, Capacidade.ATENDIMENTOS_RESPONDER,
                Capacidade.ATENDIMENTOS_COLABORAR, Capacidade.CONTATOS_EDITAR, Capacidade.RESUMO_IA_VER,
                Capacidade.MENSAGENS_PROGRAMADAS_VER, Capacidade.LEMBRETES_VER)) {
            assertThat(efetivas.permite(capacidade)).as(capacidade.id()).isFalse();
            assertThat(efetivas.estado(capacidade).motivo()).isEqualTo(EstadoDaCapacidade.Motivo.DEPENDENCIA);
        }
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "OPERADOR", "SUBGESTOR"})
    void excecaoSeguePrecedenciaOficialSemAmpliarCarteira(PapelUsuario papel) {
        var negacao = new ConfiguracaoDePermissoes(Map.of(), Map.of(acesso(), false));
        var concessao = new ConfiguracaoDePermissoes(Map.of(), Map.of(acesso(), true));
        assertThat(PoliticaDePermissoes.calcular(papel, concessao, negacao, FLAGS).permite(acesso())).isFalse();
        var permitido = PoliticaDePermissoes.calcular(papel, negacao, concessao, FLAGS);
        assertThat(permitido.permite(acesso())).isTrue();
        assertThat(Capacidade.ATENDIMENTOS_VER.alcancePara(papel))
                .isEqualTo(papel.enxergaTodosOsLeads() ? Capacidade.Alcance.TODOS : Capacidade.Alcance.MEUS);
    }
}
