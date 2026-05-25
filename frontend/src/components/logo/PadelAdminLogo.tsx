interface Props {
  size?: number
  className?: string
}

/**
 * Ícono de PadelAdmin — raqueta de pádel sobre fondo naranja redondeado.
 * Diseñado como SVG inline para evitar dependencias de archivos externos.
 */
export default function PadelAdminLogo({ size = 40, className }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      {/* Fondo naranja redondeado */}
      <rect width="100" height="100" rx="22" fill="#FF7835" />

      {/* ── Raqueta de pádel ─────────────────────────────────────── */}
      {/* Cabeza de la raqueta — óvalo */}
      <ellipse
        cx="50"
        cy="42"
        rx="22"
        ry="24"
        fill="white"
        opacity="0.95"
      />
      {/* Borde interior de la cabeza (efecto profundidad) */}
      <ellipse
        cx="50"
        cy="42"
        rx="17"
        ry="19"
        fill="#FF7835"
        opacity="0.6"
      />

      {/* Agujeros de la raqueta — 3 filas */}
      {/* Fila superior */}
      <circle cx="44" cy="34" r="2.8" fill="white" opacity="0.9" />
      <circle cx="50" cy="32" r="2.8" fill="white" opacity="0.9" />
      <circle cx="56" cy="34" r="2.8" fill="white" opacity="0.9" />
      {/* Fila media */}
      <circle cx="41" cy="42" r="2.8" fill="white" opacity="0.9" />
      <circle cx="50" cy="41" r="2.8" fill="white" opacity="0.9" />
      <circle cx="59" cy="42" r="2.8" fill="white" opacity="0.9" />
      {/* Fila inferior */}
      <circle cx="44" cy="50" r="2.8" fill="white" opacity="0.9" />
      <circle cx="50" cy="52" r="2.8" fill="white" opacity="0.9" />
      <circle cx="56" cy="50" r="2.8" fill="white" opacity="0.9" />

      {/* Mango de la raqueta */}
      <rect
        x="45"
        y="64"
        width="10"
        height="20"
        rx="5"
        fill="white"
        opacity="0.95"
      />
      {/* Cuello (unión cabeza-mango) */}
      <rect
        x="46"
        y="61"
        width="8"
        height="6"
        rx="2"
        fill="white"
        opacity="0.95"
      />
    </svg>
  )
}
