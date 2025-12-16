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
        SPRING_PROFILES_ACTIVE: "prod",
        TZ: "Europe/Moscow"
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
        SPRING_PROFILES_ACTIVE: "prod",
        TZ: "Europe/Moscow"
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
        SPRING_PROFILES_ACTIVE: "prod",
        TZ: "Europe/Moscow"
      }
    }
  ]
};
