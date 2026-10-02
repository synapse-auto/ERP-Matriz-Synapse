"use client";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { filtroSemVazios, type EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { useProjecaoDeEnvio, useValorComAtraso } from "@/lib/campanhas/hooks";
import type { PedidoDeProjecao } from "@/lib/campanhas/types";
import { janelaValida, validarLimiteDiario, validarRampa, validarRitmo } from "@/lib/campanhas/validacao";
import { rampaDoEstado } from "@/lib/campanhas/rampa";
import { useTextos } from "@/lib/config/textos-provider";

import { CampoDeLimite, lerInteiro } from "./campo-de-limite";
import { InicioDaCampanha, RampaDeLimite } from "./inicio-e-rampa";
import { JanelaDeEnvio } from "./janela-de-envio";
import { EsqueletoDaProjecao, MiniCalendarioDeEnvios } from "./mini-calendario-de-envios";

interface Props {
  estado: EstadoDoAssistente;
  teto: number;
  limiteMeta: number;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
  mostrarErros: boolean;
}

/** Só projeta quando o plano é válido; um plano inválido já mostra o erro no campo certo. */
function pedidoDeProjecao(estado: EstadoDoAssistente, teto: number): PedidoDeProjecao | null {
  const rampa = rampaDoEstado(estado);
  const valido =
    validarLimiteDiario(estado.limiteDiario, teto) === "ok" &&
    validarRitmo(estado.ritmoPorMinuto) &&
    janelaValida(estado.janelaInicio, estado.janelaFim) &&
    estado.dias.length > 0 &&
    validarRampa(rampa, estado.limiteDiario ?? 0, teto) &&
    !(estado.rampaAtiva && rampa === null);
  if (!valido) return null;
  return {
    filtro: filtroSemVazios(estado.filtro),
    limiteDiario: estado.limiteDiario as number,
    janela: { inicio: estado.janelaInicio, fim: estado.janelaFim, dias: estado.dias },
    ritmoPorMinuto: estado.ritmoPorMinuto as number,
    rampa,
    primeiroDia: null,
  };
}

export function PassoRitmo({ estado, teto, limiteMeta, aoMudar, mostrarErros }: Props) {
  const textos = useTextos().campanhas.passoRitmo;
  const pedido = useValorComAtraso(pedidoDeProjecao(estado, teto));
  const projecao = useProjecaoDeEnvio(pedido);
  const ritmoInvalido = !validarRitmo(estado.ritmoPorMinuto);
  const rampa = rampaDoEstado(estado);
  const rampaValida = !estado.rampaAtiva || (rampa !== null && validarRampa(rampa, estado.limiteDiario ?? 0, teto));

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]">
      <div className="space-y-6">
        <CampoDeLimite
          id="limite-diario"
          rotulo={textos.limiteDiario}
          valor={estado.limiteDiario}
          teto={teto}
          limiteMeta={limiteMeta}
          mostrarErros={mostrarErros}
          aoMudar={(limiteDiario) => aoMudar({ limiteDiario })}
        />
        <div className="space-y-1.5">
          <Label htmlFor="ritmo-por-minuto">{textos.ritmo}</Label>
          <Input
            id="ritmo-por-minuto"
            type="number"
            min={1}
            max={600}
            value={estado.ritmoPorMinuto ?? ""}
            aria-invalid={ritmoInvalido}
            className="max-w-40 tabular-nums"
            onChange={(evento) => aoMudar({ ritmoPorMinuto: lerInteiro(evento.target.value) })}
          />
          <p className={ritmoInvalido ? "text-xs text-destructive" : "text-xs text-muted-foreground"}>
            {ritmoInvalido ? textos.ritmoInvalido : textos.ritmoAjuda}
          </p>
        </div>
        <JanelaDeEnvio inicio={estado.janelaInicio} fim={estado.janelaFim} dias={estado.dias} aoMudar={aoMudar} />
        <InicioDaCampanha estado={estado} aoMudar={aoMudar} />
        <RampaDeLimite estado={estado} teto={teto} rampaValida={rampaValida} aoMudar={aoMudar} />
      </div>
      <aside className="space-y-3 lg:sticky lg:top-6 lg:self-start">
        <h2 className="text-sm font-bold">{textos.estimativa}</h2>
        {pedido === null && <p className="text-sm text-muted-foreground">{textos.semProjecao}</p>}
        {pedido !== null && projecao.isPending && <EsqueletoDaProjecao />}
        {pedido !== null && projecao.isError && !projecao.data && (
          <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => projecao.refetch()} />
        )}
        {pedido !== null && projecao.data && <MiniCalendarioDeEnvios projecao={projecao.data} />}
      </aside>
    </div>
  );
}
