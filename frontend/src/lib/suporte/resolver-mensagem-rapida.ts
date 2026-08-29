/** Variáveis públicas e seguras para o composer. Dados sensíveis nunca entram neste mapa. */
const VARIAVEIS_AUTORIZADAS = new Set(["nome", "empresa"]);

export function resolverMensagemRapida(template: string, dados: { nome: string; empresa?: string | null }) {
  const pendentes = new Set<string>();
  const texto = template.replace(/\{([\w-]+)\}/g, (inteiro, chave: string) => {
    if (!VARIAVEIS_AUTORIZADAS.has(chave)) {
      pendentes.add(chave);
      return inteiro;
    }
    const valor = chave === "nome" ? dados.nome : dados.empresa;
    if (!valor?.trim()) pendentes.add(chave);
    return valor?.trim() || inteiro;
  });
  return { texto, pendentes: [...pendentes] };
}

export function mensagemRapidaPodeEnviar(template: string) {
  return !/\{[\w-]+\}/.test(template);
}
