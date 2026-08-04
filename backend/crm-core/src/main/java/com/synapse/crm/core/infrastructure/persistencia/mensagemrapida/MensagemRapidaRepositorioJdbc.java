package com.synapse.crm.core.infrastructure.persistencia.mensagemrapida;
import java.sql.ResultSet;

import org.springframework.dao.DuplicateKeyException;

import com.synapse.crm.core.application.mensagemrapida.*;
@Repository class MensagemRapidaRepositorioJdbc implements MensagemRapidaRepositorio{private final JdbcTemplate jdbc;MensagemRapidaRepositorioJdbc(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String BASE="SELECT m.id,m.atendente_id,u.nome atendente_nome,m.palavra_chave,m.conteudo,m.tipo_midia FROM mensagem_rapida m JOIN usuario u ON u.id=m.atendente_id";
 @Override public List<MensagemRapida> listar(EscopoMensagensRapidas e){TransacaoObrigatoria.exigir("listar mensagens rapidas");return e.todas()?jdbc.query(BASE+" ORDER BY u.nome,m.palavra_chave",MensagemRapidaRepositorioJdbc::mapear):jdbc.query(BASE+" WHERE m.atendente_id=? ORDER BY m.palavra_chave",MensagemRapidaRepositorioJdbc::mapear,e.usuarioId());}
 @Override public MensagemRapida criar(UUID atendente,String chave,String conteudo){TransacaoObrigatoria.exigir("criar mensagem rapida");UUID id=UUID.randomUUID();try{jdbc.update("INSERT INTO mensagem_rapida(id,atendente_id,palavra_chave,conteudo) VALUES(?,?,?,?)",id,atendente,chave,conteudo);}catch(DuplicateKeyException e){throw new PalavraChaveEmUsoException();}return porId(id).orElseThrow();}
 @Override public Optional<MensagemRapida> atualizar(UUID id,EscopoMensagensRapidas e,String chave,String conteudo){TransacaoObrigatoria.exigir("atualizar mensagem rapida");try{int n=e.todas()?jdbc.update("UPDATE mensagem_rapida SET palavra_chave=?,conteudo=? WHERE id=?",chave,conteudo,id):jdbc.update("UPDATE mensagem_rapida SET palavra_chave=?,conteudo=? WHERE id=? AND atendente_id=?",chave,conteudo,id,e.usuarioId());return n==0?Optional.empty():porId(id);}catch(DuplicateKeyException x){throw new PalavraChaveEmUsoException();}}
 @Override public boolean remover(UUID id,EscopoMensagensRapidas e){TransacaoObrigatoria.exigir("remover mensagem rapida");return(e.todas()?jdbc.update("DELETE FROM mensagem_rapida WHERE id=?",id):jdbc.update("DELETE FROM mensagem_rapida WHERE id=? AND atendente_id=?",id,e.usuarioId()))==1;}
 private Optional<MensagemRapida> porId(UUID id){return jdbc.query(BASE+" WHERE m.id=?",MensagemRapidaRepositorioJdbc::mapear,id).stream().findFirst();}
 private static MensagemRapida mapear(ResultSet r,int i)throws SQLException{return new MensagemRapida(r.getObject("id",UUID.class),r.getObject("atendente_id",UUID.class),r.getString("atendente_nome"),r.getString("palavra_chave"),r.getString("conteudo"),r.getString("tipo_midia"));}}
