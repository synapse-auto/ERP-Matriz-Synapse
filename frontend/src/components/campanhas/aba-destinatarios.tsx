"use client";

import { useState } from "react";
import { UsersRound } from "lucide-react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Label } from "@/components/ui/label";
import { Seletor } from "@/components/ui/seletor";
import { Skeleton } from "@/components/ui/skeleton";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import { useDestinatarios } from "@/lib/campanhas/hooks";
import type { MotivoDoDestinatario, StatusDoDestinatario } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

import { BotaoExportarCsv } from "./botao-exportar-csv";
import { EstadoVazio } from "./estados";
import { PaginacaoSimples } from "./paginacao-simples";
import { TabelaDeDestinatarios } from "./tabela-de-destinatarios";

const TODOS = "";

export function AbaDestinatarios({ campanhaId }: { campanhaId: string }) {
  const textos = useTextos().campanhas;
  const t = textos.detalhe.destinatarios;
  const [status, setStatus] = useState<string>(TODOS);
  const [motivo, setMotivo] = useState<string>(TODOS);
  const [pagina, setPagina] = useState(0);
  const filtro = {
    status: (status || null) as StatusDoDestinatario | null,
    motivo: (motivo || null) as MotivoDoDestinatario | null,
  };
  const consulta = useDestinatarios(campanhaId, filtro, pagina);

  const opcoesDeStatus = [
    { valor: TODOS, rotulo: t.todos },
    ...(Object.keys(textos.statusDoDestinatario) as StatusDoDestinatario[]).map((chave) => ({
      valor: chave,
      rotulo: textos.statusDoDestinatario[chave],
    })),
  ];
  const opcoesDeMotivo = [
    { valor: TODOS, rotulo: t.todosMotivos },
    ...(Object.keys(textos.motivos) as MotivoDoDestinatario[]).map((chave) => ({ valor: chave, rotulo: textos.motivos[chave] })),
  ];
  const filtrar = (definir: (valor: string) => void) => (valor: string) => {
    definir(valor);
    setPagina(0);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="filtro-status">{t.filtroStatus}</Label>
            <Seletor id="filtro-status" valor={status} opcoes={opcoesDeStatus} placeholder={t.todos} className="w-48" onChange={filtrar(setStatus)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filtro-motivo">{t.filtroMotivo}</Label>
            <Seletor id="filtro-motivo" valor={motivo} opcoes={opcoesDeMotivo} placeholder={t.todosMotivos} className="w-64" onChange={filtrar(setMotivo)} />
          </div>
        </div>
        <BotaoExportarCsv campanhaId={campanhaId} />
      </div>
      {consulta.isPending && (
        <div role="status" aria-label={t.carregando} className="space-y-2">
          {Array.from({ length: 5 }, (_, indice) => (
            <Skeleton key={indice} className="h-12 w-full" />
          ))}
        </div>
      )}
      {consulta.isError && !consulta.data && <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />}
      {consulta.data && consulta.data.itens.length === 0 && (
        <EstadoVazio icone={<UsersRound className="size-6" aria-hidden />} titulo={t.vazio} descricao={interpolarCatalogo(t.total, { total: "0" })} />
      )}
      {consulta.data && consulta.data.itens.length > 0 && (
        <>
          <p className="text-xs text-muted-foreground">{interpolarCatalogo(t.total, { total: formatarNumero(consulta.data.total) })}</p>
          <TabelaDeDestinatarios linhas={consulta.data.itens} legenda={textos.detalhe.abas.destinatarios} />
          <PaginacaoSimples pagina={pagina} total={consulta.data.total} rotulo={textos.detalhe.abas.destinatarios} aoMudar={setPagina} />
        </>
      )}
    </div>
  );
}
