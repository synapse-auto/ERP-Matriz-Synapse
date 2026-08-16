package com.synapse.crm.app.saude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.synapse.crm.app.saude.infrastructure.DiagnosticoCanalNoStartup;
import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.ConfiguracaoCanalAtivo;

@ExtendWith(OutputCaptureExtension.class)
class DiagnosticoCanalNoStartupTest {

    @Test
    void canalAtivoSemPhoneNumberId_gritaComAcaoCorretiva(CapturedOutput log) {
        CanalCredencialAtivaRepositorio credenciais = mock(CanalCredencialAtivaRepositorio.class);
        when(credenciais.carregarConfiguracao())
                .thenReturn(new ConfiguracaoCanalAtivo(1, 1, Set.of()));

        new DiagnosticoCanalNoStartup(credenciais).diagnosticar();

        assertThat(log.getOut())
                .contains("ERROR")
                .contains("canal(is) ativo(s) sem phone_number_id")
                .contains("Execute o provisionamento")
                .contains("canal_credencial.identificador_externo");
    }
}
