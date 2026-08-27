import { LayoutAdministracao } from "@/components/administracao/layout-administracao";
import { ProtecaoAdministrador } from "@/components/administracao/protecao-administrador";

export default function AdministracaoLayout({ children }: { children: React.ReactNode }) {
  return (
    <ProtecaoAdministrador>
      <LayoutAdministracao>{children}</LayoutAdministracao>
    </ProtecaoAdministrador>
  );
}
