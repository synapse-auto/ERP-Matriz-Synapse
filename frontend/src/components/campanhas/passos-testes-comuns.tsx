import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";

import { estadoInicial, type EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";

import { AssistenteDeCampanha } from "./assistente-de-campanha";

/** Monta o assistente já no passo pedido, com um rascunho válido; só para os testes dos passos. */
export function renderizarAssistenteNoPasso(passoInicial: number, sobrescritas: Partial<EstadoDoAssistente> = {}) {
  const inicial: EstadoDoAssistente = {
    ...estadoInicial(100),
    rascunhoId: "c1",
    nome: "Retorno de orçamentos",
    template: { nome: "retorno_orcamento", idioma: "pt_BR", parametros: 1 },
    variaveis: [{ posicao: 1, campo: "PRIMEIRO_NOME", reserva: "cliente" }],
    ...sobrescritas,
  };
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={cliente}>
      <AssistenteDeCampanha inicial={inicial} tetoDaInstancia={200} limiteMeta={150} passoInicial={passoInicial} />
    </QueryClientProvider>,
  );
}
