import { rampaDoEstado } from "./rampa";
import type { EstadoDoAssistente } from "./estado-do-assistente";
import {
  instanteDoAgendamento,
  janelaValida,
  validarLimiteDiario,
  validarRampa,
  validarRitmo,
  variaveisCompletas,
} from "./validacao";

export const PASSOS = ["template", "publico", "ritmo", "revisao"] as const;
export type PassoDoAssistente = (typeof PASSOS)[number];

export interface ContextoDosPassos {
  tetoDaInstancia: number;
  /** Destinatários que vão receber, quando a prévia já respondeu; `null` enquanto não se sabe. */
  elegiveis: number | null;
  agora: Date;
}

export function agendamentoNoPassado(estado: EstadoDoAssistente, agora: Date): boolean {
  if (estado.modoDeInicio !== "AGENDADA") return false;
  const instante = instanteDoAgendamento(estado.agendadaPara);
  return instante === null || new Date(instante).getTime() <= agora.getTime();
}

/** Cada passo só deixa avançar quando o que ele pede está certo; o erro aparece no próprio campo. */
export function passoValido(passo: PassoDoAssistente, estado: EstadoDoAssistente, contexto: ContextoDosPassos): boolean {
  switch (passo) {
    case "template":
      return estado.nome.trim().length > 0 && estado.template !== null && variaveisCompletas(estado.variaveis);
    case "publico":
      return contexto.elegiveis === null || contexto.elegiveis > 0;
    case "ritmo": {
      const rampa = rampaDoEstado(estado);
      return (
        validarLimiteDiario(estado.limiteDiario, contexto.tetoDaInstancia) === "ok" &&
        validarRitmo(estado.ritmoPorMinuto) &&
        janelaValida(estado.janelaInicio, estado.janelaFim) &&
        estado.dias.length > 0 &&
        (!estado.rampaAtiva || rampa !== null) &&
        validarRampa(rampa, estado.limiteDiario ?? 0, contexto.tetoDaInstancia) &&
        !agendamentoNoPassado(estado, contexto.agora)
      );
    }
    case "revisao":
      return true;
  }
}
