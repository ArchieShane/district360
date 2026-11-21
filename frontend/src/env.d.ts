/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BASEMAP_STYLE?: string
  readonly VITE_HOME_BOUNDS?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
