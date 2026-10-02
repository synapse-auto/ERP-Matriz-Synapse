"use client";

import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDataHora, formatarDia, formatarNumero } from "@/lib/campanhas/formatacao";
import type { EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { type FiltroEditavel } from "@/lib/campanhas/estado-do-assistente";
import type { PreviaDoPublico, ProjecaoDeEnvio } from "@/lib/campanhas/types";
import { instanteDoAgendamento } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

interface Props {
  estado: EstadoDoAssistente;
  previa: PreviaDoPublico | undefined;
  projecao: ProjecaoDeEnvio | undefined;
}

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1 py-2 sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-xs font-medium text-muted-foreground sm:pt-0.5">{rotulo}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

/** Uma frase por filtro aplicado; vazio = Agenda inteira. */
function frasesDoFiltro(filtro: FiltroEditavel, textos: ReturnType<typeof useTextos>["campanhas"]["passoPublico"]): string[] {
  const frases: string[] = [];
  if (filtro.tagIds.length > 0) frases.push(interpolarCatalogo(textos.resumoTags, { quantidade: String(filtro.tagIds.length) }));
  if (filtro.etapaId) frases.push(textos.etapa);
  if (filtro.cadastroDesde) frases.push(`${textos.cadastroDesde} ${formatarDia(filtro.cadastroDesde)}`);
  if (filtro.cadastroAte) frases.push(`${textos.cadastroAte} ${formatarDia(filtro.cadastroAte)}`);
  if (filtro.nuncaConversou) frases.push(textos.nuncaConversou);
  if (filtro.busca.trim()) frases.push(`${textos.busca}: ${filtro.busca.trim()}`);
  return frases;
}

/** Resumo completo, linha a linha, do que vai acontecer ao iniciar. */
export function ResumoDaCampanha({ estado, previa, projecao }: Props) {
  const textos = useTextos().campanhas;
  const t = textos.passoRevisao;
  const dias = estado.dias.map((dia) => textos.passoRitmo.diasCurtos[String(dia) as keyof typeof textos.passoRitmo.diasCurtos]);
  const agendada = estado.modoDeInicio === "AGENDADA" ? instanteDoAgendamento(estado.agendadaPara) : null;
  return (
    <dl className="divide-y divide-border rounded-xl border border-border bg-card px-4 shadow-sm">
      <Linha rotulo={t.nome}>{estado.nome}</Linha>
      <Linha rotulo={t.template}>
        {estado.template?.nome} ({estado.template?.idioma})
      </Linha>
      <Linha rotulo={t.publico}>
        {previa
          ? interpolarCatalogo(t.publicoValor, { receberao: formatarNumero(previa.elegiveis), total: formatarNumero(previa.total) })
          : "-"}
      </Linha>
      <Linha rotulo={t.filtros}>{frasesDoFiltro(estado.filtro, textos.passoPublico).join(" · ") || t.semFiltros}</Linha>
      <Linha rotulo={t.ritmo}>
        {interpolarCatalogo(t.ritmoValor, {
          limite: formatarNumero(estado.limiteDiario ?? 0),
          ritmo: String(estado.ritmoPorMinuto ?? 0),
        })}
      </Linha>
      <Linha rotulo={t.janela}>
        {interpolarCatalogo(t.janelaValor, { inicio: estado.janelaInicio, fim: estado.janelaFim, dias: dias.join(", ") })}
      </Linha>
      {estado.rampaAtiva && (
        <Linha rotulo={t.rampa}>
          {interpolarCatalogo(t.rampaValor, {
            incremento: String(estado.rampaIncremento ?? 0),
            teto: String(estado.rampaTeto ?? 0),
          })}
        </Linha>
      )}
      <Linha rotulo={t.inicio}>{agendada ? formatarDataHora(agendada) : t.agora}</Linha>
      <Linha rotulo={t.termino}>{projecao?.terminoEstimado ? formatarDia(projecao.terminoEstimado) : "-"}</Linha>
    </dl>
  );
}
