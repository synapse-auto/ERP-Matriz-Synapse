"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { dataHoraCompleta, SeletorDataHora } from "@/components/ui/seletor-data-hora";
import { Seletor } from "@/components/ui/seletor";
import { Textarea } from "@/components/ui/textarea";
import { listarAtendimentos } from "@/lib/atendimento/api";
import { useTextos } from "@/lib/config/textos-provider";
import { criarLembrete } from "@/lib/suporte/api";

interface Props {
  aberto: boolean;
  leadId?: string;
  leadNome?: string;
  onFechar: () => void;
}

export function FormularioLembrete({ aberto, leadId, leadNome, onFechar }: Props) {
  const textos = useTextos().lembretes;
  const cache = useQueryClient();
  const [leadSelecionado, setLeadSelecionado] = useState(leadId ?? "");
  const [texto, setTexto] = useState("");
  const [dataHora, setDataHora] = useState("");
  const atendimentos = useQuery({
    queryKey: ["atendimentos", "TODOS"],
    queryFn: () => listarAtendimentos("TODOS"),
    enabled: aberto && !leadId,
  });
  const leads = useMemo(() => {
    const unicos = new Map((atendimentos.data ?? []).map((item) => [item.leadId, item.leadNome]));
    return [...unicos.entries()];
  }, [atendimentos.data]);
  const criar = useMutation({
    mutationFn: criarLembrete,
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ["lembretes"] });
      setTexto("");
      setDataHora("");
      if (!leadId) setLeadSelecionado("");
      onFechar();
    },
  });

  const leadEfetivo = leadId ?? leadSelecionado;
  function salvar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!leadEfetivo || !texto.trim() || !dataHoraCompleta(dataHora)) return;
    criar.mutate({ leadId: leadEfetivo, texto: texto.trim(), dataHora: new Date(dataHora).toISOString() });
  }

  return (
    <Dialog open={aberto} onOpenChange={(novo) => !novo && onFechar()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{textos.formulario.titulo}</DialogTitle></DialogHeader>
        <form className="space-y-3" onSubmit={salvar}>
          <label className="block space-y-1 text-sm">
            <span>{textos.formulario.lead}</span>
            {leadId ? (
              <Input value={leadNome ?? leadId} disabled />
            ) : (
              <Seletor
                obrigatorio
                valor={leadSelecionado}
                placeholder={textos.formulario.selecionarLead}
                opcoes={leads.map(([id, nome]) => ({ valor: id, rotulo: nome }))}
                onChange={setLeadSelecionado}
              />
            )}
          </label>
          <label className="block space-y-1 text-sm">
            <span>{textos.formulario.dataHora}</span>
            <SeletorDataHora
              valor={dataHora}
              placeholderData={textos.formulario.selecionarData}
              rotuloHora={textos.formulario.hora}
              rotuloMinuto={textos.formulario.minuto}
              obrigatorio
              onChange={setDataHora}
            />
          </label>
          <label className="block space-y-1 text-sm">
            <span>{textos.formulario.texto}</span>
            <Textarea value={texto} onChange={(e) => setTexto(e.target.value)} required />
          </label>
          {criar.isError && <p role="alert" className="text-sm text-destructive">{textos.formulario.erro}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>{textos.formulario.cancelar}</Button>
            <Button type="submit" disabled={criar.isPending}>{textos.formulario.salvar}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
