/**
 * Avatar com as iniciais de uma pessoa e um tom de cor determinístico (E17b §Bloco 4 — extraído na
 * segunda tela que precisou disso, Lembretes, depois de já existir em Equipe).
 *
 * <p>O tom vem de um hash simples do id sobre a paleta de tokens (design/TOKENS.md) — nunca cor
 * literal, e nunca o backend precisando guardar uma cor por pessoa: a mesma pessoa sempre cai no
 * mesmo tom entre renderizações, sem estado extra.
 */
const TONS_DE_AVATAR = [
  "var(--primary)",
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
];

export function tomDoAvatar(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return TONS_DE_AVATAR[hash % TONS_DE_AVATAR.length];
}

export function iniciaisDoNome(nome: string): string {
  return nome
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase() ?? "")
    .join("");
}

type Props = {
  id: string;
  nome: string;
  className?: string;
};

export function AvatarIniciais({ id, nome, className }: Props) {
  return (
    <span
      className={className ?? "flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"}
      style={{ backgroundColor: tomDoAvatar(id) }}
      title={nome}
    >
      {iniciaisDoNome(nome)}
    </span>
  );
}
