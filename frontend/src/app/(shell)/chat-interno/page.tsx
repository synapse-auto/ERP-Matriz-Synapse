import { PaginaChatInterno } from "@/components/chat-interno/pagina-chat-interno";

export default async function ChatInterno({
  searchParams,
}: {
  searchParams: Promise<{ conversaId?: string | string[] }>;
}) {
  const parametros = await searchParams;
  const valor = parametros.conversaId;
  const conversaInicialId = Array.isArray(valor) ? (valor[0] ?? null) : (valor ?? null);
  return <PaginaChatInterno conversaInicialId={conversaInicialId} />;
}
