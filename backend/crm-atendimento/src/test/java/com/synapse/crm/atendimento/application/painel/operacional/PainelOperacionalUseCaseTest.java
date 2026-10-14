package com.synapse.crm.atendimento.application.painel.operacional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class PainelOperacionalUseCaseTest {
    private final PainelOperacionalRepositorio repositorio = mock(PainelOperacionalRepositorio.class);
    private final UsuarioContext usuarios = mock(UsuarioContext.class);
    private final Instant agora = Instant.parse("2026-10-14T02:30:00Z");
    private final UUID usuario = UUID.randomUUID();

    @Test
    void diaAtualUsaFusoDaInstanciaEProximaTransicaoSemPolling() {
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.GESTOR, false));
        when(repositorio.limiteMinutos()).thenReturn(27);
        var futuro = agora.plusSeconds(60);
        var linha = new PainelOperacional.Linha(usuario, "Sintético", null, "GESTOR", "ONLINE", false, true,
            futuro, new PainelOperacional.Contagens(0,0,0,0,0,0,0,0,null));
        when(repositorio.agregar(any(),eq(usuario),eq(agora),any(),any(),eq(27),any())).thenReturn(List.of(linha));
        var filtro = new FiltroOperacional(null,null,false,null,null,null);
        var caso = new ObterPainelOperacionalUseCase(repositorio,usuarios,Clock.fixed(agora,ZoneOffset.UTC),"America/Sao_Paulo");
        var resultado = caso.executar(filtro);
        assertThat(resultado.finalizadosInicio()).isEqualTo(Instant.parse("2026-10-13T03:00:00Z"));
        assertThat(resultado.finalizadosFim()).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
        assertThat(resultado.proximaTransicaoEm()).isEqualTo(futuro);
        verify(repositorio,times(1)).agregar(any(),eq(usuario),eq(agora),any(),any(),eq(27),eq(filtro));
        // A mesma fila já cruzada não vira um timer vencido em loop; o próximo limite é a meia-noite.
        when(repositorio.agregar(any(),any(),any(),any(),any(),anyInt(),any())).thenReturn(List.of(
            new PainelOperacional.Linha(usuario,"Sintético",null,"GESTOR","ONLINE",false,true,
                agora.minusSeconds(1),linha.contagens())));
        assertThat(caso.executar(filtro).proximaTransicaoEm()).isEqualTo(resultado.finalizadosFim());
    }

    @Test
    void naoTransformaFalhaDoBancoEmContagemZero() {
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(usuario,PapelUsuario.GESTOR,false));
        when(repositorio.limiteMinutos()).thenThrow(new IllegalStateException("Banco indisponível"));
        var caso = new ObterPainelOperacionalUseCase(repositorio,usuarios,Clock.fixed(agora,ZoneOffset.UTC),"UTC");
        assertThatThrownBy(() -> caso.executar(new FiltroOperacional(null,null,false,null,null,null)))
            .isInstanceOf(IllegalStateException.class);
        verify(repositorio,never()).agregar(any(),any(),any(),any(),any(),anyInt(),any());
    }

    @Test
    void filtrosIncompativeisSaoRecusadosAntesDaConsulta() {
        assertThatThrownBy(() -> new FiltroOperacional(null,usuario,true,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroOperacional(FiltroOperacional.Fila.FINALIZADOS,null,false,null,null,FiltroOperacional.Sinal.NOVAS)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new FiltroOperacional(null,null,false,"  busca  ",null,null).busca()).isEqualTo("busca");
        verifyNoInteractions(repositorio);
    }
}
