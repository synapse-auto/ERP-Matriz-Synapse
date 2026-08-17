import{apiFetch}from"@/lib/api/http-client";import type{AvaliacoesEquipe,DesempenhoEquipe,MeuUsuario,PapelGerenciavel,StatusPresenca,UsuarioEquipe}from"./types";
export const listarEquipe=()=>apiFetch<UsuarioEquipe[]>("/api/v1/usuarios");export const avaliacoesEquipe=()=>apiFetch<AvaliacoesEquipe>("/api/v1/equipe/avaliacoes");export const desempenhoEquipe=()=>apiFetch<DesempenhoEquipe>("/api/v1/equipe/desempenho");export const obterMeuUsuario=()=>apiFetch<MeuUsuario>("/api/v1/me");
export function criarUsuario(d:{nome:string;email:string;senha:string;papel:PapelGerenciavel}){return apiFetch<UsuarioEquipe>("/api/v1/usuarios",{method:"POST",body:JSON.stringify(d)})}
export function editarUsuario(id:string,d:{nome:string;email:string;papel:PapelGerenciavel}){return apiFetch<UsuarioEquipe>(`/api/v1/usuarios/${id}`,{method:"PUT",body:JSON.stringify(d)})}
export const desativarUsuario=(id:string)=>apiFetch<void>(`/api/v1/usuarios/${id}/desativar`,{method:"PATCH"});
/** E29: senha em claro devolvida uma unica vez — nao entra em cache nem sobrevive alem do dialogo que a mostra. */
export const gerarSenhaProvisoria=(id:string)=>apiFetch<{senha:string}>(`/api/v1/usuarios/${id}/senha-provisoria`,{method:"POST"});
export const obterPresenca=()=>apiFetch<{status:StatusPresenca}>("/api/v1/usuarios/me/presenca");export const atualizarPresenca=(status:StatusPresenca)=>apiFetch<{status:StatusPresenca}>("/api/v1/usuarios/me/presenca",{method:"PATCH",body:JSON.stringify({status})});
