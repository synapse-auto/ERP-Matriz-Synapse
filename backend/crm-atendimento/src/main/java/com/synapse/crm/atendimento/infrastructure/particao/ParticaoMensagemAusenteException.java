package com.synapse.crm.atendimento.infrastructure.particao;

import java.util.List;

/**
 * Falta particao de {@code mensagem} para algum mes da janela minima.
 *
 * <p>Lancada durante a inicializacao, o que impede a aplicacao de subir. E de proposito: sem a
 * particao do mes, o primeiro INSERT de mensagem falha. Entre nao subir e subir para quebrar no
 * primeiro envio da manha, nao subir e o menor dano — e o unico dos dois que alguem percebe antes
 * do cliente.
 */
public class ParticaoMensagemAusenteException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> particoesFaltantes;

    public ParticaoMensagemAusenteException(List<String> particoesFaltantes) {
        super(montarMensagem(particoesFaltantes));
        this.particoesFaltantes = List.copyOf(particoesFaltantes);
    }

    public List<String> particoesFaltantes() {
        return particoesFaltantes;
    }

    private static String montarMensagem(List<String> faltantes) {
        return """
                A tabela `mensagem` esta sem particao para: %s.

                Um INSERT numa faixa sem particao falha, o que derrubaria o envio e o \
                recebimento de mensagens. Por isso a aplicacao nao sobe.

                Para corrigir, rode no banco:
                    SELECT garantir_particoes_mensagem(3);

                Depois confira o que ainda falta com:
                    SELECT * FROM particoes_mensagem_faltantes(1);

                Se isso aconteceu em producao, o job mensal de particionamento parou de \
                rodar — vale investigar antes que a janela feche de novo."""
                .formatted(String.join(", ", faltantes));
    }
}
