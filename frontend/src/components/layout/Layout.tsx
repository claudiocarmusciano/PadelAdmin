import { Outlet, NavLink } from 'react-router-dom'
import { Trophy, Users, Tag, Building2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import PadelAdminLogo from '@/components/logo/PadelAdminLogo'

const navItems = [
  { to: '/tournaments', label: 'Torneos', icon: Trophy },
  { to: '/players', label: 'Jugadores', icon: Users },
  { to: '/categories', label: 'Categorías', icon: Tag },
  { to: '/complexes', label: 'Complejos', icon: Building2 },
]

export default function Layout() {
  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="w-56 border-r flex flex-col">
        <div className="p-4 border-b flex items-center gap-3">
          <PadelAdminLogo size={38} className="shrink-0" />
          <div>
            <h1 className="text-base font-bold tracking-tight leading-tight">Padel Admin</h1>
            <p className="text-xs text-muted-foreground">Panel de gestión</p>
          </div>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                )
              }
            >
              <Icon size={16} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="p-4 border-t text-xs text-muted-foreground">v1.0.0</div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto p-6">
        <Outlet />
      </main>
    </div>
  )
}
