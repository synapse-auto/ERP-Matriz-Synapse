package com.synapse.crm.relatorios.domain.cancelamentos;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class FiltroCancelamentosTest {
    FiltroCancelamentos filtro(LocalDate inicio, LocalDate fim, int pagina, int tamanho, String nome) {
        return FiltroCancelamentos.criar(FiltroCancelamentos.BaseTemporal.REGISTRO, inicio, fim,
                "  Profissional ", " ", FiltroCancelamentos.Descricao.SEM_DESCRICAO, nome,
                pagina, tamanho, ZoneId.of("America/Sao_Paulo"), 100, 90, 100, 200);
    }
    @Test void datasSaoLocaisInclusivasSemConfundirUtc() {
        var f = filtro(LocalDate.of(2026,10,1), LocalDate.of(2026,10,2), 0, 25, " Contato ");
        assertThat(f.inicio()).hasToString("2026-10-01T03:00:00Z");
        assertThat(f.fimExclusivo()).hasToString("2026-10-03T03:00:00Z");
        assertThat(f.profissional()).isEqualTo("Profissional");
        assertThat(f.tipo()).isNull(); assertThat(f.nome()).isEqualTo("Contato");
    }
    @Test void diaComMudancaDeFusoNaoAssumeVinteQuatroHoras() {
        var f = filtro(LocalDate.of(2018,11,4), LocalDate.of(2018,11,4), 0, 25, null);
        assertThat(f.inicio()).hasToString("2018-11-04T03:00:00Z");
        assertThat(f.fimExclusivo()).hasToString("2018-11-05T02:00:00Z");
    }
    @Test void recusaHistoricoIlimitadoPaginaETamanhoInvalidosSemEcoarNome() {
        LocalDate dia = LocalDate.of(2026,10,1);
        for (int tamanho : new int[] {0, 101}) assertThatThrownBy(() -> filtro(dia,dia,0,tamanho,null))
                .isInstanceOf(FiltroCancelamentosInvalidoException.class);
        for (int pagina : new int[] {-1,100}) assertThatThrownBy(() -> filtro(dia,dia,pagina,25,null))
                .isInstanceOf(FiltroCancelamentosInvalidoException.class);
        assertThatThrownBy(() -> filtro(dia,dia.plusDays(90),0,25,null)).isInstanceOf(FiltroCancelamentosInvalidoException.class);
        assertThatThrownBy(() -> filtro(dia,dia.minusDays(1),0,25,null)).isInstanceOf(FiltroCancelamentosInvalidoException.class);
        assertThatThrownBy(() -> filtro(dia,dia,0,25,"SENSIVEL".repeat(40)))
                .isInstanceOf(FiltroCancelamentosInvalidoException.class).hasMessageNotContaining("SENSIVEL");
    }
    @Test void descricaoNulaEVaziaNaoSaoMotivoInformado() {
        assertThat(new CancelamentoAgendamento("1",null,null,null,null,null," \n ").temDescricao()).isFalse();
        assertThat(new CancelamentoAgendamento("2",null,null,null,null,null,null).temDescricao()).isFalse();
        assertThat(new CancelamentoAgendamento("3",null,null,null,null,null,"Motivo\nsegunda linha").temDescricao()).isTrue();
    }
}
