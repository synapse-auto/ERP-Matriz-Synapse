"use client";

import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { BolhaContato } from "@/components/atendimentos/bolha-contato";
import { useTextos } from "@/lib/config/textos-provider";
import { TextosSchema } from "@/lib/config/schema";
import { abrirConversaDireta, enviarContatoChat, listarContatosChat } from "@/lib/chat-interno/api";
import type { ChatMensagem } from "@/lib/chat-interno/types";

export function CompartilharContatoChat({ conversaId, disabled }: { conversaId:string; disabled?:boolean }) {
  const catalogo=useTextos(); const textos=catalogo.chatInterno;
  const t=textos.contatoCompartilhado ?? TextosSchema.shape.chatInterno.shape.contatoCompartilhado.parse(undefined);
  const cache=useQueryClient();
  const [aberto,setAberto]=useState(false); const [tipo,setTipo]=useState("INTERNO");
  const [usuarioId,setUsuarioId]=useState(""); const [nome,setNome]=useState(""); const [numeros,setNumeros]=useState("");
  const chave=useRef<{corpo:string;id:string}|null>(null); const emEnvio=useRef(false);
  const contatos=useQuery({queryKey:["chat-interno","contatos"],queryFn:listarContatosChat,enabled:aberto});
  const enviar=useMutation({mutationFn:async()=>{
    const corpo=tipo==="INTERNO"?{usuarioId}:{nome,telefones:numeros.split(/\r?\n/).map(n=>n.trim()).filter(Boolean)};
    const serializado=JSON.stringify(corpo);
    if(chave.current?.corpo!==serializado)chave.current={corpo:serializado,id:crypto.randomUUID()};
    return enviarContatoChat(conversaId,corpo,chave.current.id);
  },onSuccess:()=>{setAberto(false);setNome("");setNumeros("");setUsuarioId("");chave.current=null;void cache.invalidateQueries({queryKey:["chat-interno"]});}});
  async function confirmar(){if(emEnvio.current)return;emEnvio.current=true;try{await enviar.mutateAsync();}catch{/* feedback pela mutation, conteúdo preservado */}finally{emEnvio.current=false;}}
  return <>
    <Button type="button" variant="ghost" size="icon" aria-label={t.compartilhar} disabled={disabled} onClick={()=>setAberto(true)}><UserRound aria-hidden className="size-(--tamanho-icone-interface)"/></Button>
    <Dialog open={aberto} onOpenChange={valor=>{if(!enviar.isPending)setAberto(valor);}}><DialogContent>
      <DialogHeader><DialogTitle>{t.titulo}</DialogTitle><DialogDescription>{t.descricao}</DialogDescription></DialogHeader>
      <Select value={tipo} onValueChange={v=>setTipo(v ?? "INTERNO")} disabled={enviar.isPending}><SelectTrigger aria-label={t.titulo}><SelectValue>{tipo==="INTERNO"?t.interno:t.externo}</SelectValue></SelectTrigger><SelectContent><SelectItem value="INTERNO">{t.interno}</SelectItem><SelectItem value="EXTERNO">{t.externo}</SelectItem></SelectContent></Select>
      {tipo==="INTERNO"?<>
        {contatos.isPending?<p role="status">{textos.carregando}</p>:contatos.isError?<p role="alert">{textos.erro}</p>:<Select value={usuarioId} onValueChange={v=>setUsuarioId(v ?? "")} disabled={enviar.isPending}><SelectTrigger aria-label={t.interno}><SelectValue placeholder={textos.selecionarPessoa}/></SelectTrigger><SelectContent>{contatos.data?.map(c=><SelectItem key={c.id} value={c.id}>{c.nome}</SelectItem>)}</SelectContent></Select>}
      </>:<><label>{t.nome}<Input value={nome} onChange={e=>setNome(e.target.value)} disabled={enviar.isPending}/></label><label>{t.telefones}<Textarea value={numeros} onChange={e=>setNumeros(e.target.value)} disabled={enviar.isPending}/></label></>}
      {enviar.isError&&<p role="alert" className="text-destructive">{textos.erroEnviar}</p>}
      <DialogFooter><Button variant="ghost" disabled={enviar.isPending} onClick={()=>setAberto(false)}>{textos.encaminharCancelar}</Button><Button disabled={enviar.isPending || (tipo==="INTERNO"?!usuarioId:!nome.trim())} onClick={()=>void confirmar()}>{enviar.isPending?textos.carregando:t.compartilhar}</Button></DialogFooter>
    </DialogContent></Dialog>
  </>;
}

export function ContatoCompartilhadoChat({mensagem}:{mensagem:ChatMensagem}) {
  const catalogo=useTextos(); const router=useRouter();
  const t=catalogo.chatInterno.contatoCompartilhado ?? TextosSchema.shape.chatInterno.shape.contatoCompartilhado.parse(undefined);
  const json=typeof mensagem.midiaMetadados==="string"?mensagem.midiaMetadados:JSON.stringify(mensagem.midiaMetadados ?? null);
  const id=usuarioDoContato(json);
  const contatos=useQuery({queryKey:["chat-interno","contatos"],queryFn:listarContatosChat,enabled:Boolean(id)});
  const abrir=useMutation({mutationFn:()=>abrirConversaDireta(id!),onSuccess:r=>router.push(`/chat-interno?conversaId=${encodeURIComponent(r.id)}`)});
  return <div className="space-y-2"><BolhaContato midiaMetadados={json} textos={{...catalogo.atendimentos.media,...catalogo.atendimentos.mensagem.acoes,abrirConversa:undefined}}/>
    {id&&contatos.data?.some(c=>c.id===id)&&<Button variant="outline" className="text-foreground" disabled={abrir.isPending} onClick={()=>abrir.mutate()}>{t.abrir}</Button>}
    {abrir.isError&&<p role="alert" className="text-destructive">{catalogo.chatInterno.erroAbrirConversa}</p>}
  </div>;
}

export function usuarioDoContato(json:string):string|null {
  try{const contato=JSON.parse(json)?.contatos?.[0];return contato?.origem==="INTERNO"&&typeof contato.usuarioId==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(contato.usuarioId)?contato.usuarioId:null;}catch{return null;}
}
