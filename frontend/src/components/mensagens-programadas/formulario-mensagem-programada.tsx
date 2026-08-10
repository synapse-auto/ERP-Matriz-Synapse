"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { Textarea } from "@/components/ui/textarea";
import { listarAtendimentos } from "@/lib/atendimento/api";
import { useTextos } from "@/lib/config/textos-provider";
import { criarMensagemProgramada, editarMensagemProgramada } from "@/lib/suporte/api";
import type { MensagemProgramada } from "@/lib/suporte/types";

interface Props { aberto: boolean; leadId?: string; leadNome?: string; conteudoInicial?: string;
  existente?: MensagemProgramada; onFechar: () => void; onSalvo?: () => void; }

function paraLocal(iso?: string) { if (!iso) return ""; const d = new Date(iso); const local = new Date(d.getTime() - d.getTimezoneOffset() * 60_000); return local.toISOString().slice(0, 16); }

export function FormularioMensagemProgramada({ aberto, leadId, leadNome, conteudoInicial, existente, onFechar, onSalvo }: Props) {
  const textos = useTextos().mensagensProgramadas.formulario; const cache = useQueryClient();
  const [leadSelecionado, setLeadSelecionado] = useState(leadId ?? existente?.leadId ?? "");
  const [conteudo, setConteudo] = useState(existente?.conteudo ?? conteudoInicial ?? "");
  const [dataEnvio, setDataEnvio] = useState(paraLocal(existente?.dataEnvio));
  const atendimentos = useQuery({ queryKey: ["atendimentos", "TODOS"], queryFn: () => listarAtendimentos("TODOS"), enabled: aberto && !leadId && !existente });
  const leads = useMemo(() => [...new Map((atendimentos.data ?? []).map((a) => [a.leadId, a.leadNome])).entries()], [atendimentos.data]);
  const salvar = useMutation({ mutationFn: () => existente
    ? editarMensagemProgramada(existente.id, { conteudo: conteudo.trim(), dataEnvio: new Date(dataEnvio).toISOString() })
    : criarMensagemProgramada({ leadId: leadId ?? leadSelecionado, conteudo: conteudo.trim(), dataEnvio: new Date(dataEnvio).toISOString() }),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ["mensagens-programadas"] }); onSalvo?.(); onFechar(); } });
  return <Dialog open={aberto} onOpenChange={(v) => !v && onFechar()}><DialogContent><DialogHeader><DialogTitle>{existente ? textos.tituloEditar : textos.tituloCriar}</DialogTitle></DialogHeader>
    <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); if ((leadId ?? leadSelecionado) && conteudo.trim() && dataEnvio) salvar.mutate(); }}>
      <label className="block space-y-1 text-sm"><span>{textos.lead}</span>{leadId || existente ? <Input disabled value={leadNome ?? existente?.leadNome ?? leadId} /> : <Seletor obrigatorio valor={leadSelecionado} placeholder={textos.selecionarLead} opcoes={leads.map(([id, nome]) => ({ valor: id, rotulo: nome }))} onChange={setLeadSelecionado} />}</label>
      <label className="block space-y-1 text-sm"><span>{textos.dataEnvio}</span><Input required type="datetime-local" value={dataEnvio} onChange={(e) => setDataEnvio(e.target.value)} /></label>
      <label className="block space-y-1 text-sm"><span>{textos.conteudo}</span><Textarea required value={conteudo} onChange={(e) => setConteudo(e.target.value)} /></label>
      {salvar.isError && <p role="alert" className="text-sm text-destructive">{textos.erro}</p>}
      <DialogFooter><Button type="button" variant="outline" onClick={onFechar}>{textos.cancelar}</Button><Button type="submit" disabled={salvar.isPending}>{textos.salvar}</Button></DialogFooter>
    </form></DialogContent></Dialog>;
}
