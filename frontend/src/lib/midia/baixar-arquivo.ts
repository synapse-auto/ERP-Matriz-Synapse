/** Salva um Blob recebido do endpoint autenticado sem expor URL nem credencial do provedor. */
export function baixarBlobComoArquivo(blob: Blob, nome: string): void {
  const url = URL.createObjectURL(blob);
  const ancora = document.createElement("a");
  ancora.href = url;
  ancora.download = nomeSeguro(nome);
  ancora.rel = "noopener noreferrer";
  document.body.appendChild(ancora);
  try {
    ancora.click();
  } finally {
    ancora.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

function nomeSeguro(nome: string): string {
  const semDiretorios = nome.replaceAll("\\", "/").split("/").at(-1) ?? "";
  const seguro = semDiretorios.replace(/[\u0000-\u001f\u007f"<>:|?*]/g, "_").trim();
  return seguro || "arquivo";
}
