"use client";

import {useState} from "react";
import {Button} from "@/components/ui/button";
import {Switch} from "@/components/ui/switch";
import {Dialog,DialogContent,DialogDescription,DialogFooter,DialogHeader,DialogTitle} from "@/components/ui/dialog";
import {useTextos} from "@/lib/config/textos-provider";
import {useClassificarUsuarioTeste} from "@/lib/equipe/use-equipe";
import type {UsuarioEquipe} from "@/lib/equipe/types";
import {ErroDeApi} from "@/lib/api/errors";

/** Nenhuma gravacao ao alternar o switch; confirmar e obrigatorio e o backend revalida vinculos. */
export function DialogoClassificacaoTeste({usuario,onFechar,onSalvo}:{usuario:UsuarioEquipe;onFechar:()=>void;onSalvo:()=>void}) {
  const {usuariosTeste:t,gestao}=useTextos();
  const [teste,setTeste]=useState(usuario.usuarioTeste===true);
  const salvar=useClassificarUsuarioTeste();
  const problema=salvar.error instanceof ErroDeApi?salvar.error.problema:null;
  return <Dialog open onOpenChange={aberto=>{if(!aberto&&!salvar.isPending)onFechar();}}>
    <DialogContent className="max-h-[90vh] overflow-y-auto">
      <DialogHeader><DialogTitle>{t.alterar}</DialogTitle><DialogDescription>{t.impacto}</DialogDescription></DialogHeader>
      <p className="break-words font-semibold">{usuario.nome}</p>
      <div className="flex items-center gap-3"><Switch aria-label={t.rotulo} checked={teste} onCheckedChange={setTeste} disabled={salvar.isPending}/><span>{t.rotulo}</span></div>
      {salvar.isError&&<div role="alert" className="space-y-2 text-sm text-destructive">
        <p>{problema?.codigo==="USUARIO_TESTE_COM_VINCULOS"?t.comVinculos:t.erro}</p>
        {problema?.vinculos&&<ul className="space-y-1">{problema.vinculos.map(v=><li key={`${v.recurso}:${v.registroId}`} className="break-all font-mono text-xs">{v.recurso}: {v.registroId}</li>)}</ul>}
      </div>}
      <DialogFooter><Button variant="outline" disabled={salvar.isPending} onClick={onFechar}>{gestao.formulario.cancelar}</Button>
        <Button disabled={salvar.isPending||teste===(usuario.usuarioTeste===true)} onClick={()=>salvar.mutate({id:usuario.id,usuarioTeste:teste},{onSuccess:onSalvo})}>{t.confirmar}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
