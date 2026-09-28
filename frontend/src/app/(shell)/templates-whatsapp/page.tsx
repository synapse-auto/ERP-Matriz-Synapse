import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaTemplatesWhatsApp } from "@/components/templates-whatsapp/pagina-templates-whatsapp";

export default function TemplatesWhatsApp() {
  return (
    <ExigeCapacidade capacidade="templates.ver">
      <PaginaTemplatesWhatsApp />
    </ExigeCapacidade>
  );
}
