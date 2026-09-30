import { useAuthStore } from "@/lib/auth/auth-store";

/** Prefixo comum: invalidar por ele alcança a lista de qualquer sessão. */
export const PREFIXO_TEMPLATES_WHATSAPP = ["whatsapp-templates"] as const;

/**
 * A lista de templates depende de quem pergunta (templates restritos só voltam para
 * ADMINISTRADOR). Por isso a chave carrega o usuário: a resposta de uma sessão nunca serve
 * de cache para outra.
 */
export function chaveTemplatesWhatsApp(usuarioId: string | null | undefined) {
  return [...PREFIXO_TEMPLATES_WHATSAPP, usuarioId ?? null] as const;
}

export function useChaveTemplatesWhatsApp() {
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  return chaveTemplatesWhatsApp(usuarioId);
}
