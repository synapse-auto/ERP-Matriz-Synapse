/**
 * Leitura do contato compartilhado pelo cliente (tipo CONTATO).
 *
 * O backend grava em `midiaMetadados`:
 * `{"contatos":[{"nome":"...","telefones":[{"numero":"...","waId":"...","tipo":"..."}]}]}`.
 * `nome`, `waId` e `tipo` são opcionais; um contato pode vir sem telefone.
 */

export interface TelefoneDoContato {
  numero: string;
  waId?: string;
  tipo?: string;
}

export interface ContatoCompartilhado {
  nome?: string;
  telefones: TelefoneDoContato[];
}

/** E.164 permite até 15 dígitos; abaixo de 8 não há número discável nem sem DDD. */
const MINIMO_DE_DIGITOS = 8;
const MAXIMO_DE_DIGITOS = 15;
const CARACTERES_DE_TELEFONE = /^\+?[\d\s().-]+$/;

export function contatosDaMensagem(metadados: string | null | undefined): ContatoCompartilhado[] {
  if (!metadados) return [];
  try {
    const valor: unknown = JSON.parse(metadados);
    const contatos = (valor as { contatos?: unknown } | null)?.contatos;
    if (!Array.isArray(contatos)) return [];
    return contatos.flatMap((contato) => {
      const lido = lerContato(contato);
      return lido ? [lido] : [];
    });
  } catch {
    return [];
  }
}

/**
 * Número pronto para `tel:`, ou `null` quando o texto não é um telefone utilizável.
 * Mantém o `+` inicial quando o provedor o enviou; o resto vira só dígitos.
 */
export function numeroDiscavel(numero: string | null | undefined): string | null {
  const texto = numero?.trim() ?? "";
  if (!CARACTERES_DE_TELEFONE.test(texto)) return null;
  const digitos = texto.replace(/\D/g, "");
  if (digitos.length < MINIMO_DE_DIGITOS || digitos.length > MAXIMO_DE_DIGITOS) return null;
  return texto.startsWith("+") ? `+${digitos}` : digitos;
}

/** Texto para a ação "copiar" da mensagem inteira: um contato por linha. */
export function textoCopiavelDosContatos(contatos: ContatoCompartilhado[]): string | null {
  const linhas = contatos
    .map((contato) => [contato.nome, ...contato.telefones.map((telefone) => telefone.numero)]
      .filter((parte): parte is string => Boolean(parte && parte.trim()))
      .join(" "))
    .filter((linha) => linha.length > 0);
  return linhas.length > 0 ? linhas.join("\n") : null;
}

function lerContato(valor: unknown): ContatoCompartilhado | null {
  if (typeof valor !== "object" || valor === null) return null;
  const { nome, telefones } = valor as { nome?: unknown; telefones?: unknown };
  const lidos = Array.isArray(telefones) ? telefones.flatMap(lerTelefone) : [];
  const nomeLido = typeof nome === "string" && nome.trim() ? nome.trim() : undefined;
  if (!nomeLido && lidos.length === 0) return null;
  return { nome: nomeLido, telefones: lidos };
}

function lerTelefone(valor: unknown): TelefoneDoContato[] {
  if (typeof valor !== "object" || valor === null) return [];
  const { numero, waId, tipo } = valor as { numero?: unknown; waId?: unknown; tipo?: unknown };
  if (typeof numero !== "string" || !numero.trim()) return [];
  return [{
    numero: numero.trim(),
    waId: typeof waId === "string" && waId.trim() ? waId.trim() : undefined,
    tipo: typeof tipo === "string" && tipo.trim() ? tipo.trim() : undefined,
  }];
}
