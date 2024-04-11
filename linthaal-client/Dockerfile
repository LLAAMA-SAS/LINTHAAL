FROM node:18-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY styles ./styles
COPY components ./components
COPY pages ./pages

ENV NEXT_TELEMETRY_DISABLED 1

CMD npm run dev