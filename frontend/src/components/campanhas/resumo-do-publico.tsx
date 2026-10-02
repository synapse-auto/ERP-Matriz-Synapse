"use client";

import { useState } from "react";
import { Eye, UserCheck, UserMinus, Users } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Skeleton } from "@/components/ui/skeleton";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import { useExcluidosDoPublico } from "@/lib/campanhas/hooks";
import type { FiltroDePublico, MotivoDoDestinatario, PreviaDoPublico } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

import { AvisoDeAtencao } from "./aviso";

export function EsqueletoDoResumo() {
  const rotulo = useTextos().campanhas.passoPublico.carregando;
  return (
    <div className="grid gap-3 sm:grid-cols-3" role="status" aria-label={rotulo} aria-busy>
      {Array.from({ length: 3 }, (_, indice) => (
        <Skeleton key={indice} className="h-20" />
      ))}
    </div>
  );
}

function Contagem({ icone: Icone, rotulo, valor, destaque }: { icone: typeof Users; rotulo: string; valor: number; destaque?: boolean }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <Icone className="size-4" aria-hidden />
        {rotulo}
      </p>
      <p className={`mt-1 text-2xl font-bold tabular-nums ${destaque ? "text-cor-sucesso" : ""}`}>{formatarNumero(valor)}</p>
    </div>
  );
}

function ListaDeExcluidos({ filtro, motivo }: { filtro: Partial<FiltroDePublico>; motivo: MotivoDoDestinatario }) {
  const textos = useTextos().campanhas.passoPublico;
  const consulta = useExcluidosDoPublico(filtro, motivo);
  if (consulta.isPending) return <Skeleton className="h-32 w-full" />;
  if (consulta.isError) return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => consulta.refetch()} />;
  if (consulta.data.length === 0) return <p className="text-sm text-muted-foreground">{textos.excluidosVazio}</p>;
  return (
    <ul className="max-h-80 divide-y divide-border overflow-y-auto rounded-lg border border-border text-sm">
      {consulta.data.map((contato) => (
        <li key={contato.leadId} className="flex items-center justify-between gap-3 px-3 py-2">
          <span className="truncate font-medium">{contato.nome ?? "-"}</span>
          <span className="tabular-nums text-muted-foreground">{contato.telefone ?? "-"}</span>
        </li>
      ))}
    </ul>
  );
}

interface Props {
  previa: PreviaDoPublico;
  filtro: Partial<FiltroDePublico>;
}

export function ResumoDoPublico({ previa, filtro }: Props) {
  const textos = useTextos().campanhas;
  const passo = textos.passoPublico;
  const [motivoAberto, setMotivoAberto] = useState<MotivoDoDestinatario | null>(null);
  const motivos = Object.entries(previa.excluidosPorMotivo) as [MotivoDoDestinatario, number][];

  return (
    <section className="space-y-4" aria-label={passo.titulo}>
      <div className="grid gap-3 sm:grid-cols-3">
        <Contagem icone={Users} rotulo={passo.total} valor={previa.total} />
        <Contagem icone={UserCheck} rotulo={passo.vaoReceber} valor={previa.elegiveis} destaque />
        <Contagem icone={UserMinus} rotulo={passo.excluidos} valor={previa.excluidos} />
      </div>
      {previa.elegiveis === 0 && (
        <AvisoDeAtencao destacado papel="alert">
          {passo.publicoVazio}
        </AvisoDeAtencao>
      )}
      <div className="space-y-2">
        <h3 className="text-sm font-bold">{passo.excluidosPorMotivo}</h3>
        {motivos.length === 0 ? (
          <p className="text-sm text-muted-foreground">{passo.nenhumExcluido}</p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border bg-card text-sm">
            {motivos.map(([motivo, quantidade]) => (
              <li key={motivo} className="flex items-center justify-between gap-3 px-3 py-2">
                <span>{textos.motivos[motivo]}</span>
                <span className="flex items-center gap-3">
                  <span className="font-bold tabular-nums">{formatarNumero(quantidade)}</span>
                  <Button type="button" variant="ghost" size="xs" onClick={() => setMotivoAberto(motivo)}>
                    <Eye aria-hidden />
                    {passo.verExcluidos}
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <Dialog open={motivoAberto !== null} onOpenChange={(aberto) => !aberto && setMotivoAberto(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {interpolarCatalogo(passo.excluidosTitulo, { motivo: motivoAberto ? textos.motivos[motivoAberto] : "" })}
            </DialogTitle>
            <DialogDescription>{passo.excluidosDescricao}</DialogDescription>
          </DialogHeader>
          {motivoAberto && <ListaDeExcluidos filtro={filtro} motivo={motivoAberto} />}
        </DialogContent>
      </Dialog>
    </section>
  );
}
