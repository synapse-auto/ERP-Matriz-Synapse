package com.synapse.crm.app.mensagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

class CompositorDeEnvioParaCanalConfiguradoTest {

    private static final UUID MENSAGEM_ID = UUID.randomUUID();
    private static final Instant ENVIADO_EM = Instant.parse("2026-08-25T12:00:00Z");

    private final OrigemDeMensagemRepositorio origens = mock(OrigemDeMensagemRepositorio.class);
    private final ConfiguracaoDeInstanciaResources recursos = mock(ConfiguracaoDeInstanciaResources.class);
    private CompositorDeEnvioParaCanalConfigurado compositor;

    @BeforeEach
    void preparar() throws Exception {
        var textos = new ObjectMapper().readTree(
                "{\"atendimentos\":{\"mensagem\":{\"prefixoAtendente\":\"*{nome}:*\\n\\n{mensagem}\"}}}");
        when(recursos.textos()).thenReturn(textos);
        compositor = new CompositorDeEnvioParaCanalConfigurado(origens, recursos);
    }

    @Test
    void textoDeAtendenteRecebePrefixoSemAlterarConteudoDaOutbox() {
        configurarOrigem(Remetente.atendente(UUID.randomUUID()), "Daiane");
        var pendente = pendente(new ConteudoDeEnvio.MensagemLivre("Olá!"));

        CanalGateway.Envio envio = compositor.montar(pendente);

        assertThat(((ConteudoDeEnvio.MensagemLivre) envio.conteudo()).texto())
                .isEqualTo("*Daiane:*\n\nOlá!");
        assertThat(((ConteudoDeEnvio.MensagemLivre) pendente.conteudo()).texto()).isEqualTo("Olá!");
        verify(origens).buscar(MENSAGEM_ID, ENVIADO_EM);
    }

    @Test
    void textoDaIaNaoRecebePrefixo() {
        configurarOrigem(Remetente.ia(), null);
        var conteudo = new ConteudoDeEnvio.MensagemLivre("resposta automática");

        assertThat(compositor.montar(pendente(conteudo)).conteudo()).isSameAs(conteudo);
    }

    @Test
    void templateNuncaRecebePrefixoNemConsultaAutoria() {
        var conteudo = ConteudoDeEnvio.MensagemTemplate.de("reativacao", "pt_BR", "Cliente");

        assertThat(compositor.montar(pendente(conteudo)).conteudo()).isSameAs(conteudo);
        verifyNoInteractions(origens);
    }

    @Test
    void legendaDeMidiaRecebePrefixo() {
        configurarOrigem(Remetente.atendente(UUID.randomUUID()), "Daiane");
        var conteudo = new ConteudoDeEnvio.MensagemMidia(
                TipoMensagem.IMAGEM, "storage/imagem", "{\"mimetype\":\"image/png\"}", "Veja o orçamento");

        var saida = (ConteudoDeEnvio.MensagemMidia) compositor.montar(pendente(conteudo)).conteudo();

        assertThat(saida.legenda()).isEqualTo("*Daiane:*\n\nVeja o orçamento");
        assertThat(saida.referenciaStorage()).isEqualTo(conteudo.referenciaStorage());
    }

    @Test
    void midiaSemLegendaContinuaSemLegenda() {
        configurarOrigem(Remetente.atendente(UUID.randomUUID()), "Daiane");
        var conteudo = new ConteudoDeEnvio.MensagemMidia(TipoMensagem.DOCUMENTO, "storage/doc", null, null);

        var saida = (ConteudoDeEnvio.MensagemMidia) compositor.montar(pendente(conteudo)).conteudo();

        assertThat(saida.legenda()).isNull();
    }

    @Test
    void nomeAusenteMantemMensagemLimpa() {
        configurarOrigem(Remetente.atendente(UUID.randomUUID()), " ");
        var conteudo = new ConteudoDeEnvio.MensagemLivre("sem nome");

        assertThat(compositor.montar(pendente(conteudo)).conteudo()).isSameAs(conteudo);
    }

    @Test
    void origemNaoEncontradaMantemMensagemLimpa() {
        when(origens.buscar(MENSAGEM_ID, ENVIADO_EM)).thenReturn(Optional.empty());
        var conteudo = new ConteudoDeEnvio.MensagemLivre("sem origem");

        assertThat(compositor.montar(pendente(conteudo)).conteudo()).isSameAs(conteudo);
    }

    private void configurarOrigem(Remetente remetente, String nome) {
        Mensagem mensagem = Mensagem.texto(
                MENSAGEM_ID, UUID.randomUUID(), remetente, "original", ENVIADO_EM);
        when(origens.buscar(MENSAGEM_ID, ENVIADO_EM))
                .thenReturn(Optional.of(new OrigemDeMensagem(mensagem, UUID.randomUUID(), "Lead", nome)));
    }

    private static Outbox.EnvioPendente pendente(ConteudoDeEnvio conteudo) {
        return new Outbox.EnvioPendente(
                UUID.randomUUID(),
                MENSAGEM_ID,
                ENVIADO_EM,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "5561999999999",
                UUID.randomUUID(),
                conteudo,
                0);
    }
}
