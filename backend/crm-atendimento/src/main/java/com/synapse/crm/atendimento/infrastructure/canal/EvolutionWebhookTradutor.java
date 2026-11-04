package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

/** Contrato Baileys da tag oficial 2.3.7, nao o schema antigo textMessage. */
public final class EvolutionWebhookTradutor implements TradutorDeCanal {
    private final ObjectMapper json;
    private final EvolutionProperties config;
    public EvolutionWebhookTradutor(ObjectMapper json, EvolutionProperties config) { this.json=json;this.config=config; }
    @Override public String provedor() { return "evolution"; }
    @Override public boolean tokenDeVerificacaoValido(String token) { return false; }
    @Override public boolean assinaturaValida(String payload,String assinatura,String segredo) { return false; }
    private JsonNode raiz(String payload) {
        try { return json.readTree(payload); } catch (Exception e) { throw new IllegalArgumentException("EVOLUTION_JSON_INVALIDO"); }
    }
    private boolean nossa(JsonNode n) { return config.instancia().equals(n.path("instance").asText()); }
    static String texto(JsonNode n,String campo) {
        JsonNode v=n.path(campo);
        return v.isTextual() && !v.asText().isBlank() && v.asText().chars().noneMatch(Character::isISOControl)?v.asText():null;
    }
    private static String conteudo(JsonNode n,String campo) {
        JsonNode v=n.path(campo);
        return v.isTextual()&&!v.asText().isBlank()&&v.asText().indexOf('\0')<0?v.asText():null;
    }
    private IdentidadeDeMensagemDoCanal identidade(String id) { return new IdentidadeDeMensagemDoCanal(provedor(),config.instancia(),"WHATSAPP",id); }
    @Override public DestinosDoWebhook destinos(String payload) {
        String instancia=texto(raiz(payload),"instance");
        return new DestinosDoWebhook(1,instancia==null?List.of():List.of(instancia));
    }
    @Override public List<String> idsExternos(String payload) {
        JsonNode n=raiz(payload);
        if(!nossa(n)) return List.of();
        JsonNode d=n.path("data");String evento=texto(n,"event");
        String id=texto(d.path("key"),"id");
        if("messages.upsert".equals(evento)&&id!=null)
            return List.of(new IdentidadeDeMensagemDoCanal(provedor(),config.instancia(),"PROCESSAMENTO",evento+":"+id).chaveInterna());
        id=texto(d,"keyId");
        if("messages.update".equals(evento)&&id!=null)
            return List.of(new IdentidadeDeMensagemDoCanal(provedor(),config.instancia(),"PROCESSAMENTO",evento+":"+id+":"+d.path("status").asText()).chaveInterna());
        return List.of(new IdentidadeDeMensagemDoCanal(provedor(),config.instancia(),"PROCESSAMENTO",n.toString()).chaveInterna());
    }
    @Override public Optional<IdentidadeDeMensagemDoCanal> identidadeDaMensagem(String payload,String interna) {
        JsonNode n=raiz(payload);String id=texto(n.path("data").path("key"),"id");
        return nossa(n)&&id!=null&&identidade(id).chaveInterna().equals(interna)?Optional.of(identidade(id)):Optional.empty();
    }
    @Override public List<StatusDeEntregaDoCanal> statusDeEntrega(String payload) { return List.of(); }
    @Override public List<StatusComIdentidade> statusComIdentidade(String payload,Instant recebidoEm) {
        JsonNode n=raiz(payload),d=n.path("data");
        if(!nossa(n)||!"messages.update".equals(n.path("event").asText())||!d.path("fromMe").isBoolean()
                ||!d.path("fromMe").asBoolean()||telefone(texto(d,"remoteJid"))==null||recebidoEm==null) return List.of();
        String id=texto(d,"keyId");
        StatusEntrega estado=switch(d.path("status").asText()) {
            case "SERVER_ACK" -> StatusEntrega.ENVIADO;
            case "DELIVERY_ACK" -> StatusEntrega.ENTREGUE;
            case "READ","PLAYED" -> StatusEntrega.LIDO;
            case "ERROR" -> StatusEntrega.FALHOU;
            default -> null;
        };
        return id==null||estado==null?List.of():List.of(new StatusComIdentidade(identidade(id),estado,recebidoEm));
    }
    @Override public Traducao traduzirComDescartes(String payload) {
        JsonNode n=raiz(payload),d=n.path("data"),key=d.path("key");
        if(!nossa(n)||!"messages.upsert".equals(n.path("event").asText())) return Traducao.semDescartes(List.of());
        if(!key.path("fromMe").isBoolean()) return descarte(MotivoDeDescarte.ITEM_MALFORMADO);
        if(key.path("fromMe").asBoolean()) return Traducao.semDescartes(List.of());
        String jid=texto(key,"remoteJid"),telefone=telefone(jid),id=texto(key,"id");
        if(jid!=null&&jid.endsWith("@g.us")) return descarte(MotivoDeDescarte.GRUPO_NAO_SUPORTADO);
        if(telefone==null) return descarte(MotivoDeDescarte.IDENTIDADE_NAO_SUPORTADA);
        if(id==null||!d.path("messageTimestamp").isIntegralNumber()||!d.path("messageTimestamp").canConvertToLong()
                ||d.path("messageTimestamp").asLong()<=0) return descarte(MotivoDeDescarte.SEM_IDENTIFICADOR);
        JsonNode m=d.path("message");
        for(int i=0;i<3;i++) {
            if(m.has("ephemeralMessage")) m=m.path("ephemeralMessage").path("message");
            else if(m.has("viewOnceMessage")) m=m.path("viewOnceMessage").path("message");
            else if(m.has("viewOnceMessageV2")) m=m.path("viewOnceMessageV2").path("message");
            else if(m.has("documentWithCaptionMessage")) m=m.path("documentWithCaptionMessage").path("message");
            else break;
        }
        String tipo="TEXTO",conteudo=conteudo(m,"conversation"),midia=null,mime=null,nome=null,legenda=null,metadados=null;
        JsonNode principal=m.path("extendedTextMessage");
        if(conteudo==null) conteudo=conteudo(principal,"text");
        for(String campo:List.of("imageMessage","audioMessage","videoMessage","documentMessage")) {
            if(!m.has(campo)) continue;
            principal=m.path(campo);tipo=switch(campo){case "imageMessage"->"IMAGEM";case "audioMessage"->"AUDIO";case "videoMessage"->"VIDEO";default->"DOCUMENTO";};
            // Somente a chave, nunca URL de CDN/mediaKey, sai para o resolvedor autenticado.
            midia=key.toString();mime=texto(principal,"mimetype");nome=texto(principal,"fileName");legenda=conteudo(principal,"caption");
        }
        if(m.has("buttonsResponseMessage")) {
            principal=m.path("buttonsResponseMessage");conteudo=conteudo(principal,"selectedDisplayText");
            String opcao=texto(principal,"selectedButtonId");
            if(opcao==null||conteudo==null) return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);
            metadados=json.createObjectNode().put("buttonId",opcao).put("tipoInteracao","BOTAO").toString();
        } else if(m.has("templateButtonReplyMessage")) {
            principal=m.path("templateButtonReplyMessage");conteudo=conteudo(principal,"selectedDisplayText");String opcao=texto(principal,"selectedId");
            if(opcao==null||conteudo==null)return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);
            metadados=json.createObjectNode().put("buttonId",opcao).put("tipoInteracao","BOTAO").toString();
        } else if(m.has("listResponseMessage")) {
            principal=m.path("listResponseMessage");conteudo=conteudo(principal,"title");String opcao=texto(principal.path("singleSelectReply"),"selectedRowId");
            if(opcao==null||conteudo==null) return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);
            metadados=json.createObjectNode().put("selectedRowId",opcao).put("tipoInteracao","LISTA").toString();
        } else if(m.has("interactiveResponseMessage")) {
            // WAProto da dependencia Baileys 7.0.0-rc.9 fixada pela tag 2.3.7.
            // paramsJson e opaco: nao presumir campo id/display_text nem inventar label.
            principal=m.path("interactiveResponseMessage");conteudo=conteudo(principal.path("body"),"text");
            var nativeFlow=principal.path("nativeFlowResponseMessage");String nomeFluxo=texto(nativeFlow,"name"),parametros=conteudo(nativeFlow,"paramsJson");
            if(conteudo==null||nomeFluxo==null||parametros==null)return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);
            try {if(!json.reader().with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(parametros).isObject())return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);}
            catch(Exception e){return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);}
            metadados=json.createObjectNode().put("tipoInteracao","NATIVE_FLOW").put("nomeInteracao",nomeFluxo).toString();
        }
        if(midia==null&&conteudo==null) return descarte(MotivoDeDescarte.TIPO_NAO_SUPORTADO);
        Instant horario;
        try { horario=Instant.ofEpochSecond(d.path("messageTimestamp").asLong()); } catch(RuntimeException e) {return descarte(MotivoDeDescarte.CONTEUDO_INVALIDO);}
        return Traducao.semDescartes(List.of(new MensagemRecebidaDoCanal(identidade(id).chaveInterna(),telefone,texto(d,"pushName"),conteudo,tipo,midia,mime,nome,legenda,horario,config.instancia(),texto(principal.path("contextInfo"),"stanzaId"),metadados)));
    }
    static String telefone(String jid) {
        return jid!=null&&jid.matches("[1-9][0-9]{7,14}@s\\.whatsapp\\.net")?jid.substring(0,jid.indexOf('@')):null;
    }
    private static Traducao descarte(MotivoDeDescarte motivo) { return new Traducao(List.of(),List.of(new ItemDescartado("mensagem",motivo))); }
    @Override public Optional<RepasseParaAutomacao> repasseSemGrupos(String payload,String assinatura) {
        return config.repasseAutomacaoHabilitado()&&!traduzir(payload).isEmpty()?Optional.of(new RepasseParaAutomacao(payload,null)):Optional.empty();
    }
}
