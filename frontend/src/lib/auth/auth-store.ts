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
  usuarioId: string | null;
  /** true enquanto a senha for provisória (E29) — decidido pelo backend, só lido aqui. */
  precisaTrocarSenha: boolean;
  status: "carregando" | "autenticado" | "nao-autenticado";
  definirSessao: (sessao: SessaoIniciada) => void;
  limparSessao: () => void;
}

/**
 * Decodifica o payload do JWT sem validar assinatura — só para leitura de exibição/roteamento
 * (`papel` no rodapé da sidebar, `senha_provisoria` para o redirecionamento de primeiro acesso). A
 * validação de verdade é sempre do backend: qualquer rota que dependa de `senha_provisoria` continua
 * bloqueada no servidor (SenhaProvisoriaFilter) mesmo que este decode minta.
 */
function decodificarPayload(accessToken: string): Record<string, unknown> {
  try {
    const payloadBase64 = accessToken.split(".")[1];
    return JSON.parse(atob(payloadBase64.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return {};
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  expiraEm: null,
  email: null,
  papel: null,
  usuarioId: null,
  precisaTrocarSenha: false,
  status: "carregando",
  definirSessao: ({ accessToken, expiraEmSegundos, email }) =>
    set((estadoAtual) => {
      const payload = decodificarPayload(accessToken);
      return {
        accessToken,
        expiraEm: Date.now() + expiraEmSegundos * 1000,
        email: email ?? estadoAtual.email,
        papel: typeof payload.papel === "string" ? payload.papel : null,
        usuarioId: typeof payload.sub === "string" ? payload.sub : null,
        precisaTrocarSenha: payload.senha_provisoria === true,
        status: "autenticado",
      };
    }),
  limparSessao: () =>
    set({
      accessToken: null,
      expiraEm: null,
      email: null,
      papel: null,
      usuarioId: null,
      precisaTrocarSenha: false,
      status: "nao-autenticado",
    }),
}));
