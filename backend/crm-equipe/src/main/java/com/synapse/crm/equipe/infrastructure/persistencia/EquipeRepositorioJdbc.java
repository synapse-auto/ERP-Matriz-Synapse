package com.synapse.crm.equipe.infrastructure.persistencia;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.usuario.EmailDeUsuarioEmUsoException;
import com.synapse.crm.equipe.application.usuario.EquipeRepositorio;
import com.synapse.crm.equipe.application.usuario.FiltroEquipe;
import com.synapse.crm.equipe.domain.avaliacao.ResumoAvaliacoes;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
@Repository class EquipeRepositorioJdbc implements EquipeRepositorio{private final JdbcTemplate jdbc;EquipeRepositorioJdbc(JdbcTemplate j){jdbc=j;}private static final String BASE="SELECT u.id,u.nome,u.email,u.senha_hash,u.papel::text,u.status_presenca::text,u.ativo,u.telefone,u.cargo,u.foto_referencia,u.senha_alterada_em,COALESCE(d.disponivel_para_ia,FALSE) AS disponivel_para_ia FROM usuario u LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id=u.id";
 @Override public List<Usuario> listar(FiltroEquipe f){return jdbc.query(BASE+(f.incluirInativos()?"":" WHERE ativo=TRUE")+" ORDER BY nome",EquipeRepositorioJdbc::mapear);}
 @Override public Usuario criar(String n,String e,String h,PapelGerenciavel p){UUID id=UUID.randomUUID();try{jdbc.update("INSERT INTO usuario(id,nome,email,senha_hash,papel) VALUES(?,?,?,?,CAST(? AS papel_usuario))",id,n,e,h,p.name());if(p.comoPapel().recebeAtendimento())jdbc.update("INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) VALUES(?,TRUE)",id);}catch(DuplicateKeyException x){throw new EmailDeUsuarioEmUsoException();}return porId(id).orElseThrow();}
 @Override public Optional<Usuario> atualizar(UUID id,String n,String e,PapelGerenciavel p){try{int x=jdbc.update("UPDATE usuario SET nome=?,email=?,papel=CAST(? AS papel_usuario) WHERE id=? AND papel IN ('ATENDENTE','SUBGESTOR')",n,e,p.name(),id);if(x==1&&p.comoPapel().recebeAtendimento())jdbc.update("INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) VALUES(?,TRUE) ON CONFLICT (atendente_id) DO NOTHING",id);return x==0?Optional.empty():porId(id);}catch(DuplicateKeyException x){throw new EmailDeUsuarioEmUsoException();}}
 @Override public Optional<Usuario> atualizarNomeDoProprio(UUID id,String n){int x=jdbc.update("UPDATE usuario SET nome=? WHERE id=? AND ativo=TRUE",n,id);return x==0?Optional.empty():porId(id);}
 @Override public Optional<Usuario> atualizarMeuPerfil(UUID id,String n,String e,String telefone,String cargo){try{int x=jdbc.update("UPDATE usuario SET nome=?,email=?,telefone=?,cargo=? WHERE id=? AND ativo=TRUE",n,e,telefone,cargo,id);return x==0?Optional.empty():porId(id);}catch(DuplicateKeyException x){throw new EmailDeUsuarioEmUsoException();}}
 @Override public boolean atualizarFoto(UUID id,String fotoReferencia){return jdbc.update("UPDATE usuario SET foto_referencia=? WHERE id=? AND ativo=TRUE",fotoReferencia,id)==1;}
 @Override public boolean desativar(UUID id){int n=jdbc.update("UPDATE usuario SET ativo=FALSE,status_presenca='OFFLINE' WHERE id=? AND papel IN ('ATENDENTE','SUBGESTOR')",id);if(n==1)jdbc.update("INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) VALUES(?,FALSE) ON CONFLICT(atendente_id) DO UPDATE SET disponivel_para_ia=FALSE,atualizado_em=now()",id);return n==1;}
 @Override public Optional<StatusPresenca> obterPresenca(UUID id){return jdbc.query("SELECT status_presenca::text FROM usuario WHERE id=? AND ativo=TRUE",(r,i)->StatusPresenca.valueOf(r.getString(1)),id).stream().findFirst();}
 @Override public Optional<StatusPresenca> atualizarPresenca(UUID id,StatusPresenca s){int n=jdbc.update("UPDATE usuario SET status_presenca=CAST(? AS status_presenca) WHERE id=? AND ativo=TRUE",s.name(),id);return n==0?Optional.empty():Optional.of(s);}
 @Override public Optional<Boolean> atualizarDisponibilidadeParaIa(UUID id,boolean disponivel){int n=jdbc.update("INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) SELECT id,? FROM usuario WHERE id=? AND papel IN ('ATENDENTE','SUBGESTOR') /* PapelUsuario.recebeAtendimento() */ AND ativo=TRUE ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia=EXCLUDED.disponivel_para_ia,atualizado_em=now()",disponivel,id);if(n==0)return Optional.empty();return Optional.of(disponivel);}
 @Override public ResumoAvaliacoes resumirAvaliacoes(){BigDecimal media=jdbc.queryForObject("SELECT COALESCE(round(avg(nota),2),0) FROM avaliacao",BigDecimal.class);Long total=jdbc.queryForObject("SELECT count(*) FROM avaliacao",Long.class);var por=jdbc.query("SELECT u.id,u.nome,round(avg(a.nota),2) media,count(*) total FROM avaliacao a JOIN usuario u ON u.id=a.atendente_id GROUP BY u.id,u.nome ORDER BY u.nome",(r,i)->new ResumoAvaliacoes.PorAtendente(r.getObject("id",UUID.class),r.getString("nome"),r.getBigDecimal("media"),r.getLong("total")));return new ResumoAvaliacoes(media,total,por);}
 @Override public Optional<Usuario> porId(UUID id){return jdbc.query(BASE+" WHERE id=?",EquipeRepositorioJdbc::mapear,id).stream().findFirst();}
 @Override public boolean definirSenhaProvisoria(UUID id,String novoHash){int n=jdbc.update("UPDATE usuario SET senha_hash=?,senha_alterada_em=NULL WHERE id=? AND papel IN ('ATENDENTE','SUBGESTOR')",novoHash,id);return n==1;}
 private static Usuario mapear(ResultSet r,int i)throws SQLException{java.sql.Timestamp alteradaEm=r.getTimestamp("senha_alterada_em");return new Usuario(r.getObject("id",UUID.class),r.getString("nome"),r.getString("email"),r.getString("senha_hash"),PapelUsuario.valueOf(r.getString("papel")),StatusPresenca.valueOf(r.getString("status_presenca")),r.getBoolean("ativo"),r.getBoolean("disponivel_para_ia"),r.getString("telefone"),r.getString("cargo"),r.getString("foto_referencia"),alteradaEm==null?null:alteradaEm.toInstant());}}
