package com.synapse.crm.atendimento.infrastructure.midia;

import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.midia.LimiteDeAnexoRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Le {@code configuracao_automacao} por SQL direto — ver o Javadoc de {@link
 * LimiteDeAnexoRepositorio} para o porque de nao usar o modulo {@code crm-automacao-config}.
 *
 * <p>Pool geral, nao o do chat: e uma leitura de configuracao antes de gravar o anexo, nao parte do
 * caminho de entrega de mensagem em si.
 */
@Repository
class LimiteDeAnexoRepositorioJdbc implements LimiteDeAnexoRepositorio {

    private static final Map<TipoMensagem, String> CHAVE_POR_TIPO = Map.of(
            TipoMensagem.IMAGEM, "anexo.tamanho_maximo_imagem_mb",
            TipoMensagem.AUDIO, "anexo.tamanho_maximo_audio_mb",
            TipoMensagem.DOCUMENTO, "anexo.tamanho_maximo_documento_mb");

    private static final String SQL = "SELECT valor FROM configuracao_automacao WHERE chave = ?";
    private static final String CHAVE_DURACAO_AUDIO = "gravacao_audio.duracao_maxima_segundos";

    private final JdbcTemplate geral;

    LimiteDeAnexoRepositorioJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    @Override
    public Optional<Long> limiteEmBytes(TipoMensagem tipo) {
        String chave = CHAVE_POR_TIPO.get(tipo);
        if (chave == null) {
            return Optional.empty();
        }
        try {
            String valorEmMb = geral.queryForObject(SQL, String.class, chave);
            return Optional.of(Long.parseLong(valorEmMb) * 1024 * 1024);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> duracaoMaximaAudioEmSegundos() {
        try {
            return Optional.of(Long.parseLong(geral.queryForObject(SQL, String.class, CHAVE_DURACAO_AUDIO)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
