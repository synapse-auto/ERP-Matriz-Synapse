"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { X } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import type { TomDePill } from "@/components/ui/pill-de-status";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
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
          <SeletorData
            valor={inicio}
            placeholder={t.filtros.inicio}
            onChange={(valor) => mudarFiltro(() => setInicio(valor))}
          />
        </label>
        <label className="text-sm">
          {t.filtros.fim}
          <SeletorData
            valor={fim}
            placeholder={t.filtros.fim}
            onChange={(valor) => mudarFiltro(() => setFim(valor))}
          />
        </label>
        <label className="text-sm">
          {t.filtros.status}
          <Seletor
            className="ml-2"
            valor={status}
            placeholder={t.filtros.todos}
            opcoes={[
              { valor: "AGENDADA", rotulo: t.status.agendada },
              { valor: "ENVIADA", rotulo: t.status.enviada },
              { valor: "CANCELADA", rotulo: t.status.cancelada },
            ]}
            onChange={(valor) =>
              mudarFiltro(() => setStatus(valor as StatusMensagemProgramada | ""))
            }
          />
        </label>
      </div>

      {consulta.isLoading ? (
        <p>{t.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />
      ) : !consulta.data?.mensagens.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <TabelaDeMensagensProgramadas
          itens={consulta.data.mensagens}
          mostrarAtendente={gestor}
          textos={t}
          onEditar={setEdicao}
          onCancelar={(id) => cancelar.mutate(id)}
        />
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

      <FormularioMensagemProgramada
        key={novo ? "novo-aberto" : "novo-fechado"}
        aberto={novo}
        onFechar={() => setNovo(false)}
      />
      {edicao && (
        <FormularioMensagemProgramada
          key={edicao.id}
          aberto
          existente={edicao}
          onFechar={() => setEdicao(null)}
        />
      )}
    </div>
  );
}

type TextosMensagensProgramadas = ReturnType<typeof useTextos>["mensagensProgramadas"];

function TabelaDeMensagensProgramadas({
  itens,
  mostrarAtendente,
  textos,
  onEditar,
  onCancelar,
}: {
  itens: MensagemProgramada[];
  mostrarAtendente: boolean;
  textos: TextosMensagensProgramadas;
  onEditar: (mensagem: MensagemProgramada) => void;
  onCancelar: (id: string) => void;
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card">
      <table className="w-full text-sm">
        <thead className="bg-muted/60">
          <tr>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.lead}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.conteudo}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.data}
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
          {itens.map((mensagem) => {
            const editavel = mensagem.status === "AGENDADA";
            return (
              <tr key={mensagem.id} className="border-t border-border">
                <td className="px-4 py-3">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <AvatarIniciais
                      id={mensagem.leadId}
                      nome={mensagem.leadNome}
                      className="flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                    />
                    <span className="truncate font-bold text-foreground">{mensagem.leadNome}</span>
                  </div>
                </td>
                <td className="max-w-xs px-4 py-3 truncate text-muted-foreground">
                  {mensagem.conteudo}
                </td>
                <td className="px-4 py-3 text-xs font-medium text-muted-foreground">
                  {new Date(mensagem.dataEnvio).toLocaleString("pt-BR")}
                </td>
                <td className="px-4 py-3">
                  <PillDeStatus tom={TOM_DO_STATUS[mensagem.status]}>
                    {textos.status[mensagem.status.toLowerCase() as "agendada" | "enviada" | "cancelada"]}
                  </PillDeStatus>
                </td>
                {mostrarAtendente && (
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-2">
                      <AvatarIniciais
                        id={mensagem.atendenteId}
                        nome={mensagem.atendenteNome}
                        className="flex size-7 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
                      />
                      <span className="truncate text-xs font-medium">{mensagem.atendenteNome}</span>
                    </div>
                  </td>
                )}
                <td className="px-4 py-3 text-right">
                  {editavel && (
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="outline" onClick={() => onEditar(mensagem)}>
                        {textos.editar}
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="size-8 text-destructive hover:text-destructive"
                        aria-label={`${textos.cancelar} ${mensagem.leadNome}`}
                        onClick={() => onCancelar(mensagem.id)}
                      >
                        <X className="size-4" />
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
