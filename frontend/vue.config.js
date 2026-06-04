const amapProxyTarget = process.env.VUE_APP_AMAP_NGINX || 'http://127.0.0.1:80'

module.exports = {
  parallel: false,
  chainWebpack: (config) => {
    config.plugins.delete('prefetch')
  },
  devServer: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/upload': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/_AMapService': {
        target: amapProxyTarget,
        changeOrigin: true,
        secure: false
      }
    }
  }
}
