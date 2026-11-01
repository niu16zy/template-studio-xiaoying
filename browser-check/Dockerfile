FROM mcr.microsoft.com/playwright:v1.64.1-noble
WORKDIR /checks
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY check.mjs ./
CMD ["npm", "test"]
