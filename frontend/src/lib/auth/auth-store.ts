import { create } from "zustand";

interface SessaoIniciada {
  accessToken: string;
  expiraEmSegundos: number;
  /** Só disponível logo após login (o usuário acabou de digitar) — não sobrevive a um refresh. */
  email?: string;
}

interface AuthState {
  accessToken: string | null;
  /** epoch ms — quando o access token expira. */
  expiraEm: number | null;
  email: string | null;
  papel: string | null;
  status: "carregando" | "autenticado" | "nao-autenticado";
  definirSessao: (sessao: SessaoIniciada) => void;
  limparSessao: () => void;
}

/**
 * Decodifica o payload do JWT sem validar assinatura — só para ler `papel`, exibido no rodapé da
 * sidebar. A validação de verdade é sempre do backend; isto é leitura de exibição, não decisão de
 * autorização.
 */
function decodificarPapel(accessToken: string): string | null {
  try {
    const payloadBase64 = accessToken.split(".")[1];
    const payload = JSON.parse(atob(payloadBase64.replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.papel === "string" ? payload.papel : null;
  } catch {
    return null;
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  expiraEm: null,
  email: null,
  papel: null,
  status: "carregando",
  definirSessao: ({ accessToken, expiraEmSegundos, email }) =>
    set((estadoAtual) => ({
      accessToken,
      expiraEm: Date.now() + expiraEmSegundos * 1000,
      email: email ?? estadoAtual.email,
      papel: decodificarPapel(accessToken),
      status: "autenticado",
    })),
  limparSessao: () =>
    set({ accessToken: null, expiraEm: null, email: null, papel: null, status: "nao-autenticado" }),
}));
