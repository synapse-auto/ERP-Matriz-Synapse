package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;
import com.synapse.crm.sharedkernel.midia.RegrasDeAnexoBase;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

/** ACL de saida; recebe somente objetos ja validados e persistidos pelo upload/encaminhamento. */
@Component
@ConditionalOnProperty(prefix = "synapse.canal.z-api", name = "habilitado", havingValue = "true")
class ZApiMidiaSaida {
    private static final Logger log = LoggerFactory.getLogger(ZApiMidiaSaida.class);
    private static final Map<String, String> EXTENSOES = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"), Map.entry("image/webp", "webp"),
            Map.entry("audio/ogg", "ogg"), Map.entry("audio/mpeg", "mp3"), Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/amr", "amr"), Map.entry("audio/aac", "aac"),
            Map.entry("video/mp4", "mp4"), Map.entry("video/3gpp", "3gp"),
            Map.entry("application/pdf", "pdf"), Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("text/plain", "txt"));
    private final ArmazenamentoDeMidia storage;
    private final LimiteDeAnexoRepositorio limites;
    private final ObjectMapper json;

    ZApiMidiaSaida(ArmazenamentoDeMidia storage, LimiteDeAnexoRepositorio limites, ObjectMapper json) {
        this.storage = storage;
        this.limites = limites;
        this.json = json;
    }

    Preparada preparar(ConteudoDeEnvio.MensagemMidia midia) {
        JsonNode metadados;
        try {
            metadados = json.readTree(midia.metadados());
        } catch (IOException | IllegalArgumentException e) {
            throw new MidiaInvalida();
        }
        if (metadados == null || !metadados.isObject() || !metadados.path("mimetype").isTextual()
                || !metadados.path("tamanho").isIntegralNumber() || !metadados.path("tamanho").canConvertToLong()) {
            throw new MidiaInvalida();
        }
        String mime = metadados.path("mimetype").asText();
        String extensao = EXTENSOES.get(mime);
        CategoriaDeMidia categoria = categoria(midia.tipo());
        if (extensao == null || !compativel(midia.tipo(), mime)
                || (midia.tipo() == TipoMensagem.AUDIO && midia.legenda() != null && !midia.legenda().isBlank())) {
            throw new MidiaInvalida();
        }
        long teto = TiposDeMidiaPermitidos.tetoDaMetaEmBytes(midia.tipo());
        long limite = Math.min(teto, limites.limiteEmBytes(categoria).orElse(teto));
        long esperado = metadados.path("tamanho").asLong();
        if (esperado <= 0 || esperado > limite) throw new MidiaInvalida();
        String nome = nomeSeguro(metadados.path("nome").asText(""), extensao);
        Path temporario = null;
        try {
            // O storage e lido ANTES do POST: falha aqui pode recuperar sem duplicar mensagem.
            // Spool limitado evita byte[]/String Base64 de ate 100 MiB no heap do worker.
            temporario = Files.createTempFile("z-api-midia-", ".tmp");
            long lidos = 0;
            try (InputStream entrada = storage.abrirLeitura(midia.referenciaStorage());
                    OutputStream saida = Files.newOutputStream(temporario)) {
                byte[] buffer = new byte[8192];
                int quantidade;
                while ((quantidade = entrada.read(buffer, 0, (int) Math.min(buffer.length, limite - lidos + 1))) != -1) {
                    lidos += quantidade;
                    if (lidos > limite) throw new MidiaInvalida();
                    saida.write(buffer, 0, quantidade);
                }
            }
            if (lidos != esperado) throw new MidiaInvalida();
            if (metadados.path("gravacaoDoComposer").asBoolean(false)
                    && (midia.tipo() != TipoMensagem.AUDIO || !mime.equals("audio/ogg")
                    || !ValidadorDeOggOpus.ehValido(Files.readAllBytes(temporario)))) {
                throw new MidiaInvalida();
            }
            return new Preparada(temporario, midia.tipo(), mime, nome, midia.legenda());
        } catch (RuntimeException | IOException e) {
            remover(temporario);
            if (e instanceof MidiaInvalida invalida) throw invalida;
            // Nao propagar a causa: pode conter chave privada, endpoint ou credencial do storage.
            throw new StorageIndisponivel();
        }
    }

    private static CategoriaDeMidia categoria(TipoMensagem tipo) {
        return switch (tipo) {
            case IMAGEM -> CategoriaDeMidia.IMAGEM;
            case AUDIO -> CategoriaDeMidia.AUDIO;
            case VIDEO -> CategoriaDeMidia.VIDEO;
            case DOCUMENTO -> CategoriaDeMidia.DOCUMENTO;
            default -> throw new MidiaInvalida();
        };
    }

    private static boolean compativel(TipoMensagem tipo, String mime) {
        if (tipo == TipoMensagem.VIDEO) return mime.equals("video/mp4") || mime.equals("video/3gpp");
        var categoria = RegrasDeAnexoBase.categoriaDe(mime);
        return categoria.isPresent() && (tipo == TipoMensagem.DOCUMENTO || categoria.get() == categoria(tipo));
    }

    private static String nomeSeguro(String nome, String extensao) {
        String base = nome.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (base.isBlank() || base.equals(".") || base.equals("..")) base = "arquivo";
        String minusculo = base.toLowerCase(Locale.ROOT);
        if (minusculo.endsWith("." + extensao)
                || (extensao.equals("jpg") && minusculo.endsWith(".jpeg"))
                || (extensao.equals("m4a") && minusculo.endsWith(".mp4"))) return base;
        int ponto = base.lastIndexOf('.');
        return (ponto > 0 ? base.substring(0, ponto) : base) + "." + extensao;
    }

    private static void remover(Path temporario) {
        if (temporario == null) return;
        try {
            Files.deleteIfExists(temporario);
        } catch (IOException e) {
            // Uma falha de limpeza apos o aceite nao pode inverter o resultado nem provocar retry.
            log.warn("Nao foi possivel remover spool temporario de midia Z-API");
            temporario.toFile().deleteOnExit();
        }
    }

    record Preparada(Path arquivo, TipoMensagem tipo, String mime, String nome, String legenda) implements AutoCloseable {
        String campo() {
            return switch (tipo) {
                case IMAGEM -> "image";
                case AUDIO -> "audio";
                case VIDEO -> "video";
                case DOCUMENTO -> "document";
                default -> throw new MidiaInvalida();
            };
        }

        String rota() {
            return tipo == TipoMensagem.DOCUMENTO ? "send-document/" + EXTENSOES.get(mime) : "send-" + campo();
        }

        void escrever(OutputStream saida, ObjectMapper json, String telefone, String citacao) throws IOException {
            try (JsonGenerator gerador = json.getFactory().createGenerator(saida);
                    InputStream entrada = Files.newInputStream(arquivo)) {
                gerador.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                gerador.writeStartObject();
                gerador.writeStringField("phone", telefone);
                if(citacao!=null && !citacao.isBlank()) gerador.writeStringField("messageId",citacao);
                if (tipo == TipoMensagem.DOCUMENTO) gerador.writeStringField("fileName", nome);
                if (tipo != TipoMensagem.AUDIO && legenda != null && !legenda.isBlank()) gerador.writeStringField("caption", legenda);
                gerador.writeFieldName(campo());
                // Prefixo composto exclusivamente de MIME da allowlist; os demais campos sao escapados pelo Jackson.
                gerador.writeRawValue("\"data:" + mime + ";base64,");
                gerador.flush();
                try (OutputStream codificador = Base64.getEncoder().wrap(new FilterOutputStream(saida) {
                    @Override public void close() throws IOException { flush(); }
                    @Override public void write(byte[] bytes, int inicio, int tamanho) throws IOException { out.write(bytes, inicio, tamanho); }
                })) {
                    entrada.transferTo(codificador);
                }
                gerador.writeRaw('"');
                gerador.writeEndObject();
            }
        }

        @Override public void close() { remover(arquivo); }
    }

    static final class MidiaInvalida extends RuntimeException {
        private static final long serialVersionUID = 1L;
        MidiaInvalida() { super("Z_API_MIDIA_INVALIDA"); }
    }

    static final class StorageIndisponivel extends RuntimeException {
        private static final long serialVersionUID = 1L;
        StorageIndisponivel() { super("Z_API_STORAGE_INDISPONIVEL"); }
    }
}
