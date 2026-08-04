import { PaginaAtendimentosCliente } from "@/components/atendimentos/pagina-atendimentos-cliente";
import type { VisaoAtendimento } from "@/lib/atendimento/types";

const VISOES = new Set<VisaoAtendimento>(["ATIVOS", "PENDENTES", "POTENCIAIS", "TODOS"]);

function primeiro(valor: string | string[] | undefined): string | null {
  return Array.isArray(valor) ? (valor[0] ?? null) : (valor ?? null);
}

/** Recebe o deep link vindo da Agenda e entrega ids simples ao Client Component. */
export default async function PaginaAtendimentos({
  searchParams,
}: {
  searchParams: Promise<{ leadId?: string | string[]; visao?: string | string[] }>;
}) {
  const parametros = await searchParams;
  const leadInicialId = primeiro(parametros.leadId);
  const visaoRecebida = primeiro(parametros.visao);
  const visaoInicial = visaoRecebida && VISOES.has(visaoRecebida as VisaoAtendimento)
    ? (visaoRecebida as VisaoAtendimento)
    : null;

  return <PaginaAtendimentosCliente leadInicialId={leadInicialId} visaoInicial={visaoInicial} />;
}
