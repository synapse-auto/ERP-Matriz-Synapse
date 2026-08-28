This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

The application uses [Inter](https://fonts.google.com/specimen/Inter) through
[`next/font/google`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts). The
files are downloaded at build time and served from the frontend image, so routes do not depend on
the Google Fonts CDN at runtime. Technical values use the local JetBrains Mono font through the
`font-mono` token.

### WebSocket no desenvolvimento local

O servidor Next local não encaminha upgrades WebSocket para o backend. Para testar o tempo real
fora do proxy da stack, inicie o frontend com `NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws` e
reinicie o processo após alterar a variável. Em homologação/produção, mantenha a variável vazia:
o navegador usa a origem pública (`/ws`) e o proxy da stack encaminha a conexão para o backend.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
