"use client";

import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { formatarDiaCurto } from "@/lib/campanhas/formatacao";
import type { DiaEnfileirado } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

interface Props {
  dias: DiaEnfileirado[];
  limiteDoDia: number;
}

/** Enfileiradas por dia contra o limite que vale hoje. Cores só por token; legenda em texto, não só cor. */
export function GraficoPorDia({ dias, limiteDoDia }: Props) {
  const textos = useTextos().campanhas.detalhe.grafico;
  if (dias.length === 0) {
    return <p className="flex h-40 items-center justify-center text-sm text-muted-foreground">{textos.vazio}</p>;
  }
  const dados = dias.map((dia) => ({ rotulo: formatarDiaCurto(dia.dia), enfileiradas: dia.enfileiradas }));
  return (
    <div>
      <div role="img" aria-label={textos.rotulo} className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={dados} margin={{ top: 16, right: 12, left: -12, bottom: 0 }}>
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis dataKey="rotulo" tickLine={false} axisLine={false} tick={{ fill: "var(--muted-foreground)", fontSize: 12 }} />
            <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: "var(--muted-foreground)", fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: "var(--muted)" }}
              contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", borderRadius: 8, color: "var(--foreground)" }}
              formatter={(valor) => [valor as number, textos.enfileiradas]}
            />
            <ReferenceLine y={limiteDoDia} stroke="var(--cor-erro)" strokeDasharray="6 4" />
            <Bar dataKey="enfileiradas" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={72} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <ul className="mt-2 flex flex-wrap gap-4 text-xs text-muted-foreground">
        <li className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-sm bg-primary" aria-hidden />
          {textos.enfileiradas}
        </li>
        <li className="flex items-center gap-1.5">
          <span className="h-0 w-4 border-t-2 border-dashed border-cor-erro" aria-hidden />
          {textos.limite}: {limiteDoDia}
        </li>
      </ul>
    </div>
  );
}
