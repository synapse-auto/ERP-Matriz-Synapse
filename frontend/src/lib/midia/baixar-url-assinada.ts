import { urlSegura } from "@/lib/utils";

/**
 * Abre uma URL já assinada (MinIO/S3). Nunca recebe caminho `/api/` — o JWT não
 * viaja em `href`, e o navegador resolve sozinho só o que a assinatura autoriza.
 */
export function baixarUrlAssinada(url: string): void {
  const segura = urlSegura(url);
  if (!segura) {
    return;
  }
  const ancora = document.createElement("a");
  ancora.href = segura;
  ancora.rel = "noopener noreferrer";
  ancora.target = "_blank";
  ancora.referrerPolicy = "no-referrer";
  document.body.appendChild(ancora);
  ancora.click();
  ancora.remove();
}
