/**
 * Nome do cookie httpOnly que guarda o refresh token.
 *
 * <p>O backend (E07) devolve accessToken/refreshToken no corpo da resposta, não como cookie — quem
 * transforma o refreshToken num cookie httpOnly são os Route Handlers em app/api/auth/*, que fazem
 * o papel de BFF (backend-for-frontend) só para essa troca. O nome precisa ser idêntico nos três
 * handlers (login/refresh/logout); por isso vive num único lugar.
 */
export const NOME_COOKIE_REFRESH = "synapse_refresh";

/**
 * Registra, no servidor, se o refresh token pode sobreviver ao fechamento do navegador. É um
 * cookie httpOnly separado porque o browser não devolve ao Route Handler os atributos (`maxAge` /
 * `expires`) com que o cookie de refresh original foi criado. Sem esta informação, uma rotação
 * poderia transformar silenciosamente um cookie de sessão em um cookie persistente.
 */
export const NOME_COOKIE_PERSISTENCIA_SESSAO = "synapse_sessao_persistente";

/** 7 dias — mesmo default de synapse.seguranca.validade-refresh-token no backend. */
export const MAX_AGE_COOKIE_REFRESH_SEGUNDOS = 60 * 60 * 24 * 7;
