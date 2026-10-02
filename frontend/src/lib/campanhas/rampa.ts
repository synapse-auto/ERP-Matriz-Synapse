import type { EstadoDoAssistente } from "./estado-do-assistente";
import type { RampaDeLimite } from "./types";

/** Rampa só existe se estiver ligada e com os dois números preenchidos. */
export function rampaDoEstado(estado: EstadoDoAssistente): RampaDeLimite | null {
  return estado.rampaAtiva && estado.rampaIncremento && estado.rampaTeto
    ? { incrementoPorDia: estado.rampaIncremento, teto: estado.rampaTeto }
    : null;
}
