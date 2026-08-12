package com.synapse.crm.app.canal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;

/**
 * Um provedor inteiro, falso, escolhido por configuracao.
 *
 * <p>Esta classe e o teste de portabilidade do ACL. Ela nao e mencionada por nenhuma linha de codigo
 * de producao — entra pelo mesmo caminho que um provedor real entraria: implementa as portas e
 * declara {@code provedor() = "fake"}. Se o fluxo completo roda com ela, entao trocar a Meta por
 * Z-API num filho e mesmo so mudar {@code synapse.canal.whatsapp.provedor}.
 *
 * <p>Se algum dia um caso de uso precisar saber qual provedor esta ativo, os testes que usam esta
 * classe quebram — que e exatamente o alarme que se quer.
 */
@Component
public class CanalFake implements CanalGateway {

    public static final String PROVEDOR = "fake";

    /** Assinatura aceita nos testes. O formato nao importa; a checagem, sim. */
    public static final String ASSINATURA_VALIDA = "sha256=fake-assinatura-valida";

    private final List<Envio> enviados = new CopyOnWriteArrayList<>();
    private final AtomicReference<ResultadoDeEnvio> proximaResposta =
            new AtomicReference<>(new ResultadoDeEnvio.Aceito("fake-id"));
    private final AtomicBoolean janelaAberta = new AtomicBoolean(true);

    @Override
    public String provedor() {
        return PROVEDOR;
    }

    // --- controle do teste ----------------------------------------------------

    /** Simula o provedor fora do ar: recusa temporaria, como o breaker aberto produziria. */
    public void derrubar(String motivo) {
        proximaResposta.set(ResultadoDeEnvio.Recusado.temporario(motivo));
    }

    /** Recusa que nao adianta retentar — numero invalido, template nao aprovado. */
    public void recusarDeVez(String motivo) {
        proximaResposta.set(ResultadoDeEnvio.Recusado.permanente(motivo));
    }

    public void religar() {
        proximaResposta.set(new ResultadoDeEnvio.Aceito("fake-id-" + System.nanoTime()));
    }

    /** Provedor nao oficial nao tem janela; oficial tem. O fake simula os dois. */
    public void fecharJanela() {
        janelaAberta.set(false);
    }

    public void abrirJanela() {
        janelaAberta.set(true);
    }

    public List<Envio> enviados() {
        return List.copyOf(enviados);
    }

    public void limpar() {
        enviados.clear();
        religar();
        abrirJanela();
    }

    // --- CanalGateway ---------------------------------------------------------

    @Override
    public boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora) {
        return janelaAberta.get();
    }

    /** Provedor nao oficial: nunca exigiu template, nao tem janela para comecar (E07 §5). */
    @Override
    public boolean exigeTemplateForaDaJanela() {
        return false;
    }

    @Override
    public AutenticacaoDoCanal verificarAutenticacao() {
        return AutenticacaoDoCanal.aceita();
    }

    @Override
    public ResultadoDeEnvio enviar(Envio envio) {
        ResultadoDeEnvio resposta = proximaResposta.get();
        // So conta como enviado o que o provedor de fato aceitou: e assim que o teste
        // distingue "nao chegou ao provedor" de "chegou e foi recusado".
        if (resposta.aceito()) {
            enviados.add(envio);
        }
        return resposta;
    }

    // --- midia recebida (E11b) -------------------------------------------------

    private final AtomicReference<MidiaRecebida> proximaMidiaRecebida = new AtomicReference<>();

    /** O que {@link #baixarMidiaRecebida} vai devolver na proxima chamada. */
    public void programarMidiaRecebida(byte[] conteudo, String mimetype) {
        proximaMidiaRecebida.set(new MidiaRecebida(conteudo, mimetype));
    }

    @Override
    public MidiaRecebida baixarMidiaRecebida(String midiaIdExterno) {
        MidiaRecebida programada = proximaMidiaRecebida.get();
        if (programada == null) {
            throw new IllegalStateException(
                    "teste nao chamou programarMidiaRecebida antes de simular o webhook de midia");
        }
        return programada;
    }
}
