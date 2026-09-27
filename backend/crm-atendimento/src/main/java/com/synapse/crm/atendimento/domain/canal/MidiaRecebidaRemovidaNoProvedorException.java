package com.synapse.crm.atendimento.domain.canal;

/**
 * O provedor declarou que a midia recebida nao existe mais (E218: HTTP 410 do resolvedor).
 *
 * <p>Oposto de {@link MidiaRecebidaTemporariamenteIndisponivelException}: repetir a mesma consulta
 * nao muda o resultado, entao o processador registra a mensagem sem arquivo na hora em vez de
 * entrar no backoff. De proposito nao estende a excecao temporaria, para nenhum caminho de retry
 * captura-la por heranca. A mensagem carrega somente identificadores tecnicos e o status HTTP;
 * nunca o corpo da resposta, uma URL temporaria ou uma credencial.
 */
public class MidiaRecebidaRemovidaNoProvedorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MidiaRecebidaRemovidaNoProvedorException(String motivo) {
        super(motivo);
    }

    /** Mesmo formato de {@link MidiaRecebidaTemporariamenteIndisponivelException#comTipo}. */
    public MidiaRecebidaRemovidaNoProvedorException comTipo(String tipo) {
        return new MidiaRecebidaRemovidaNoProvedorException("tipo=" + tipo + "; " + getMessage());
    }
}
