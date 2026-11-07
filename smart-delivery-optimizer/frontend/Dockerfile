# ---- Build stage -------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Runtime stage: static files + reverse proxy for /api ---------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html

# Upstream API address, substituted into nginx.conf at container start
ENV BACKEND_URL=http://backend:8080

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
