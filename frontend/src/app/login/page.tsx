import { Bot, BriefcaseBusiness, ChartNoAxesCombined } from "lucide-react";

import { MarcaSynapse } from "@/components/auth/marca-synapse";
import { LoginForm } from "@/components/auth/login-form";
import { buscarTextos } from "@/lib/config/fetch-config";

export default async function LoginPage() {
  const textos = (await buscarTextos()).login;
  const destaques = [
    { icone: BriefcaseBusiness, ...textos.destaques.crm },
    { icone: Bot, ...textos.destaques.atendimento },
    { icone: ChartNoAxesCombined, ...textos.destaques.iaAssistente },
  ];

  return (
    <main className="synapse-login">
      <section className="synapse-login__apresentacao" aria-labelledby="apresentacao-login-titulo">
        <div className="synapse-login__marca-lockup">
          <MarcaSynapse alt={textos.marcaSynapse} className="synapse-login__marca" />
          <span className="synapse-login__marca-nome">{textos.marcaSynapse}</span>
          <span className="synapse-login__marca-pill">CRM</span>
        </div>
        <div className="synapse-login__conteudo">
          <h1 id="apresentacao-login-titulo" className="synapse-login__titulo">
            {textos.apresentacaoTitulo}
          </h1>
          <p className="synapse-login__subtitulo">{textos.apresentacaoSubtitulo}</p>
        </div>
        <ul className="synapse-login__destaques">
          {destaques.map(({ icone: Icone, titulo, descricao }) => (
            <li key={titulo} className="synapse-login__destaque">
              <span className="synapse-login__icone-destaque">
                <Icone aria-hidden="true" size={18} />
              </span>
              <span>
                <strong>{titulo}</strong>
                <small>{descricao}</small>
              </span>
            </li>
          ))}
        </ul>
      </section>
      <section className="synapse-login__formulario" aria-label={textos.titulo}>
        <LoginForm />
      </section>
    </main>
  );
}
