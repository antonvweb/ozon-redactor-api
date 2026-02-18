// Common environment variables for all services
const commonEnv = {
  SPRING_PROFILES_ACTIVE: "prod",
  TZ: "Europe/Moscow",

  // Database (non-standard port)
  DB_URL: process.env.DB_URL || "jdbc:postgresql://localhost:15432/ozon_label_bd",
  DB_USERNAME: process.env.DB_USERNAME || "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD,

  // Redis (non-standard port)
  REDIS_HOST: process.env.REDIS_HOST || "localhost",
  REDIS_PORT: process.env.REDIS_PORT || "16379",
  REDIS_PASSWORD: process.env.REDIS_PASSWORD,

  // JWT
  JWT_SECRET: process.env.JWT_SECRET,
  JWT_EXPIRATION: process.env.JWT_EXPIRATION || "86400000",
  JWT_REFRESH_EXPIRATION: process.env.JWT_REFRESH_EXPIRATION || "1209600000",

  // Cookies
  COOKIE_SECURE: process.env.COOKIE_SECURE || "true",
  COOKIE_DOMAIN: process.env.COOKIE_DOMAIN || ".print-365.ru",

  // Email
  MAIL_HOST: process.env.MAIL_HOST || "smtp.yandex.ru",
  MAIL_PORT: process.env.MAIL_PORT || "587",
  MAIL_USERNAME: process.env.MAIL_USERNAME,
  MAIL_PASSWORD: process.env.MAIL_PASSWORD,

  // Logging (production)
  LOG_LEVEL: process.env.LOG_LEVEL || "WARN",
  SHOW_SQL: process.env.SHOW_SQL || "false"
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
        UPLOAD_DIR: process.env.UPLOAD_DIR || "/var/www/labelEditor/uploads",
        UPLOAD_BASE_URL: process.env.UPLOAD_BASE_URL || "https://api.print-365.ru/uploads"
      }
    }
  ]
};
