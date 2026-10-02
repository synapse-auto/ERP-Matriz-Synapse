"use client";

import { Rocket } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { filtroSemVazios, type EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import { usePreviaDoPublico } from "@/lib/campanhas/hooks";
import { useProjecaoDeEnvio, useTemplatesParaCampanha } from "@/lib/campanhas/hooks";
import { rampaDoEstado } from "@/lib/campanhas/rampa";
import { pendenciasDeInicio, type PendenciaDeInicio } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { BalaoDeTemplate } from "./balao-de-template";
import { chaveDoTemplate } from "./lista-de-templates";
import { EnvioDeTeste } from "./envio-de-teste";
import { ResumoDaCampanha } from "./resumo-da-campanha";

interface Props {
  estado: EstadoDoAssistente;
  ehAdministrador: boolean;
  iniciando: boolean;
  erroAoIniciar: string | null;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
  aoIniciar: () => void;
}

const TEXTO_DA_PENDENCIA: Record<PendenciaDeInicio, "pendenciaConsentimento" | "pendenciaTotal" | "pendenciaPublico" | "pendenciaPermissao"> = {
  consentimento: "pendenciaConsentimento",
  total: "pendenciaTotal",
  publico: "pendenciaPublico",
  permissao: "pendenciaPermissao",
};

export function PassoRevisao({ estado, ehAdministrador, iniciando, erroAoIniciar, aoMudar, aoIniciar }: Props) {
  const textos = useTextos().campanhas.passoRevisao;
  const previa = usePreviaDoPublico(filtroSemVazios(estado.filtro));
  const templates = useTemplatesParaCampanha();
  const projecao = useProjecaoDeEnvio({
    filtro: filtroSemVazios(estado.filtro),
    limiteDiario: estado.limiteDiario ?? 1,
    janela: { inicio: estado.janelaInicio, fim: estado.janelaFim, dias: estado.dias },
    ritmoPorMinuto: estado.ritmoPorMinuto ?? 1,
    rampa: rampaDoEstado(estado),
    primeiroDia: null,
  });
  const destinatarios = previa.data?.elegiveis ?? 0;
  const pendencias = pendenciasDeInicio({
    ehAdministrador,
    consentimento: estado.consentimento,
    confirmacaoDigitada: estado.confirmacaoDigitada,
    destinatarios,
  });
  const corpo = templates.data?.find((t) => estado.template && chaveDoTemplate(t) === chaveDoTemplate(estado.template))?.corpo;
  const total = formatarNumero(destinatarios);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
      <div className="space-y-5">
        <h2 className="text-sm font-bold">{textos.resumo}</h2>
        <ResumoDaCampanha estado={estado} previa={previa.data} projecao={projecao.data} />
        <EnvioDeTeste rascunhoId={estado.rascunhoId} />
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-primary"
            checked={estado.consentimento}
            onChange={(evento) => aoMudar({ consentimento: evento.target.checked })}
          />
          {textos.consentimento}
        </label>
        <div className="space-y-1.5">
          <Label htmlFor="confirmacao-total">{interpolarCatalogo(textos.digiteTotal, { receberao: total })}</Label>
          <Input
            id="confirmacao-total"
            inputMode="numeric"
            className="max-w-48 tabular-nums"
            aria-label={textos.digiteTotalRotulo}
            placeholder={interpolarCatalogo(textos.digiteTotalPlaceholder, { receberao: total })}
            value={estado.confirmacaoDigitada}
            onChange={(evento) => aoMudar({ confirmacaoDigitada: evento.target.value })}
          />
        </div>
        {pendencias.length > 0 && (
          <div className="rounded-lg border border-border bg-muted/40 p-3 text-sm">
            <p className="font-medium">{textos.pendencias}</p>
            <ul className="mt-1 list-inside list-disc text-muted-foreground">
              {pendencias.map((pendencia) => (
                <li key={pendencia}>{textos[TEXTO_DA_PENDENCIA[pendencia]]}</li>
              ))}
            </ul>
          </div>
        )}
        {!ehAdministrador && <p className="text-sm text-muted-foreground">{textos.somenteAdministrador}</p>}
        {erroAoIniciar && (
          <p role="alert" className="text-sm text-destructive">
            {erroAoIniciar}
          </p>
        )}
        <Button type="button" size="lg" disabled={pendencias.length > 0 || iniciando} onClick={aoIniciar}>
          <Rocket aria-hidden />
          {iniciando ? textos.iniciando : estado.modoDeInicio === "AGENDADA" ? textos.agendarCampanha : textos.iniciar}
        </Button>
      </div>
      <aside className="lg:sticky lg:top-6 lg:self-start">
        {corpo && <BalaoDeTemplate corpo={corpo} variaveis={estado.variaveis} />}
      </aside>
    </div>
  );
}
