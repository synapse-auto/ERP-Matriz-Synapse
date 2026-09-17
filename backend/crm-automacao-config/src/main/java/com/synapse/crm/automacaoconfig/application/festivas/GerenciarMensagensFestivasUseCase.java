package com.synapse.crm.automacaoconfig.application.festivas;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestivaInvalidaException;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestivaNaoEncontradaException;

/** CRUD das datas cadastradas; nao envia mensagens nesta fase. */
@Service
public class GerenciarMensagensFestivasUseCase {
    private static final int LIMITE_TITULO = 100;
    private static final int LIMITE_ICONE = 80;
    private static final int LIMITE_MENSAGEM = 2000;

    private final MensagemFestivaRepositorio repositorio;

    public GerenciarMensagensFestivasUseCase(MensagemFestivaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<MensagemFestiva> listar() {
        return repositorio.listarTodas();
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public MensagemFestiva criar(String titulo, String icone, LocalDate data, String mensagem, boolean ativo) {
        return repositorio.salvar(validar(new MensagemFestiva(UUID.randomUUID(), titulo, icone, data, mensagem, ativo)));
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public MensagemFestiva atualizar(UUID id, String titulo, String icone, LocalDate data, String mensagem, boolean ativo) {
        existente(id);
        return repositorio.salvar(validar(new MensagemFestiva(id, titulo, icone, data, mensagem, ativo)));
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public MensagemFestiva alternar(UUID id, boolean ativo) {
        MensagemFestiva atual = existente(id);
        return repositorio.salvar(new MensagemFestiva(id, atual.titulo(), atual.icone(), atual.data(), atual.mensagem(), ativo));
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void excluir(UUID id) {
        existente(id);
        repositorio.excluir(id);
    }

    private MensagemFestiva existente(UUID id) {
        return repositorio.porId(id).orElseThrow(() -> new MensagemFestivaNaoEncontradaException(id));
    }

    private static MensagemFestiva validar(MensagemFestiva mensagem) {
        exigirTexto(mensagem.titulo(), "O titulo da data festiva", LIMITE_TITULO);
        exigirTexto(mensagem.icone(), "O icone da data festiva", LIMITE_ICONE);
        exigirTexto(mensagem.mensagem(), "A mensagem da data festiva", LIMITE_MENSAGEM);
        if (mensagem.data() == null) {
            throw new MensagemFestivaInvalidaException("A data festiva e obrigatoria");
        }
        return mensagem;
    }

    private static void exigirTexto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new MensagemFestivaInvalidaException(campo + " nao pode ser vazio");
        }
        if (valor.length() > limite) {
            throw new MensagemFestivaInvalidaException(campo + " excede o limite de " + limite + " caracteres");
        }
    }
}
