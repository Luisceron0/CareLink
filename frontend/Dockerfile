# Build de la SPA (ADR-014) y servido estático por nginx.
#
# Multi-stage: el resultado es una imagen con archivos estáticos y nada más — sin Node,
# sin node_modules, sin el código fuente. Menos superficie y menos dependencias vivas en
# el contenedor que se ejecuta.
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
