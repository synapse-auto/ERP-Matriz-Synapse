package com.synapse.crm.core.application.lead.foto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.lead.FotoDoLead;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/**
 * Recebe, reprocessa e guarda a foto de perfil que a integracao externa coletou do contato.
 *
 * <p>O CRM nunca busca foto em lugar nenhum e nao agenda nada (RN-CRM-07): quem varre e quem chama
 * e a integracao. Este caso de uso e todo o lado do CRM.
 *
 * <p>A ordem de {@code AtualizarMinhaFotoUseCase} e copiada de proposito: valida, processa, salva no
 * storage, grava a referencia e <b>so entao</b> remove a antiga; em caso de erro remove a nova. A
 * inversao — apagar antes de gravar — deixa o lead sem foto nenhuma quando a escrita falha.
 */
@Service
public class AtualizarFotoDoLeadUseCase {

    private final LeadRepositorio leads;
    private final ProcessadorDeFotoDeLead processador;
    private final ArmazenamentoDeFotoDeLead armazenamento;
    private final LimiteDeAnexoRepositorio limite;
    private final Clock relogio;

    public AtualizarFotoDoLeadUseCase(
            LeadRepositorio leads,
            ProcessadorDeFotoDeLead processador,
            ArmazenamentoDeFotoDeLead armazenamento,
            LimiteDeAnexoRepositorio limite,
            Clock relogio) {
        this.leads = leads;
        this.processador = processador;
        this.armazenamento = armazenamento;
        this.limite = limite;
        this.relogio = relogio;
    }

    /**
     * Grava a foto recebida.
     *
     * <p>O hash e conferido <b>antes</b> do limite: reenvio do mesmo arquivo e o caminho comum do
     * polling e nao pode custar nem uma escrita, nem passar a falhar com 413 se alguem baixar o
     * limite configurado depois que a foto ja estava guardada.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    public ResultadoDaFotoDoLead executar(UUID leadId, byte[] original) {
        FotoDoLead atual = leads.fotoDoLead(leadId).orElseThrow(() -> naoEncontrado(leadId));
        if (original == null || original.length == 0) {
            throw new FotoDeLeadInvalidaException("a foto esta vazia");
        }
        String hash = sha256(original);
        if (atual.mesmoConteudo(hash)) {
            return ResultadoDaFotoDoLead.INALTERADA;
        }
        limite.limiteEmBytes(CategoriaDeMidia.IMAGEM)
                .filter(valor -> original.length > valor)
                .ifPresent(valor -> {
                    throw new FotoDeLeadExcedeuLimiteException(valor);
                });

        ProcessadorDeFotoDeLead.Resultado pronto = processador.processar(original);
        String novaReferencia = armazenamento.salvar(pronto.conteudo(), pronto.mimetype());
        try {
            if (!leads.atualizarFoto(leadId, novaReferencia, hash, Instant.now(relogio))) {
                throw naoEncontrado(leadId);
            }
        } catch (RuntimeException erro) {
            removerSemFalhar(novaReferencia);
            throw erro;
        }
        removerSemFalhar(atual.referencia());
        return ResultadoDaFotoDoLead.ATUALIZADA;
    }

    /**
     * Remove a foto. Idempotente de proposito: lead sem foto responde {@code REMOVIDA} tambem.
     *
     * <p>Um 404 aqui obrigaria a integracao a tratar "ja estava removida" como erro, e ela nao tem
     * como saber o estado do CRM sem perguntar antes.
     */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    public ResultadoDaFotoDoLead remover(UUID leadId) {
        FotoDoLead atual = leads.fotoDoLead(leadId).orElseThrow(() -> naoEncontrado(leadId));
        if (!atual.existe()) {
            return ResultadoDaFotoDoLead.REMOVIDA;
        }
        if (!leads.atualizarFoto(leadId, null, null, null)) {
            throw naoEncontrado(leadId);
        }
        removerSemFalhar(atual.referencia());
        return ResultadoDaFotoDoLead.REMOVIDA;
    }

    private void removerSemFalhar(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return;
        }
        try {
            armazenamento.remover(referencia);
        } catch (RuntimeException ignorado) {
            // A coluna ja aponta para a nova referencia; lixo antigo nao pode quebrar a integracao.
        }
    }

    private static LeadDaAutomacaoNaoEncontradoException naoEncontrado(UUID leadId) {
        return new LeadDaAutomacaoNaoEncontradoException(leadId);
    }

    /** SHA-256 dos bytes ORIGINAIS — o que a integracao mandou, nao o que o CRM gravou. */
    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
