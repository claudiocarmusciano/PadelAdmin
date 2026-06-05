import { Component, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  message?: string
}

/**
 * Captura errores de render de cualquier componente hijo y muestra una pantalla
 * de "algo salió mal" en vez de dejar la app en negro. Sin esto, una excepción
 * en un componente tira abajo todo el árbol de React.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(error: unknown): State {
    return { hasError: true, message: error instanceof Error ? error.message : String(error) }
  }

  componentDidCatch(error: unknown, info: unknown) {
    // Log para diagnóstico (consola del navegador)
    console.error('ErrorBoundary capturó un error:', error, info)
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div className="min-h-screen flex items-center justify-center bg-background p-6 text-center">
        <div className="max-w-sm space-y-4">
          <div className="text-4xl">⚠️</div>
          <h1 className="text-lg font-bold">Algo salió mal</h1>
          <p className="text-sm text-muted-foreground">
            Ocurrió un error inesperado en la aplicación. Probá recargar la página.
          </p>
          {this.state.message && (
            <p className="text-xs text-muted-foreground/60 break-words">{this.state.message}</p>
          )}
          <button
            onClick={() => window.location.reload()}
            className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            Recargar
          </button>
        </div>
      </div>
    )
  }
}
