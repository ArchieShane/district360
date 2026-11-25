/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 开发代理目标，默认 http://localhost:8080 */
  readonly VITE_API_PROXY?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
