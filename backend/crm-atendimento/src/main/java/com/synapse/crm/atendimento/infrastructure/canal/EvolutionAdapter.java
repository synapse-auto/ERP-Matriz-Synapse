package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import okio.BufferedSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.*;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/** ACL Baileys 2.3.7. So chama o origin configurado; nao segue links do callback. */
@Component
@ConditionalOnProperty(prefix="synapse.canal.evolution",name="habilitado",havingValue="true")
class EvolutionAdapter implements CanalGateway {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(EvolutionAdapter.class);
    private final EvolutionProperties p;
    private final ObjectMapper json;
    private final OkHttpClient http;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;
    private final CircuitBreaker midiaBreaker,saudeBreaker;
    private final Bulkhead midiaBulkhead,saudeBulkhead;
    private final ZApiMidiaSaida preparador;
    private final DetectorDeTipoReal detector;
    private final LimiteDeAnexoRepositorio limites;
    @Autowired EvolutionAdapter(EvolutionProperties p,ObjectMapper json,CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia storage,LimiteDeAnexoRepositorio limites,DetectorDeTipoReal detector) {
        this(p,json,breakers,storage,limites,detector,cliente(p));
    }
    EvolutionAdapter(EvolutionProperties p,ObjectMapper json,CircuitBreakerRegistry breakers,
            ArmazenamentoDeMidia storage,LimiteDeAnexoRepositorio limites,DetectorDeTipoReal detector,OkHttpClient http) {
        p.validar();this.p=p;this.json=json;this.http=http;this.detector=detector;this.limites=limites;
        this.preparador=new ZApiMidiaSaida(storage,limites,json);
        this.breaker=breakers.circuitBreaker("canal-evolution");
        this.bulkhead=Bulkhead.of("canal-evolution",BulkheadConfig.custom().maxConcurrentCalls(p.concorrencia()).maxWaitDuration(java.time.Duration.ZERO).build());
        this.midiaBreaker=breakers.circuitBreaker("canal-evolution-midia");this.saudeBreaker=breakers.circuitBreaker("canal-evolution-saude");
        this.midiaBulkhead=Bulkhead.of("canal-evolution-midia",BulkheadConfig.custom().maxConcurrentCalls(p.concorrencia()).maxWaitDuration(java.time.Duration.ZERO).build());
        this.saudeBulkhead=Bulkhead.of("canal-evolution-saude",BulkheadConfig.custom().maxConcurrentCalls(p.concorrencia()).maxWaitDuration(java.time.Duration.ZERO).build());
    }
    private static OkHttpClient cliente(EvolutionProperties p) {
        p.validar();
        return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .dns(p.permitirDestinoPrivado()?Dns.SYSTEM:new ZApiDnsPublico(Dns.SYSTEM))
                .connectTimeout(p.timeout()).readTimeout(p.timeout()).callTimeout(p.timeout()).build();
    }
    @Override public String provedor(){return "evolution";}
    @Override public boolean aceitaTextoLivre(Optional<Instant> ultima,Instant agora){return true;}
    @Override public boolean exigeTemplateForaDaJanela(){return false;}
    @Override public AutenticacaoDoCanal verificarAutenticacao() {
        try {
            var n=saudeBulkhead.executeSupplier(()->saudeBreaker.executeSupplier(()->consultar("instance/connectionState",null)));
            return "open".equals(n.path("instance").path("state").asText())?AutenticacaoDoCanal.aceita():AutenticacaoDoCanal.recusada("EVOLUTION_DESCONECTADA");
        } catch(RuntimeException e){return AutenticacaoDoCanal.recusada("EVOLUTION_SAUDE_NAO_CONFIRMADA");}
    }
    @Override public ResultadoDeEnvio enviar(Envio envio) {
        if(!p.credencialId().equals(envio.credencialId())) return ResultadoDeEnvio.Recusado.permanente("EVOLUTION_CREDENCIAL_DIVERGENTE");
        if(envio.telefoneDestino()==null||!envio.telefoneDestino().matches("[1-9][0-9]{7,14}")) return ResultadoDeEnvio.Recusado.permanente("EVOLUTION_DESTINO_INVALIDO");
        if(envio.conteudo() instanceof ConteudoDeEnvio.MensagemTemplate) return ResultadoDeEnvio.Recusado.permanente("CONTEUDO_NAO_SUPORTADO_PELO_CANAL");
        if(envio.contextoWamid()!=null) {
            var id=envio.identidadeDaCitacao();
            if(id==null||!provedor().equals(id.provedor())||!p.instancia().equals(id.instancia())||!"WHATSAPP".equals(id.namespace())
                    ||!envio.contextoWamid().equals(id.valor())) return ResultadoDeEnvio.Recusado.permanente("CITACAO_NAO_SUPORTADA_PELO_CANAL");
        }
        try {return bulkhead.executeSupplier(()->breaker.executeSupplier(()->enviarPreparado(envio)));}
        catch(CallNotPermittedException|BulkheadFullException e){return ResultadoDeEnvio.Recusado.temporario("EVOLUTION_LIMITE_OU_DISJUNTOR");}
        catch(ZApiMidiaSaida.MidiaInvalida e){return ResultadoDeEnvio.Recusado.permanente("EVOLUTION_MIDIA_INVALIDA");}
        catch(ZApiMidiaSaida.StorageIndisponivel e){return ResultadoDeEnvio.Recusado.temporario("EVOLUTION_STORAGE_INDISPONIVEL");}
        catch(PreparacaoIndisponivel e){return ResultadoDeEnvio.Recusado.temporario("EVOLUTION_REFERENCIA_INDISPONIVEL");}
        catch(FalhaHttp e){
            log.warn("EVOLUTION_ENVIO_FALHOU etapa=POST_ENVIO http_status={} mensagem={}",e.status,envio.mensagemId());
            return List.of(401,403,404,405,413,415).contains(e.status)?ResultadoDeEnvio.Recusado.permanente("EVOLUTION_HTTP_"+e.status)
                    :new ResultadoDeEnvio.Ambiguo("EVOLUTION_ENVIO_NAO_CONFIRMADO_HTTP_"+e.status);
        }
        catch(RuntimeException e){log.warn("EVOLUTION_ENVIO_FALHOU etapa=POST_ENVIO codigo=RESPOSTA_NAO_CONFIRMADA mensagem={}",envio.mensagemId());return new ResultadoDeEnvio.Ambiguo("EVOLUTION_ENVIO_NAO_CONFIRMADO");}
    }
    private ResultadoDeEnvio enviarPreparado(Envio envio) {
        ObjectNode campos=json.createObjectNode().put("number",envio.telefoneDestino());
        if(envio.contextoWamid()!=null) {
            ObjectNode consulta=json.createObjectNode().put("offset",1).put("page",1);
            consulta.putObject("where").putObject("key").put("id",envio.contextoWamid())
                    .put("remoteJid",envio.telefoneDestino()+"@s.whatsapp.net").put("remoteJidAlt",envio.telefoneDestino()+"@s.whatsapp.net");
            JsonNode registros;
            try {registros=consultar("chat/findMessages",consulta).path("messages").path("records");}
            catch(RuntimeException e){throw new PreparacaoIndisponivel();}
            if(!registros.isArray()||registros.size()!=1) return ResultadoDeEnvio.Recusado.permanente("CITACAO_NAO_SUPORTADA_PELO_CANAL");
            JsonNode original=registros.get(0),key=original.path("key");
            if(!envio.contextoWamid().equals(key.path("id").asText())||!envio.telefoneDestino().equals(EvolutionWebhookTradutor.telefoneDaChave(key))
                    ||!key.path("fromMe").isBoolean()||!original.path("message").isObject()) return ResultadoDeEnvio.Recusado.permanente("CITACAO_NAO_SUPORTADA_PELO_CANAL");
            campos.putObject("quoted").set("key",key);
            ((ObjectNode)campos.path("quoted")).set("message",original.path("message"));
        }
        if(envio.conteudo() instanceof ConteudoDeEnvio.MensagemLivre texto) {
            campos.put("text",texto.texto());return despachar("message/sendText",corpo(campos));
        }
        var midia=(ConteudoDeEnvio.MensagemMidia)envio.conteudo();
        try(var pronta=preparador.preparar(midia)) {
            campos.put("mediatype",pronta.campo()).put("mimetype",pronta.mime()).put("fileName",pronta.nome());
            if(pronta.legenda()!=null)campos.put("caption",pronta.legenda());
            // A rota multipart 2.3.7 nao desserializa quoted. JSON preserva o objeto;
            // codificacao binaria em fluxo evita String/byte[] Base64 no heap do worker.
            return despachar("message/sendMedia",new RequestBody(){
                @Override public MediaType contentType(){return MediaType.get("application/json");}
                @Override public void writeTo(BufferedSink sink)throws IOException {
                    try(var g=json.getFactory().createGenerator(sink.outputStream());var in=Files.newInputStream(pronta.arquivo())) {
                        g.disable(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                        g.writeStartObject();
                        var camposIterator=campos.fields();
                        while(camposIterator.hasNext()){var e=camposIterator.next();g.writeFieldName(e.getKey());g.writeTree(e.getValue());}
                        g.writeFieldName("media");g.writeBinary(in,-1);g.writeEndObject();
                    }
                }
            });
        }
    }
    private ResultadoDeEnvio despachar(String rota,RequestBody corpo) {
        // Falhas atravessam o breaker ANTES de virar resultado tipado.
        JsonNode n=consultarCorpo(rota,corpo),key=n.path("key");
        String id=EvolutionWebhookTradutor.texto(key,"id");
        if(id==null||!key.path("fromMe").asBoolean(false))throw new IllegalStateException("EVOLUTION_RESPOSTA_SEM_IDENTIDADE");
        return new ResultadoDeEnvio.Enfileirado(p.instancia(),null,id);
    }
    private RequestBody corpo(JsonNode no) {
        return new RequestBody(){
            @Override public MediaType contentType(){return MediaType.get("application/json");}
            @Override public void writeTo(BufferedSink sink)throws IOException {
                try(var g=json.getFactory().createGenerator(sink.outputStream())) {
                    g.disable(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET);g.writeTree(no);
                }
            }
        };
    }
    private JsonNode consultar(String rota,JsonNode no){return consultarCorpo(rota,no==null?null:corpo(no));}
    Request pedido(String rota,RequestBody corpo) {
        var url=HttpUrl.get(p.urlBase()).newBuilder();
        for(String parte:rota.split("/"))url.addPathSegment(parte);
        url.addPathSegment(p.instancia());
        return new Request.Builder().url(url.build()).header("apikey",p.apiKey()).method(corpo==null?"GET":"POST",corpo).build();
    }
    private JsonNode consultarCorpo(String rota,RequestBody corpo) {
        try(var response=http.newCall(pedido(rota,corpo)).execute()) {
            if(!response.isSuccessful()) throw new FalhaHttp(response.code());
            if(response.body()==null) throw new FalhaHttp(response.code());
            try(var in=response.body().byteStream()) {
                byte[] bytes=in.readNBytes(p.limiteRespostaBytes()+1);
                if(bytes.length>p.limiteRespostaBytes())throw new IllegalArgumentException("EVOLUTION_RESPOSTA_EXCEDE_LIMITE");
                return json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            }
        }catch(IOException e){throw new IllegalStateException("EVOLUTION_TRANSPORTE_INDISPONIVEL");}
    }
    @Override public Optional<MidiaRecebidaEmFluxo> abrirMidiaRecebida(String referencia,TipoMensagem tipo) {
        try {return Optional.of(midiaBulkhead.executeSupplier(()->midiaBreaker.executeSupplier(()->baixar(referencia,tipo))));}
        catch(MidiaRecebidaInvalidaException|MidiaRecebidaTemporariamenteIndisponivelException|MidiaRecebidaRemovidaNoProvedorException e){throw e;}
        catch(RuntimeException e){throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=evolution; etapa=download; codigo=TRANSPORTE_OU_DISJUNTOR");}
    }
    @Override public MidiaRecebida baixarMidiaRecebida(String referencia) {
        try(var f=abrirMidiaRecebida(referencia,TipoMensagem.DOCUMENTO).orElseThrow()) {
            return new MidiaRecebida(f.conteudo().readAllBytes(),f.mimetype());
        }catch(IOException e){throw new MidiaRecebidaInvalidaException();}
    }
    private MidiaRecebidaEmFluxo baixar(String referencia,TipoMensagem esperado) {
        java.nio.file.Path arquivo=null;
        String etapa="referencia";
        try {
            JsonNode key=json.readTree(referencia);
            if(EvolutionWebhookTradutor.texto(key,"id")==null||EvolutionWebhookTradutor.telefoneDaChave(key)==null
                    ||!key.path("fromMe").isBoolean()||key.path("fromMe").asBoolean())throw new MidiaRecebidaInvalidaException();
            ObjectNode pedido=json.createObjectNode().put("convertToMp4",false);pedido.putObject("message").set("key",key);
            long teto=Math.min(TiposDeMidiaPermitidos.tetoDaMetaEmBytes(esperado),
                    limites.limiteEmBytes(com.synapse.crm.sharedkernel.midia.CategoriaDeMidia.valueOf(esperado.name())).orElse(Long.MAX_VALUE));
            etapa="spool";arquivo=Files.createTempFile("evolution-entrada-",".tmp");
            boolean binario=false;
            etapa="download";
            try(var response=http.newCall(pedido("chat/getBase64FromMediaMessage",corpo(pedido))).execute()) {
                int status=response.code();
                if(status==410)throw new MidiaRecebidaRemovidaNoProvedorException("provedor=evolution; etapa=download; http_status=410");
                if(status==429||status>=500)throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=evolution; etapa=download; http_status="+status);
                if(!response.isSuccessful())throw new MidiaRecebidaInvalidaException(status);
                if(response.body()==null)throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.CONTEUDO_VAZIO);
                // O endpoint oficial devolve Base64. Decodificar diretamente no spool limitado.
                try(var in=new EntradaLimitada(response.body().byteStream(),teto*2+p.limiteRespostaBytes());var parser=json.getFactory().createParser(in);
                        var out=Files.newOutputStream(arquivo)) {
                    if(parser.nextToken()!=com.fasterxml.jackson.core.JsonToken.START_OBJECT)throw new MidiaRecebidaInvalidaException();
                    while(parser.nextToken()!=com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                        if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.FIELD_NAME)throw new MidiaRecebidaInvalidaException();
                        String campo=parser.currentName();parser.nextToken();
                        if("base64".equals(campo)) {
                            if(binario||parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_STRING)throw new MidiaRecebidaInvalidaException();
                            parser.readBinaryValue(new java.io.FilterOutputStream(out){
                                long gravados;
                                @Override public void write(int b)throws IOException{if(++gravados>teto)throw new IOException("EVOLUTION_MIDIA_EXCEDE_LIMITE");out.write(b);}
                                @Override public void write(byte[] b,int off,int len)throws IOException{gravados+=len;if(gravados>teto)throw new IOException("EVOLUTION_MIDIA_EXCEDE_LIMITE");out.write(b,off,len);}
                            });binario=true;
                        } else parser.skipChildren();
                    }
                    if(parser.nextToken()!=null)throw new MidiaRecebidaInvalidaException();
                }
            }
            etapa="validacao";long tamanho=Files.size(arquivo);
            if(!binario||tamanho==0||tamanho>teto)throw new MidiaRecebidaInvalidaException();
            String mime=detector.detectar(arquivo);
            boolean container=java.util.Set.of("application/ogg","audio/ogg","audio/opus","audio/x-opus+ogg","video/mp4","video/quicktime","audio/mp4","video/3gpp").contains(mime);
            if(container&&tamanho>TiposDeMidiaPermitidos.tetoDaMetaEmBytes(TipoMensagem.AUDIO))throw new MidiaRecebidaInvalidaException();
            byte[] bytes=container?Files.readAllBytes(arquivo):new byte[0];
            if(java.util.Set.of("application/ogg","audio/ogg","audio/opus","audio/x-opus+ogg").contains(mime)) {
                if(!com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus.ehValido(bytes)&&!OggOpusRecebido.integro(bytes))
                    throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.OGG_INVALIDO);
                mime="audio/ogg";
            }
            var tipo=TiposDeMidiaPermitidos.classificar(mime,bytes).orElseThrow(MidiaRecebidaInvalidaException::new);
            if(esperado!=TipoMensagem.DOCUMENTO&&tipo.tipo()!=esperado)throw new MidiaRecebidaInvalidaException();
            var pronto=arquivo;
            var fluxo=new java.io.FilterInputStream(Files.newInputStream(pronto)) {
                @Override public void close()throws IOException{try{super.close();}finally{removerSpool(pronto);}}
            };
            arquivo=null;return new MidiaRecebidaEmFluxo(fluxo,tamanho,tipo.mimetype());
        }catch(com.fasterxml.jackson.core.JacksonException|IllegalArgumentException e){throw new MidiaRecebidaInvalidaException(MidiaRecebidaInvalidaException.Motivo.PROCESSAMENTO);}
        catch(IOException e){throw new MidiaRecebidaTemporariamenteIndisponivelException("provedor=evolution; etapa="+etapa+"; codigo=IO_INDISPONIVEL");}
        finally{removerSpool(arquivo);}
    }
    private static void removerSpool(java.nio.file.Path arquivo) {
        if(arquivo==null)return;
        try{Files.deleteIfExists(arquivo);}catch(IOException e){log.warn("EVOLUTION_LIMPEZA_SPOOL_PENDENTE");arquivo.toFile().deleteOnExit();}
    }
    private static final class PreparacaoIndisponivel extends RuntimeException {private static final long serialVersionUID=1L;}
    private static final class EntradaLimitada extends java.io.FilterInputStream {
        private long restante;
        EntradaLimitada(InputStream in,long limite){super(in);restante=limite;}
        @Override public int read()throws IOException{int b=super.read();if(b!=-1&&--restante<0)throw new IOException("EVOLUTION_RESPOSTA_EXCEDE_LIMITE");return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException{
            int n=super.read(b,off,(int)Math.min(len,restante+1));
            if(n>0&&(restante-=n)<0)throw new IOException("EVOLUTION_RESPOSTA_EXCEDE_LIMITE");return n;
        }
    }
    private static final class FalhaHttp extends RuntimeException {
        private static final long serialVersionUID=1L;final int status;
        FalhaHttp(int status){super("EVOLUTION_HTTP_"+status);this.status=status;}
    }
}
