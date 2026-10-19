package com.synapse.crm.relatorios.domain.cancelamentos;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NovoCancelamentoTest {
    @Test void normalizaAusenciaMicrosSemInventarCancelamento() {
        var e=new NovoCancelamento("origem","evento-1","agenda-1",null,null,
                Instant.parse("2036-10-01T12:00:00.123456789Z"),null," Contato ",null,"  "," \n\t");
        assertThat(e.descricao()).isNull();assertThat(e.tipoAgendamento()).isNull();
        assertThat(e.canceladoEm()).isNull();assertThat(e.contatoNome()).isEqualTo("Contato");
        assertThat(e.agendadoPara()).hasToString("2036-10-01T12:00:00.123456Z");
    }
    @Test void rejeitaParIncompletoControlesELimites() {
        assertThatThrownBy(()->new NovoCancelamento("origem","evento","agenda",UUID.randomUUID(),null,Instant.EPOCH,null,null,null,null,null)).isInstanceOf(RegistroCancelamentoInvalidoException.class);
        for(String invalido:new String[]{""," ","segredo\n","a".repeat(201),"nome com espaco"})
            assertThatThrownBy(()->NovoCancelamento.identificador(invalido,200)).isInstanceOf(RegistroCancelamentoInvalidoException.class);
        assertThatThrownBy(()->new NovoCancelamento("origem","evento","agenda",null,null,Instant.EPOCH,null,null,null,null,"x".repeat(2001))).isInstanceOf(RegistroCancelamentoInvalidoException.class);
    }
}
