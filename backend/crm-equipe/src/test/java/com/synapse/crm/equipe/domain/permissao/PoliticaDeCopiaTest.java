package com.synapse.crm.equipe.domain.permissao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class PoliticaDeCopiaTest {

    private static final Set<String> FLAGS = Set.of("dashboard");
    private static final ConfiguracaoDePermissoes VAZIA = ConfiguracaoDePermissoes.vazia();

    private static PermissoesEfetivas de(PapelUsuario papel, ConfiguracaoDePermissoes excecoes) {
        return PoliticaDePermissoes.calcular(papel, VAZIA, excecoes, FLAGS);
    }

    private static PoliticaDeConcessao.Ator gestor() {
        return new PoliticaDeConcessao.Ator(UUID.randomUUID(), PapelUsuario.GESTOR, de(PapelUsuario.GESTOR, VAZIA));
    }

    @Test
    @DisplayName("copia de GESTOR (acesso fixo) e recusada: acesso total nao e lista que se transfira")
    void origemFixaRecusada() {
        assertThatThrownBy(() -> PoliticaDeCopia.paraUsuario(de(PapelUsuario.GESTOR, VAZIA), PapelUsuario.ATENDENTE,
                        VAZIA, VAZIA, gestor(), FLAGS))
                .isInstanceOf(PermissaoInvalidaException.class);
    }

    @Test
    @DisplayName("copia de SUBGESTOR para ATENDENTE nao transmite o que esta acima do teto do atendente")
    void recortaPeloTetoDoDestino() {
        PoliticaDeCopia.Resultado r = PoliticaDeCopia.paraUsuario(de(PapelUsuario.SUBGESTOR, VAZIA),
                PapelUsuario.ATENDENTE, VAZIA, VAZIA, gestor(), FLAGS);
        assertThat(r.impedidos()).extracting(PoliticaDeCopia.Impedimento::chave)
                .contains("tags.criar", "dashboard.ver", "equipe.ver", "modulo:equipe");
        PermissoesEfetivas depois = PoliticaDePermissoes.calcular(PapelUsuario.ATENDENTE, VAZIA, r.configuracao(), FLAGS);
        assertThat(depois.permite(Capacidade.TAGS_CRIAR)).isFalse();
        assertThat(depois.permite(Capacidade.EQUIPE_VER)).isFalse();
        PoliticaDePermissoes.validarExcecoes(PapelUsuario.ATENDENTE, VAZIA, r.configuracao(), FLAGS);
    }

    @Test
    @DisplayName("copia entre atendentes reproduz as negacoes da origem como excecoes minimas")
    void copiaEntreAtendentes() {
        ConfiguracaoDePermissoes daOrigem = new ConfiguracaoDePermissoes(Map.of(),
                Map.of(Capacidade.RESUMO_IA_SOLICITAR, false, Capacidade.TAGS_APLICAR, false));
        PoliticaDeCopia.Resultado r = PoliticaDeCopia.paraUsuario(de(PapelUsuario.ATENDENTE, daOrigem),
                PapelUsuario.ATENDENTE, VAZIA, VAZIA, gestor(), FLAGS);
        assertThat(r.configuracao().acoes()).containsExactlyInAnyOrderEntriesOf(daOrigem.acoes());
        assertThat(r.impedidos()).isEmpty();
    }
}
