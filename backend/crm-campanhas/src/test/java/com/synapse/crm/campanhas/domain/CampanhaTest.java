package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampanhaTest {

    private static final Instant AGORA = Instant.parse("2026-10-05T12:00:00Z");
    private static final int TETO = 200;

    @Test
    @DisplayName("rascunho valido nasce RASCUNHO, sem inicio e com contadores zerados")
    void rascunho_valido() {
        Campanha campanha = rascunho("MARKETING", 100, null);

        assertThat(campanha.status()).isEqualTo(StatusDaCampanha.RASCUNHO);
        assertThat(campanha.iniciadaEm()).isNull();
        assertThat(campanha.contadores()).isEqualTo(Campanha.Contadores.zerados());
    }

    @Test
    @DisplayName("limite diario acima do teto da instancia e recusado")
    void limiteAcimaDoTeto_recusado() {
        assertThatThrownBy(() -> rascunho("MARKETING", TETO + 1, null))
                .isInstanceOf(CampanhaInvalidaException.class)
                .hasMessageContaining(String.valueOf(TETO));
    }

    @Test
    @DisplayName("template de autenticacao nao serve para campanha")
    void templateDeAutenticacao_recusado() {
        assertThatThrownBy(() -> rascunho("AUTENTICACAO", 100, null)).isInstanceOf(CampanhaInvalidaException.class);
    }

    @Test
    @DisplayName("mapeamento que nao cobre as variaveis do template e recusado")
    void mapeamentoIncompleto_recusado() {
        assertThatThrownBy(() -> Campanha.rascunho(
                        UUID.randomUUID(),
                        "Natal",
                        template("MARKETING", 2),
                        mapeamento(1),
                        FiltroDePublico.agendaInteira(),
                        100,
                        TETO,
                        janela(),
                        20,
                        null,
                        null,
                        UUID.randomUUID(),
                        AGORA))
                .isInstanceOf(CampanhaInvalidaException.class)
                .hasMessageContaining("2 variavel");
    }

    @Test
    @DisplayName("iniciar sem agenda comeca ja; com data no futuro fica AGENDADA e comeca na hora")
    void iniciar_comEsemAgenda() {
        Campanha agora = rascunho("MARKETING", 100, null).iniciar(AGORA);
        assertThat(agora.status()).isEqualTo(StatusDaCampanha.EM_ANDAMENTO);
        assertThat(agora.iniciadaEm()).isEqualTo(AGORA);

        Campanha agendada = rascunho("MARKETING", 100, AGORA.plusSeconds(3600)).iniciar(AGORA);
        assertThat(agendada.status()).isEqualTo(StatusDaCampanha.AGENDADA);
        assertThat(agendada.iniciadaEm()).isNull();

        Campanha comecou = agendada.comecarAgendada(AGORA.plusSeconds(3600));
        assertThat(comecou.status()).isEqualTo(StatusDaCampanha.EM_ANDAMENTO);
        assertThat(comecou.iniciadaEm()).isEqualTo(AGORA.plusSeconds(3600));
    }

    @Test
    @DisplayName("pausar, retomar e cancelar seguem a tabela de transicoes")
    void pausarRetomarCancelar() {
        Campanha emAndamento = rascunho("MARKETING", 100, null).iniciar(AGORA);

        Campanha pausada = emAndamento.pausar(AGORA.plusSeconds(60));
        assertThat(pausada.status()).isEqualTo(StatusDaCampanha.PAUSADA);
        assertThat(pausada.pausadaEm()).isEqualTo(AGORA.plusSeconds(60));

        Campanha retomada = pausada.retomar(AGORA.plusSeconds(120));
        assertThat(retomada.status()).isEqualTo(StatusDaCampanha.EM_ANDAMENTO);
        assertThat(retomada.pausadaEm()).isNull();
        assertThat(retomada.iniciadaEm()).isEqualTo(AGORA);

        assertThat(retomada.cancelar(AGORA).status()).isEqualTo(StatusDaCampanha.CANCELADA);
    }

    @Test
    @DisplayName("campanha terminada nao volta: retomar, pausar e cancelar lancam")
    void terminada_naoVolta() {
        Campanha cancelada = rascunho("MARKETING", 100, null).iniciar(AGORA).cancelar(AGORA);
        Campanha concluida = rascunho("MARKETING", 100, null).iniciar(AGORA).concluir(AGORA);

        assertThatThrownBy(() -> cancelada.retomar(AGORA)).isInstanceOf(TransicaoDeStatusInvalidaException.class);
        assertThatThrownBy(() -> concluida.pausar(AGORA)).isInstanceOf(TransicaoDeStatusInvalidaException.class);
        assertThatThrownBy(() -> concluida.cancelar(AGORA)).isInstanceOf(TransicaoDeStatusInvalidaException.class);
    }

    @Test
    @DisplayName("pausa automatica guarda o motivo, exige retomada explicita e a retomada o limpa")
    void pausaAutomatica() {
        Campanha pausada = rascunho("MARKETING", 100, null)
                .iniciar(AGORA)
                .pausarAutomaticamente(AGORA, "TAXA_DE_FALHA:35");

        assertThat(pausada.status()).isEqualTo(StatusDaCampanha.PAUSADA_AUTOMATICAMENTE);
        assertThat(pausada.motivoDePausa()).isEqualTo("TAXA_DE_FALHA:35");
        assertThat(pausada.podeEnviarAgora(true)).isFalse();
        assertThat(pausada.retomar(AGORA).motivoDePausa()).isNull();
    }

    @Test
    @DisplayName("so EM_ANDAMENTO envia, e o interruptor global ou o da campanha o impedem")
    void podeEnviarAgora() {
        Campanha emAndamento = rascunho("MARKETING", 100, null).iniciar(AGORA);

        assertThat(emAndamento.podeEnviarAgora(true)).isTrue();
        assertThat(emAndamento.podeEnviarAgora(false)).isFalse();
        assertThat(emAndamento.comDesligada(true).podeEnviarAgora(true)).isFalse();
        assertThat(rascunho("MARKETING", 100, null).podeEnviarAgora(true)).isFalse();
    }

    @Test
    @DisplayName("alterar o limite respeita o teto, vale em campanha em andamento e nao em campanha terminada")
    void alterarLimite() {
        Campanha emAndamento = rascunho("MARKETING", 100, null).iniciar(AGORA);

        assertThat(emAndamento.comLimiteDiario(150, TETO).limiteDiario()).isEqualTo(150);
        assertThatThrownBy(() -> emAndamento.comLimiteDiario(TETO + 1, TETO)).isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> emAndamento.comLimiteDiario(0, TETO)).isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> emAndamento.concluir(AGORA).comLimiteDiario(10, TETO))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
    }

    @Test
    @DisplayName("o teto da rampa nao pode ficar abaixo do limite diario")
    void rampaComTetoAbaixoDoLimite_recusada() {
        assertThatThrownBy(() -> rascunho("MARKETING", 100, null, new PlanoDeLimite.Rampa(10, 50)))
                .isInstanceOf(CampanhaInvalidaException.class);
    }

    private static Campanha rascunho(String categoria, int limite, Instant agendadaPara) {
        return rascunho(categoria, limite, agendadaPara, null);
    }

    private static Campanha rascunho(String categoria, int limite, Instant agendadaPara, PlanoDeLimite.Rampa rampa) {
        return Campanha.rascunho(
                UUID.randomUUID(),
                "Natal",
                template(categoria, 1),
                mapeamento(1),
                FiltroDePublico.agendaInteira(),
                limite,
                TETO,
                janela(),
                20,
                rampa,
                agendadaPara,
                UUID.randomUUID(),
                AGORA);
    }

    private static Campanha.TemplateSnapshot template(String categoria, int parametros) {
        return new Campanha.TemplateSnapshot("id-1", "natal_2026", "pt_BR", categoria, "Ola {{1}}", parametros);
    }

    private static MapeamentoDeVariaveis mapeamento(int quantidade) {
        return new MapeamentoDeVariaveis(java.util.stream.IntStream.rangeClosed(1, quantidade)
                .mapToObj(posicao -> new MapeamentoDeVariaveis.Variavel(posicao, CampoDoLead.PRIMEIRO_NOME, "cliente"))
                .toList());
    }

    private static JanelaDeEnvio janela() {
        return new JanelaDeEnvio(LocalTime.of(9, 0), LocalTime.of(18, 0), DiasDaSemana.DIAS_UTEIS);
    }

    @SuppressWarnings("unused")
    private static List<UUID> semTags() {
        return List.of();
    }
}
