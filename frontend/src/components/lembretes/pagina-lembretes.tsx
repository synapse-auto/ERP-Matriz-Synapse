"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { atualizarLembrete, listarLembretes, removerLembrete } from "@/lib/suporte/api";
import type { Lembrete, StatusLembrete } from "@/lib/suporte/types";

import { FormularioLembrete } from "./formulario-lembrete";

export function PaginaLembretes() {
  const textos = useTextos().lembretes;
  const papel = useAuthStore((s) => s.papel);
  const gestor = papel !== "ATENDENTE";
  const cache = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [status, setStatus] = useState<StatusLembrete | "">("");
  const [pagina, setPagina] = useState(0);
  const consulta = useQuery({
    queryKey: ["lembretes", inicio, fim, status, pagina],
    queryFn: () => listarLembretes({
      inicio: inicio ? new Date(`${inicio}T00:00:00`).toISOString() : undefined,
      fim: fim ? new Date(`${fim}T23:59:59`).toISOString() : undefined,
      status: status || undefined,
      pagina,
    }),
  });
  const atualizar = useMutation({
    mutationFn: ({ item, novo }: { item: Lembrete; novo: StatusLembrete }) => atualizarLembrete(item, novo),
    onSuccess: () => cache.invalidateQueries({ queryKey: ["lembretes"] }),
  });
  const remover = useMutation({
    mutationFn: removerLembrete,
    onSuccess: () => cache.invalidateQueries({ queryKey: ["lembretes"] }),
  });

  return (
    <div className="space-y-5 p-6">
      <header className="flex items-center justify-between">
        <div><h1 className="text-xl font-semibold">{textos.titulo}</h1><p className="text-sm text-muted-foreground">{textos.descricao}</p></div>
        <Button onClick={() => setAberto(true)}>{textos.novo}</Button>
      </header>
      <div className="flex flex-wrap gap-3 rounded-lg border p-3">
        <label className="text-sm">{textos.filtros.inicio}<Input type="date" value={inicio} onChange={(e) => { setInicio(e.target.value); setPagina(0); }} /></label>
        <label className="text-sm">{textos.filtros.fim}<Input type="date" value={fim} onChange={(e) => { setFim(e.target.value); setPagina(0); }} /></label>
        <label className="text-sm">{textos.filtros.status}<select className="ml-2 h-8 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value as StatusLembrete | ""); setPagina(0); }}><option value="">{textos.filtros.todos}</option><option value="PENDENTE">{textos.status.pendente}</option><option value="CONCLUIDO">{textos.status.concluido}</option></select></label>
      </div>
      {consulta.isLoading ? <p>{textos.carregando}</p> : consulta.isError ? <p role="alert" className="text-destructive">{textos.erro}</p> : !consulta.data?.lembretes.length ? <p>{textos.vazio}</p> : (
        <div className="overflow-x-auto rounded-lg border"><table className="w-full text-sm"><thead className="bg-muted"><tr><th className="p-2 text-left">{textos.colunas.dataHora}</th><th className="p-2 text-left">{textos.colunas.lead}</th>{gestor && <th className="p-2 text-left">{textos.colunas.atendente}</th>}<th className="p-2 text-left">{textos.colunas.texto}</th><th className="p-2 text-left">{textos.colunas.status}</th><th className="p-2 text-left">{textos.colunas.acoes}</th></tr></thead><tbody>{consulta.data.lembretes.map((item) => <tr key={item.id} className="border-t"><td className="p-2">{new Date(item.dataHora).toLocaleString()}</td><td className="p-2">{item.leadNome}</td>{gestor && <td className="p-2">{item.atendenteNome}</td>}<td className="p-2">{item.texto}{item.origemAutomatica && <span className="ml-2 text-xs text-muted-foreground">{textos.automatico}</span>}</td><td className="p-2">{item.status === "PENDENTE" ? textos.status.pendente : textos.status.concluido}</td><td className="space-x-2 p-2">{item.status === "PENDENTE" && <Button size="sm" variant="outline" onClick={() => atualizar.mutate({ item, novo: "CONCLUIDO" })}>{textos.concluir}</Button>}<Button size="sm" variant="ghost" onClick={() => remover.mutate(item.id)}>{textos.remover}</Button></td></tr>)}</tbody></table></div>
      )}
      <div className="flex justify-end gap-2"><Button variant="outline" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>{textos.paginacao.anterior}</Button><Button variant="outline" disabled={!consulta.data?.temMais} onClick={() => setPagina((p) => p + 1)}>{textos.paginacao.proxima}</Button></div>
      <FormularioLembrete aberto={aberto} onFechar={() => setAberto(false)} />
    </div>
  );
}
