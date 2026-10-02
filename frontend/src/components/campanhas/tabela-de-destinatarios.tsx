"use client";

import type { ReactNode } from "react";

import { PillDeStatus } from "@/components/ui/pill-de-status";
import { formatarDataHora } from "@/lib/campanhas/formatacao";
import type { Destinatario, StatusDoDestinatario } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

const TOM_DO_STATUS = {
  PENDENTE: "neutro",
  ENFILEIRADO: "neutro",
  ENVIADO: "info",
  ENTREGUE: "info",
  LIDO: "sucesso",
  FALHA: "erro",
  IGNORADO: "atencao",
} as const satisfies Record<StatusDoDestinatario, "neutro" | "info" | "sucesso" | "erro" | "atencao">;

interface Props {
  linhas: Destinatario[];
  /** Coluna extra no fim (ação da conferência). */
  acao?: (linha: Destinatario) => ReactNode;
  rotuloDaAcao?: string;
  legenda: string;
}

/** Tabela de destinatários, usada na lista completa e na conferência manual. */
export function TabelaDeDestinatarios({ linhas, acao, rotuloDaAcao, legenda }: Props) {
  const textos = useTextos().campanhas;
  const colunas = textos.detalhe.destinatarios.colunas;
  const semData = textos.detalhe.destinatarios.semData;
  const titulos = [colunas.contato, colunas.status, colunas.motivo, colunas.erro, colunas.enviado, colunas.entregue, colunas.lido, colunas.respondeu];
  return (
    <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
      <table className="w-full min-w-208 text-left text-sm">
        <caption className="sr-only">{legenda}</caption>
        <thead className="border-b border-border bg-muted/50 text-xs text-muted-foreground">
          <tr>
            {titulos.map((titulo) => (
              <th key={titulo} scope="col" className="px-3 py-2.5 font-medium">
                {titulo}
              </th>
            ))}
            {acao && (
              <th scope="col" className="px-3 py-2.5 text-right font-medium">
                {rotuloDaAcao}
              </th>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {linhas.map((linha) => (
            <tr key={linha.id} className="hover:bg-muted/40">
              <td className="px-3 py-2.5">
                <p className="font-medium">{linha.nome ?? semData}</p>
                <p className="text-xs tabular-nums text-muted-foreground">{linha.telefone ?? semData}</p>
              </td>
              <td className="px-3 py-2.5">
                <PillDeStatus tom={TOM_DO_STATUS[linha.status]}>{textos.statusDoDestinatario[linha.status]}</PillDeStatus>
              </td>
              <td className="max-w-48 px-3 py-2.5 text-muted-foreground">{linha.motivo ? textos.motivos[linha.motivo] : semData}</td>
              <td className="px-3 py-2.5 tabular-nums text-muted-foreground">{linha.codigoDeErro ?? semData}</td>
              {[linha.enviadoEm, linha.entregueEm, linha.lidoEm, linha.respondeuEm].map((instante, indice) => (
                <td key={indice} className="whitespace-nowrap px-3 py-2.5 text-xs text-muted-foreground">
                  {formatarDataHora(instante, semData)}
                </td>
              ))}
              {acao && <td className="px-3 py-2.5 text-right">{acao(linha)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
