module.exports = {
  apps: [
    {
      name: 'v7-shop-mall',
      port: 3000,
      exec_mode: 'cluster',
      instances: '1',
      script: './.output/server/index.mjs',
      cwd: __dirname,
    },
  ],
};
