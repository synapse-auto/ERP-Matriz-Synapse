import { urlSegura } from "@/lib/utils";

/**
 * A mesma regra do AvatarIniciais: caminho relativo da API vai autenticado;
 * URL absoluta http(s) segue direta; qualquer outro esquema é recusado.
 */
export type OrigemDeRecursoVisual =
  | { tipo: "autenticada"; caminho: string }
  | { tipo: "absoluta"; url: string }
  | { tipo: "invalida" };

export function classificarOrigemDeRecursoVisual(referencia: string): OrigemDeRecursoVisual {
  if (referencia.startsWith("/") && !referencia.startsWith("//")) {
    return { tipo: "autenticada", caminho: referencia };
  }
  const url = urlSegura(referencia);
  return url ? { tipo: "absoluta", url } : { tipo: "invalida" };
}
