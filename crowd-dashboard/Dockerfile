FROM node:20-alpine AS build
WORKDIR /app

ARG REACT_APP_API_BASE=http://localhost:8080/api
ARG REACT_APP_WS_URL=http://localhost:8080/ws
ENV REACT_APP_API_BASE=$REACT_APP_API_BASE
ENV REACT_APP_WS_URL=$REACT_APP_WS_URL

COPY package*.json ./
RUN npm ci

COPY public ./public
COPY src ./src
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
