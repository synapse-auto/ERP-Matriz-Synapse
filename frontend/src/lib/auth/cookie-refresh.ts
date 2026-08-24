import { MAX_AGE_COOKIE_REFRESH_SEGUNDOS } from "./constants";

/** A mesma política precisa ser aplicada a cada emissão/rotação do refresh token. */
export function opcoesCookieRefresh(manterSessaoAtiva: boolean) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    ...(manterSessaoAtiva ? { maxAge: MAX_AGE_COOKIE_REFRESH_SEGUNDOS } : {}),
  };
}
