import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Duas iniciais em maiúsculo para fallback de avatar — "Ana Silva" → "AS". */
export function iniciaisDoNome(nome: string): string {
  return (
    nome
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0])
      .join("")
      .toUpperCase() || "?"
  )
}

/**
 * Só http(s) — para qualquer URL que venha de dado externo (webhook do provedor de canal) antes de
 * usar em `src`/`href`. Bloqueia `javascript:`/`data:`/outros esquemas perigosos.
 */
export function urlSegura(url: string | null | undefined): string | undefined {
  if (!url) {
    return undefined
  }
  try {
    const parsed = new URL(url)
    return ["http:", "https:"].includes(parsed.protocol) ? url : undefined
  } catch {
    return undefined
  }
}
