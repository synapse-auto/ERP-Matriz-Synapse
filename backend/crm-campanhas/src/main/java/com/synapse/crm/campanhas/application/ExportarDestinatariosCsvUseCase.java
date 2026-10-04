package com.synapse.crm.campanhas.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios.Filtro;
import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios.Linha;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** Resultado da campanha em CSV, uma linha por destinatario, escrito sem materializar a lista na memoria. */
@Service
public class ExportarDestinatariosCsvUseCase {

    static final String CABECALHO = "nome,telefone,status,motivo,codigo_de_erro,enviado_em,entregue_em,lido_em,respondeu_em,conferencia_em";

    private final ConsultasDeDestinatarios destinatarios;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;

    public ExportarDestinatariosCsvUseCase(
            ConsultasDeDestinatarios destinatarios,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes) {
        this.destinatarios = destinatarios;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
    }

    @PreAuthorize(PermissoesDeCampanha.LEITURA_DE_DESTINATARIOS)
    @Auditable(acao = "EXPORTAR_RESULTADO_CAMPANHA", entidadeTipo = "CAMPANHA", capturarDados = false)
    public void executar(UUID campanhaId, Appendable saida) {
        disponibilidade.exigir();
        transacoes.noGeralSomenteLeitura(() -> {
            escrever(saida, CABECALHO);
            destinatarios.percorrer(new Filtro(campanhaId, null, null, false), linha -> escrever(saida, linha(linha)));
            return null;
        });
    }

    static String linha(Linha linha) {
        return String.join(
                ",",
                celula(linha.nome()),
                celula(linha.telefone()),
                celula(linha.status().name()),
                celula(linha.motivo() == null ? null : linha.motivo().name()),
                celula(linha.codigoDeErro() == null ? null : linha.codigoDeErro().toString()),
                celula(instante(linha.enviadoEm())),
                celula(instante(linha.entregueEm())),
                celula(instante(linha.lidoEm())),
                celula(instante(linha.respondeuEm())),
                celula(instante(linha.conferenciaEm())));
    }

    /**
     * Escapa para CSV e neutraliza injecao de formula: nome de contato comeca com {@code =}, {@code +}, {@code -}
     * ou {@code @} vira texto no Excel, nao formula.
     */
    static String celula(String valor) {
        if (valor == null) {
            return "";
        }
        String seguro = valor;
        if (!seguro.isEmpty() && "=+-@\t\r".indexOf(seguro.charAt(0)) >= 0) {
            seguro = "'" + seguro;
        }
        if (seguro.contains(",") || seguro.contains("\"") || seguro.contains("\n") || seguro.contains("\r")) {
            return "\"" + seguro.replace("\"", "\"\"") + "\"";
        }
        return seguro;
    }

    private static String instante(Instant instante) {
        return instante == null ? null : instante.toString();
    }

    private static void escrever(Appendable saida, String linha) {
        try {
            saida.append(linha).append('\n');
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
