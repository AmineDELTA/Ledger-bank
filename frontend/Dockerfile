# ==========================================
# Stage 1: Build Vite / React production bundle
# ==========================================
FROM node:20-alpine AS builder
WORKDIR /app

# Cache node modules
COPY package.json package-lock.json ./
RUN npm ci

# Build React application
COPY . .
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ==========================================
# Stage 2: Serve static files with Nginx
# ==========================================
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]