FROM node:22-alpine
WORKDIR /app
COPY backend/ ./
ENV PORT=7860
EXPOSE 7860
USER node
CMD ["node", "server.js"]
