"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Bot, Check, Trash2 } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
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
          <SeletorData
            valor={inicio}
            placeholder={textos.filtros.inicio}
            onChange={(valor) => mudarFiltro(() => setInicio(valor))}
          />
        </label>
        <label className="text-sm">
          {textos.filtros.fim}
          <SeletorData
            valor={fim}
            placeholder={textos.filtros.fim}
            onChange={(valor) => mudarFiltro(() => setFim(valor))}
          />
        </label>
        <label className="text-sm">
          {textos.filtros.status}
          <Seletor
            className="ml-2"
            valor={status}
            placeholder={textos.filtros.todos}
            opcoes={[
              { valor: "PENDENTE", rotulo: textos.status.pendente },
              { valor: "CONCLUIDO", rotulo: textos.status.concluido },
            ]}
            onChange={(valor) => mudarFiltro(() => setStatus(valor as StatusLembrete | ""))}
          />
        </label>
      </div>

      {consulta.isLoading ? (
        <p>{textos.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento
          mensagem={textos.erro}
          onTentarNovamente={() => consulta.refetch()}
        />
      ) : !consulta.data?.lembretes.length ? (
        <p className="text-muted-foreground">{textos.vazio}</p>
      ) : (
        <TabelaDeLembretes
          itens={consulta.data.lembretes}
          mostrarAtendente={gestor}
          textos={textos}
          onConcluir={(item) => atualizar.mutate({ item, novo: "CONCLUIDO" })}
          onRemover={(id) => remover.mutate(id)}
        />
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

function TabelaDeLembretes({
  itens,
  mostrarAtendente,
  textos,
  onConcluir,
  onRemover,
}: {
  itens: Lembrete[];
  mostrarAtendente: boolean;
  textos: TextosLembretes;
  onConcluir: (item: Lembrete) => void;
  onRemover: (id: string) => void;
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card">
      <table className="w-full text-sm">
        <thead className="bg-muted/60">
          <tr>
            <th className="w-10 px-4 py-3" />
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.texto}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.lead}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.dataHora}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.status}
            </th>
            {mostrarAtendente && (
              <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
                {textos.colunas.atendente}
              </th>
            )}
            <th className="px-4 py-3 text-right text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.acoes}
            </th>
          </tr>
        </thead>
        <tbody>
          {itens.map((item) => {
            const concluido = item.status === "CONCLUIDO";
            return (
              <tr key={item.id} className="border-t border-border">
                <td className="px-4 py-3">
                  <button
                    type="button"
                    aria-label={textos.concluir}
                    disabled={concluido}
                    onClick={() => onConcluir(item)}
                    className={`flex size-6 items-center justify-center rounded-full border-2 ${
                      concluido ? "border-cor-sucesso bg-cor-sucesso" : "border-muted-foreground/40"
                    }`}
                  >
                    {concluido && <Check className="size-3.5 text-white" />}
                  </button>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {item.origemAutomatica && (
                      <PillDeStatus tom="ia" icone={<Bot className="size-3" />} className="shrink-0">
                        {textos.automatico}
                      </PillDeStatus>
                    )}
                    <p
                      className={`truncate font-medium ${concluido ? "text-muted-foreground line-through" : "text-foreground"}`}
                    >
                      {item.texto}
                    </p>
                  </div>
                </td>
                <td className="px-4 py-3">
                  {item.leadNome ? (
                    <div className="flex min-w-0 items-center gap-2">
                      <AvatarIniciais
                        id={item.leadId}
                        nome={item.leadNome}
                        className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
                      />
                      <span className="truncate text-xs font-medium">{item.leadNome}</span>
                    </div>
                  ) : (
                    <span className="text-xs italic text-muted-foreground">{textos.semVinculo}</span>
                  )}
                </td>
                <td className="px-4 py-3 text-xs font-medium text-muted-foreground">
                  {new Date(item.dataHora).toLocaleString("pt-BR")}
                </td>
                <td className="px-4 py-3">
                  <PillDeStatus tom={concluido ? "sucesso" : "atencao"}>
                    {concluido ? textos.status.concluido : textos.status.pendente}
                  </PillDeStatus>
                </td>
                {mostrarAtendente && (
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-2">
                      <AvatarIniciais
                        id={item.atendenteId}
                        nome={item.atendenteNome}
                        className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
                      />
                      <span className="truncate text-xs font-medium">{item.atendenteNome}</span>
                    </div>
                  </td>
                )}
                <td className="px-4 py-3 text-right">
                  <Button
                    size="icon"
                    variant="ghost"
                    className="size-8 text-destructive hover:text-destructive"
                    aria-label={`${textos.remover} ${item.texto}`}
                    onClick={() => onRemover(item.id)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
