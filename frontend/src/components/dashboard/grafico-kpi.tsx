"use client";

import { useId } from "react";
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import type { VisaoGeralDashboard } from "@/lib/dashboard/types";

type Ponto = NonNullable<VisaoGeralDashboard["seriesMensais"]>[number];
export type CampoSerie = "atendimentos" | "novosLeads" | "tempoMedioSegundos" |
  "vendasFechadas" | "taxaConversao" | "avaliacaoMedia" | "resolucaoPorIa";

export function GraficoKpi({
  pontos,
  campo,
  compacto,
  titulo,
  meses,
  semDado,
  formatar,
}: {
  pontos: Ponto[];
  campo: CampoSerie;
  compacto: boolean;
  titulo: string;
  meses: string[];
  semDado: string;
  formatar: (valor: number) => string;
}) {
  const gradiente = `serie-${useId().replaceAll(":", "")}`;
  const dados = pontos.map((ponto) => ({
    mes: ponto.mes,
    rotulo: meses[Number(ponto.mes.slice(-2)) - 1] ?? ponto.mes,
    valor: ponto.disponivel ? ponto[campo] : null,
  }));
  if (!dados.some((ponto) => ponto.valor !== null)) {
    return compacto ? null : (
      <p className="mt-4 flex h-28 items-center justify-center text-xs text-muted-foreground">
        {semDado}
      </p>
    );
  }

  return (
    <div
      className={compacto ? "h-9 w-20 shrink-0" : "h-30 w-full"}
      role="img"
      aria-label={titulo}
      data-testid={`serie-${campo}`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={compacto
          ? { top: 2, right: 0, bottom: 0, left: 0 }
          : { top: 20, right: 0, bottom: 0, left: -20 }}>
          <defs>
            <linearGradient id={gradiente} x1="0" y1="1" x2="0" y2="0">
              <stop offset="0%" stopColor="var(--tom)" stopOpacity={0.55} />
              <stop offset="100%" stopColor="var(--tom)" />
            </linearGradient>
          </defs>
          {!compacto && <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />}
          {!compacto && <XAxis dataKey="rotulo" tickLine={false} axisLine={false} tick={{ fontSize: 10 }} />}
          {!compacto && <YAxis tickLine={false} axisLine={false} tick={{ fontSize: 10 }} tickFormatter={formatar} />}
          {!compacto && <Tooltip formatter={(valor) => formatar(Number(valor))} />}
          <Bar dataKey="valor" fill={`url(#${gradiente})`} radius={[3, 3, 0, 0]} maxBarSize={32} isAnimationActive={false}>
            {!compacto && (
              <LabelList
                dataKey="valor"
                position="top"
                fontSize={9}
                fill="var(--muted-foreground)"
                formatter={(valor) => Number(valor) === 0 ? "" : formatar(Number(valor))}
              />
            )}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
