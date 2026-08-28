package com.synapse.crm.atendimento.domain.canal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A porta de saida para o canal externo. O unico lugar por onde mensagem deixa o CRM.
 *
 * <p>Java puro: nenhum tipo do provedor aparece nesta assinatura, nem em {@link ConteudoDeEnvio}, nem
 * em {@link ResultadoDeEnvio}. Quando o formato da Meta mudar — e vai —, a mudanca fica confinada ao
 * adaptador. Esse e o ponto inteiro do ACL.
 *
 * <p>Nenhum caso de uso sabe qual implementacao esta ativa. A escolha e por configuracao da instancia
 * ({@code synapse.canal.whatsapp.provedor}), resolvida num mapa. Se um caso de uso precisar perguntar
 * "sou Meta ou Z-API?", a abstracao vazou e o proximo filho vira um fork.
 */
public interface CanalGateway {

    /** Chave de configuracao que seleciona este adaptador. Ex.: {@code meta-cloud}, {@code z-api}. */
    String provedor();

    /**
     * Da para mandar texto livre para este lead agora?
     *
     * <p>A pergunta e do dominio; a resposta e do adaptador. A Meta oficial responde "so dentro de
     * 24h desde a ultima mensagem do cliente"; um provedor nao oficial responde sempre sim. Se a
     * regra morasse no dominio, todo filho que usa Z-API carregaria uma restricao que nao existe para
     * ele — e alguem acabaria escrevendo {@code if (provedor == "meta")} para contornar.
     *
     * @param ultimaInteracaoDoLead vazio quando o lead nunca interagiu; a janela esta fechada
     */
    boolean aceitaTextoLivre(Optional<Instant> ultimaInteracaoDoLead, Instant agora);

    /**
     * O canal ativo exige template pre-aprovado fora da janela de texto livre?
     *
     * <p>A Automacao (E07 §5) precisa saber disto <b>antes</b> de tentar enviar — senao descobre por
     * um 400 traduzido da Meta, que e diagnostico ruim, na primeira execucao de uma campanha de
     * reativacao de 90 dias sem contato. Um provedor nao oficial responde que nao exige, porque nunca
     * teve janela para comecar.
     */
    boolean exigeTemplateForaDaJanela();

    /**
     * Confirma que a credencial configurada ainda autentica no provedor.
     *
     * <p>Esta chamada pertence exclusivamente ao monitoramento operacional. Nunca entra no caminho
     * sincrono de envio ou recebimento de mensagem.
     */
    AutenticacaoDoCanal verificarAutenticacao();

    /**
     * Entrega a mensagem ao provedor.
     *
     * <p>Nao lanca por falha do provedor: devolve {@link ResultadoDeEnvio.Recusado}. Excecao aqui
     * subiria pelo publisher da outbox e a linha ficaria sem diagnostico; o resultado tipado carrega
     * o motivo e diz se vale retentar.
     */
    ResultadoDeEnvio enviar(Envio envio);

    /**
     * Baixa uma midia que o cliente mandou (E11b).
     *
     * <p>A Meta entrega midia recebida por <b>referencia</b> no webhook — um id, nao os bytes nem uma
     * URL duradoura. Este metodo faz a troca desse id pelos bytes de verdade, para o CRM persistir no
     * proprio storage; a URL da Meta expira em minutos e nao pode ser o que fica salvo.
     */
    MidiaRecebida baixarMidiaRecebida(String midiaIdExterno);

    /**
     * Templates cadastrados no provedor desta instancia.
     *
     * <p>Nao entra no caminho sincrono de envio/recebimento: so e chamado quando o usuario abre a
     * tela de templates ou o composer com a janela de 24h fechada. Provedor que nao gerencia
     * templates devolve lista vazia — o CRM nao inventa modelo.
     */
    default List<TemplateDoCanal> listarTemplates() {
        return List.of();
    }

    /**
     * Submete um template de texto ao provedor. A aprovacao, quando existe, e assincrona: aceitar
     * aqui significa "entrou na fila da Meta", nao "ja pode enviar".
     */
    default ResultadoDeTemplate criarTemplate(PedidoDeTemplate pedido) {
        return new ResultadoDeTemplate.Recusado("provedor nao gerencia templates");
    }

    /**
     * Tudo o que o adaptador precisa para montar a chamada.
     *
     * @param credencialId qual credencial usar; o historico aponta para a vigente na epoca, entao
     *     troca de numero nao corrompe conversa antiga
     */
    record Envio(UUID mensagemId, String telefoneDestino, ConteudoDeEnvio conteudo, UUID credencialId) {}

    /** Os bytes de uma midia recebida, prontos para o storage proprio. */
    record MidiaRecebida(byte[] conteudo, String mimetype) {}

    /** Resultado sanitizado: o diagnostico nunca carrega token nem corpo devolvido pelo provedor. */
    record AutenticacaoDoCanal(boolean autenticada, String detalhe) {

        public static AutenticacaoDoCanal aceita() {
            return new AutenticacaoDoCanal(true, "credencial aceita pelo provedor");
        }

        public static AutenticacaoDoCanal recusada(String detalhe) {
            return new AutenticacaoDoCanal(false, detalhe);
        }
    }
}
