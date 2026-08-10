"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { X } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import type { TomDePill } from "@/components/ui/pill-de-status";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { cancelarMensagemProgramada, listarMensagensProgramadas } from "@/lib/suporte/api";
import type { MensagemProgramada, StatusMensagemProgramada } from "@/lib/suporte/types";

import { FormularioMensagemProgramada } from "./formulario-mensagem-programada";

const TOM_DO_STATUS: Record<StatusMensagemProgramada, TomDePill> = {
  AGENDADA: "atencao",
  ENVIADA: "sucesso",
  CANCELADA: "neutro",
};

export function PaginaMensagensProgramadas() {
  const t = useTextos().mensagensProgramadas;
  const gestor = useAuthStore((s) => s.papel) !== "ATENDENTE";
  const cache = useQueryClient();
  const [novo, setNovo] = useState(false);
  const [edicao, setEdicao] = useState<MensagemProgramada | null>(null);
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [status, setStatus] = useState<StatusMensagemProgramada | "">("");
  const [pagina, setPagina] = useState(0);

  const consulta = useQuery({
    queryKey: ["mensagens-programadas", inicio, fim, status, pagina],
    queryFn: () =>
      listarMensagensProgramadas({
        inicio: inicio ? new Date(`${inicio}T00:00:00`).toISOString() : undefined,
        fim: fim ? new Date(`${fim}T23:59:59`).toISOString() : undefined,
        status: status || undefined,
        pagina,
      }),
  });
  const cancelar = useMutation({
    mutationFn: cancelarMensagemProgramada,
    onSuccess: () => cache.invalidateQueries({ queryKey: ["mensagens-programadas"] }),
  });

  function mudarFiltro(atualizar: () => void) {
    atualizar();
    setPagina(0);
  }

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setNovo(true)}>{t.nova}</Button>
      </header>

      <div className="flex flex-wrap gap-3 rounded-lg border p-3">
        <label className="text-sm">
          {t.filtros.inicio}
          <Input type="date" value={inicio} onChange={(e) => mudarFiltro(() => setInicio(e.target.value))} />
        </label>
        <label className="text-sm">
          {t.filtros.fim}
          <Input type="date" value={fim} onChange={(e) => mudarFiltro(() => setFim(e.target.value))} />
        </label>
        <label className="text-sm">
          {t.filtros.status}
          <select
            className="ml-2 h-8 rounded-lg border bg-background px-2"
            value={status}
            onChange={(e) => mudarFiltro(() => setStatus(e.target.value as StatusMensagemProgramada | ""))}
          >
            <option value="">{t.filtros.todos}</option>
            <option value="AGENDADA">{t.status.agendada}</option>
            <option value="ENVIADA">{t.status.enviada}</option>
            <option value="CANCELADA">{t.status.cancelada}</option>
          </select>
        </label>
      </div>

      {consulta.isLoading ? (
        <p>{t.carregando}</p>
      ) : consulta.isError ? (
        <p className="text-destructive">{t.erro}</p>
      ) : !consulta.data?.mensagens.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="space-y-2">
          {consulta.data.mensagens.map((mensagem) => (
            <CardDeMensagemProgramada
              key={mensagem.id}
              mensagem={mensagem}
              mostrarAtendente={gestor}
              textos={t}
              onEditar={() => setEdicao(mensagem)}
              onCancelar={() => cancelar.mutate(mensagem.id)}
            />
          ))}
        </div>
      )}

      <div className="flex justify-end gap-2">
        <Button variant="outline" disabled={!pagina} onClick={() => setPagina((p) => p - 1)}>
          {t.paginacao.anterior}
        </Button>
        <Button
          variant="outline"
          disabled={!consulta.data?.temMais}
          onClick={() => setPagina((p) => p + 1)}
        >
          {t.paginacao.proxima}
        </Button>
      </div>

      <FormularioMensagemProgramada aberto={novo} onFechar={() => setNovo(false)} />
      {edicao && (
        <FormularioMensagemProgramada aberto existente={edicao} onFechar={() => setEdicao(null)} />
      )}
    </div>
  );
}

type TextosMensagensProgramadas = ReturnType<typeof useTextos>["mensagensProgramadas"];

function CardDeMensagemProgramada({
  mensagem,
  mostrarAtendente,
  textos,
  onEditar,
  onCancelar,
}: {
  mensagem: MensagemProgramada;
  mostrarAtendente: boolean;
  textos: TextosMensagensProgramadas;
  onEditar: () => void;
  onCancelar: () => void;
}) {
  const editavel = mensagem.status === "AGENDADA";

  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card p-3">
      <AvatarIniciais
        id={mensagem.leadId}
        nome={mensagem.leadNome}
        className="flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
      />
      <span className="w-36 shrink-0 truncate text-sm font-bold">{mensagem.leadNome}</span>

      <p className="min-w-0 flex-1 truncate text-sm text-muted-foreground">{mensagem.conteudo}</p>

      <p className="w-28 shrink-0 text-xs font-medium text-muted-foreground">
        {new Date(mensagem.dataEnvio).toLocaleString("pt-BR")}
      </p>

      <PillDeStatus tom={TOM_DO_STATUS[mensagem.status]} className="shrink-0">
        {textos.status[mensagem.status.toLowerCase() as "agendada" | "enviada" | "cancelada"]}
      </PillDeStatus>

      {mostrarAtendente && (
        <div className="flex w-32 shrink-0 items-center gap-2">
          <AvatarIniciais
            id={mensagem.atendenteId}
            nome={mensagem.atendenteNome}
            className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
          />
          <span className="truncate text-xs font-medium">{mensagem.atendenteNome}</span>
        </div>
      )}

      {editavel && (
        <div className="flex shrink-0 gap-1">
          <Button size="sm" variant="outline" onClick={onEditar}>
            {textos.editar}
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="size-8 text-destructive hover:text-destructive"
            aria-label={`${textos.cancelar} ${mensagem.leadNome}`}
            onClick={onCancelar}
          >
            <X className="size-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
