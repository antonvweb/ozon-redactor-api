// Load .env file
const dotenv = require('dotenv');
const fs = require('fs');
const path = require('path');

// Load .env from backend directory
const envPath = '/var/www/labelEditor/backend/.env';
const envConfig = fs.existsSync(envPath) ? dotenv.parse(fs.readFileSync(envPath)) : {};

// Common environment variables for all services
const commonEnv = {
  SPRING_PROFILES_ACTIVE: "prod",
  TZ: "Europe/Moscow",

  // Database (non-standard port)
  DB_URL: envConfig.DB_URL || "jdbc:postgresql://localhost:15432/ozon_label_bd",
  DB_USERNAME: envConfig.DB_USERNAME || "postgres",
  DB_PASSWORD: envConfig.DB_PASSWORD,

  // Redis (non-standard port)
  REDIS_HOST: envConfig.REDIS_HOST || "localhost",
  REDIS_PORT: envConfig.REDIS_PORT || "16379",
  REDIS_PASSWORD: envConfig.REDIS_PASSWORD,

  // JWT
  JWT_SECRET: envConfig.JWT_SECRET,
  JWT_EXPIRATION: envConfig.JWT_EXPIRATION || "86400000",
  JWT_REFRESH_EXPIRATION: envConfig.JWT_REFRESH_EXPIRATION || "1209600000",

  // Cookies
  COOKIE_SECURE: envConfig.COOKIE_SECURE || "true",
  COOKIE_DOMAIN: envConfig.COOKIE_DOMAIN || "print-365.ru",

  // Email
  MAIL_HOST: envConfig.MAIL_HOST || "smtp.yandex.ru",
  MAIL_PORT: envConfig.MAIL_PORT || "587",
  MAIL_USERNAME: envConfig.MAIL_USERNAME,
  MAIL_PASSWORD: envConfig.MAIL_PASSWORD,

  // Logging (production)
  LOG_LEVEL: envConfig.LOG_LEVEL || "WARN",
  SHOW_SQL: envConfig.SHOW_SQL || "false"
};

module.exports = {
  apps: [
    {
      name: "auth-service",
      script: "java",
      args: ["-jar", "authService.jar"],
      cwd: "/var/www/labelEditor/backend/authService/target",
      exec_mode: "fork",
      instances: 1,
      autorestart: true,
      max_memory_restart: "1G",
      env: {
        ...commonEnv,
        SERVER_PORT: "9147"
      }
    },

    {
      name: "user-service",
      script: "java",
      args: ["-jar", "userService-1.0.0-exec.jar"],
      cwd: "/var/www/labelEditor/backend/userService/target",
      exec_mode: "fork",
      instances: 1,
      autorestart: true,
      max_memory_restart: "1G",
      env: {
        ...commonEnv,
        SERVER_PORT: "7293"
      }
    },

    {
      name: "ozon-api",
      script: "java",
      args: ["-jar", "ozonApi.jar"],
      cwd: "/var/www/labelEditor/backend/ozonApi/target",
      exec_mode: "fork",
      instances: 1,
      autorestart: true,
      max_memory_restart: "1G",
      env: {
        ...commonEnv,
        SERVER_PORT: "6482",
        UPLOAD_DIR: envConfig.UPLOAD_DIR || "/var/www/labelEditor/uploads",
        UPLOAD_BASE_URL: envConfig.UPLOAD_BASE_URL || "https://api.print-365.ru/uploads"
      }
    }
  ]
};
