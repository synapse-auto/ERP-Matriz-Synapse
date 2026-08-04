/**
 * URL que apenas o servidor Next usa para falar com o backend.
 *
 * Em container ela aponta para o nome interno do servico, sem sair pelo Traefik. No desenvolvimento
 * cai para a mesma URL publica usada pelo browser. Manter esta variavel sem o prefixo NEXT_PUBLIC
 * permite trocar a instancia no runtime sem reconstruir a imagem.
 */
export function obterUrlApiServidor(): string {
  const url = process.env.SYNAPSE_BACKEND_URL ?? process.env.NEXT_PUBLIC_API_URL;
  if (!url) {
    throw new Error(
      "SYNAPSE_BACKEND_URL ou NEXT_PUBLIC_API_URL nao configurada — veja .env.example",
    );
  }
  return url.replace(/\/$/, "");
}
