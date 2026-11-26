/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 开发代理目标，默认 http://localhost:8080 */
  readonly VITE_API_PROXY?: string
  /** 接口前缀（相对页面地址），默认 api/v2/space；部署在 /district360/v2/ 下时为 ../api/v2/space */
  readonly VITE_API_BASE?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
