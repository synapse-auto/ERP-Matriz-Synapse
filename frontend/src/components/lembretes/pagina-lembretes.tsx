"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Bot, Check, Trash2 } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
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
    queryFn: () =>
      listarLembretes({
        inicio: inicio ? new Date(`${inicio}T00:00:00`).toISOString() : undefined,
        fim: fim ? new Date(`${fim}T23:59:59`).toISOString() : undefined,
        status: status || undefined,
        pagina,
      }),
  });
  const atualizar = useMutation({
    mutationFn: ({ item, novo }: { item: Lembrete; novo: StatusLembrete }) =>
      atualizarLembrete(item, novo),
    onSuccess: () => cache.invalidateQueries({ queryKey: ["lembretes"] }),
  });
  const remover = useMutation({
    mutationFn: removerLembrete,
    onSuccess: () => cache.invalidateQueries({ queryKey: ["lembretes"] }),
  });

  function mudarFiltro(atualizar: () => void) {
    atualizar();
    setPagina(0);
  }

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{textos.titulo}</h1>
          <p className="text-sm text-muted-foreground">{textos.descricao}</p>
        </div>
        <Button onClick={() => setAberto(true)}>{textos.novo}</Button>
      </header>

      <div className="flex flex-wrap gap-3 rounded-lg border p-3">
        <label className="text-sm">
          {textos.filtros.inicio}
          <Input
            type="date"
            value={inicio}
            onChange={(e) => mudarFiltro(() => setInicio(e.target.value))}
          />
        </label>
        <label className="text-sm">
          {textos.filtros.fim}
          <Input type="date" value={fim} onChange={(e) => mudarFiltro(() => setFim(e.target.value))} />
        </label>
        <label className="text-sm">
          {textos.filtros.status}
          <select
            className="ml-2 h-8 rounded-lg border bg-background px-2"
            value={status}
            onChange={(e) => mudarFiltro(() => setStatus(e.target.value as StatusLembrete | ""))}
          >
            <option value="">{textos.filtros.todos}</option>
            <option value="PENDENTE">{textos.status.pendente}</option>
            <option value="CONCLUIDO">{textos.status.concluido}</option>
          </select>
        </label>
      </div>

      {consulta.isLoading ? (
        <p>{textos.carregando}</p>
      ) : consulta.isError ? (
        <p role="alert" className="text-destructive">
          {textos.erro}
        </p>
      ) : !consulta.data?.lembretes.length ? (
        <p className="text-muted-foreground">{textos.vazio}</p>
      ) : (
        <div className="space-y-2">
          {consulta.data.lembretes.map((item) => (
            <CardDeLembrete
              key={item.id}
              item={item}
              mostrarAtendente={gestor}
              textos={textos}
              onConcluir={() => atualizar.mutate({ item, novo: "CONCLUIDO" })}
              onRemover={() => remover.mutate(item.id)}
            />
          ))}
        </div>
      )}

      <div className="flex justify-end gap-2">
        <Button variant="outline" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
          {textos.paginacao.anterior}
        </Button>
        <Button
          variant="outline"
          disabled={!consulta.data?.temMais}
          onClick={() => setPagina((p) => p + 1)}
        >
          {textos.paginacao.proxima}
        </Button>
      </div>

      <FormularioLembrete aberto={aberto} onFechar={() => setAberto(false)} />
    </div>
  );
}

type TextosLembretes = ReturnType<typeof useTextos>["lembretes"];

function CardDeLembrete({
  item,
  mostrarAtendente,
  textos,
  onConcluir,
  onRemover,
}: {
  item: Lembrete;
  mostrarAtendente: boolean;
  textos: TextosLembretes;
  onConcluir: () => void;
  onRemover: () => void;
}) {
  const concluido = item.status === "CONCLUIDO";

  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card p-3">
      <button
        type="button"
        aria-label={textos.concluir}
        disabled={concluido}
        onClick={onConcluir}
        className={`flex size-6 shrink-0 items-center justify-center rounded-full border-2 ${
          concluido ? "border-cor-sucesso bg-cor-sucesso" : "border-muted-foreground/40"
        }`}
      >
        {concluido && <Check className="size-3.5 text-white" />}
      </button>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          {item.origemAutomatica && (
            <PillDeStatus tom="ia" icone={<Bot className="size-3" />}>
              {textos.automatico}
            </PillDeStatus>
          )}
          <p className={`truncate text-sm font-medium ${concluido ? "text-muted-foreground line-through" : ""}`}>
            {item.texto}
          </p>
        </div>
      </div>

      <div className="flex w-40 shrink-0 items-center gap-2">
        {item.leadNome ? (
          <>
            <AvatarIniciais
              id={item.leadId}
              nome={item.leadNome}
              className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
            />
            <span className="truncate text-xs font-medium">{item.leadNome}</span>
          </>
        ) : (
          <span className="text-xs italic text-muted-foreground">{textos.semVinculo}</span>
        )}
      </div>

      <p className="w-28 shrink-0 text-xs font-medium text-muted-foreground">
        {new Date(item.dataHora).toLocaleString("pt-BR")}
      </p>

      <PillDeStatus tom={concluido ? "sucesso" : "atencao"} className="shrink-0">
        {concluido ? textos.status.concluido : textos.status.pendente}
      </PillDeStatus>

      {mostrarAtendente && (
        <div className="flex w-32 shrink-0 items-center gap-2">
          <AvatarIniciais
            id={item.atendenteId}
            nome={item.atendenteNome}
            className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
          />
          <span className="truncate text-xs font-medium">{item.atendenteNome}</span>
        </div>
      )}

      <Button
        size="icon"
        variant="ghost"
        className="size-8 shrink-0 text-destructive hover:text-destructive"
        aria-label={`${textos.remover} ${item.texto}`}
        onClick={onRemover}
      >
        <Trash2 className="size-4" />
      </Button>
    </div>
  );
}
