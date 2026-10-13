package com.synapse.crm.equipe.application.setor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synapse.crm.equipe.application.permissao.AlvoDePermissaoNaoEncontradoException;
import com.synapse.crm.equipe.application.usuario.AlcadaSobreIntegrantes;
import com.synapse.crm.equipe.application.usuario.EquipeRepositorio;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException.Codigo;
import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.NomeDeSetorInvalidoException;
import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.equipe.domain.setor.SetorInativoException;
import com.synapse.crm.equipe.domain.setor.SetorNaoEncontradoException;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

class SetorUseCasesTest {

    private final HabilitacaoDeSetores habilitacao = Mockito.mock(HabilitacaoDeSetores.class);
    private final SetorRepositorio setores = Mockito.mock(SetorRepositorio.class);
    private final EquipeRepositorio equipe = Mockito.mock(EquipeRepositorio.class);
    private final AlcadaSobreIntegrantes alcada = Mockito.mock(AlcadaSobreIntegrantes.class);

    private final UUID gestor = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();
    private final UUID vendas = UUID.randomUUID();

    private final ListarSetoresUseCase listar = new ListarSetoresUseCase(habilitacao, setores);
    private final CriarSetorUseCase criar = new CriarSetorUseCase(habilitacao, setores);
    private final AtualizarSetorUseCase atualizar = new AtualizarSetorUseCase(habilitacao, setores);
    private final AtribuirSetorAoUsuarioUseCase atribuir =
            new AtribuirSetorAoUsuarioUseCase(habilitacao, setores, equipe, alcada);

    @BeforeEach
    void ligarCapacidade() {
        // Cada use case chama exigir(); com o mock puro o default do interface nao roda, entao o encaminhamos.
        Mockito.doCallRealMethod().when(habilitacao).exigir();
        when(habilitacao.habilitada()).thenReturn(true);
        when(alcada.atual()).thenReturn(new UsuarioAutenticado(gestor, PapelUsuario.GESTOR, false));
    }

    private Usuario atendente(UUID id) {
        return new Usuario(
                id, "Ana", "ana@example.invalid", "hash", PapelUsuario.ATENDENTE,
                StatusPresenca.ONLINE, true, true, null, null, null, null);
    }

    // --- capacidade desligada: nada acontece, nada e lido -------------------------------------------------

    @Test
    void comACapacidadeDesligadaNenhumUseCaseTocaOBanco() {
        when(habilitacao.habilitada()).thenReturn(false);

        assertThatThrownBy(listar::executar).isInstanceOf(SetoresDesligadosException.class);
        assertThatThrownBy(() -> criar.executar("Vendas", true)).isInstanceOf(SetoresDesligadosException.class);
        assertThatThrownBy(() -> atualizar.executar(vendas, "Vendas", true, true))
                .isInstanceOf(SetoresDesligadosException.class);
        assertThatThrownBy(() -> atribuir.executar(ana, vendas)).isInstanceOf(SetoresDesligadosException.class);

        verifyNoInteractions(setores, equipe);
    }

    // --- nome ---------------------------------------------------------------------------------------------

    @Test
    void nomeVazioOuSoComEspacosERecusadoAntesDoBanco() {
        assertThatThrownBy(() -> criar.executar("   ", true)).isInstanceOf(NomeDeSetorInvalidoException.class);
        assertThatThrownBy(() -> criar.executar(null, true)).isInstanceOf(NomeDeSetorInvalidoException.class);
        verify(setores, never()).criar(anyString(), anyBoolean());
    }

    @Test
    void nomeLongoDemaisERecusado() {
        assertThatThrownBy(() -> criar.executar("x".repeat(Setor.TAMANHO_MAXIMO_DO_NOME + 1), true))
                .isInstanceOf(NomeDeSetorInvalidoException.class);
    }

    @Test
    void criaComONomeSemEspacosNasPontas() {
        Setor criado = new Setor(vendas, "Vendas", false, true, 0);
        when(setores.criar("Vendas", false)).thenReturn(criado);

        assertThat(criar.executar("  Vendas ", false)).isEqualTo(criado);
    }

    @Test
    void atualizarSetorInexistenteViraNaoEncontrado() {
        when(setores.atualizar(vendas, "Vendas", true, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atualizar.executar(vendas, "Vendas", true, true))
                .isInstanceOf(SetorNaoEncontradoException.class);
    }

    @Test
    void listarDevolveSetoresEAtribuicoes() {
        Setor setor = new Setor(vendas, "Vendas", true, true, 1);
        AtribuicaoDeSetor atribuicao = new AtribuicaoDeSetor(ana, vendas);
        when(setores.listar()).thenReturn(List.of(setor));
        when(setores.listarAtribuicoes()).thenReturn(List.of(atribuicao));

        VisaoDeSetores visao = listar.executar();

        assertThat(visao.setores()).containsExactly(setor);
        assertThat(visao.atribuicoes()).containsExactly(atribuicao);
    }

    // --- atribuicao ---------------------------------------------------------------------------------------

    @Test
    void atribuiRespeitandoAAlcadaSobreOAlvo() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.of(atendente(ana)));
        when(setores.porId(vendas)).thenReturn(Optional.of(new Setor(vendas, "Vendas", true, true, 0)));

        AtribuicaoDeSetor resultado = atribuir.executar(ana, vendas);

        assertThat(resultado).isEqualTo(new AtribuicaoDeSetor(ana, vendas));
        verify(alcada).exigirAlvo(ana, PapelUsuario.ATENDENTE);
        verify(setores).atribuir(ana, vendas, gestor);
    }

    @Test
    void foraDaAlcadaNaoGravaNada() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.of(atendente(ana)));
        doThrow(new ConcessaoNegadaException(Codigo.ALVO_FORA_DA_ALCADA)).when(alcada).exigirAlvo(any(), any());

        assertThatThrownBy(() -> atribuir.executar(ana, vendas)).isInstanceOf(ConcessaoNegadaException.class);

        verify(setores, never()).atribuir(any(), any(), any());
        verify(setores, never()).remover(any());
    }

    @Test
    void usuarioInexistenteViraNaoEncontrado() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atribuir.executar(ana, vendas))
                .isInstanceOf(AlvoDePermissaoNaoEncontradoException.class);
        verify(setores, never()).atribuir(any(), any(), any());
    }

    @Test
    void setorInexistenteNaoAtribui() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.of(atendente(ana)));
        when(setores.porId(vendas)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> atribuir.executar(ana, vendas)).isInstanceOf(SetorNaoEncontradoException.class);
        verify(setores, never()).atribuir(any(), any(), any());
    }

    @Test
    void setorInativoNaoAceitaNovosIntegrantes() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.of(atendente(ana)));
        when(setores.porId(vendas)).thenReturn(Optional.of(new Setor(vendas, "Vendas", true, false, 0)));

        assertThatThrownBy(() -> atribuir.executar(ana, vendas)).isInstanceOf(SetorInativoException.class);
        verify(setores, never()).atribuir(any(), any(), any());
    }

    @Test
    void setorNuloTiraOIntegranteDeQualquerSetor() {
        when(equipe.travarParaAlteracao(ana)).thenReturn(Optional.of(atendente(ana)));

        AtribuicaoDeSetor resultado = atribuir.executar(ana, null);

        assertThat(resultado).isEqualTo(new AtribuicaoDeSetor(ana, null));
        verify(setores).remover(ana);
        verify(setores, never()).atribuir(any(), any(), any());
    }
}
