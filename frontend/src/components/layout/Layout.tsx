import { useState } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import { Trophy, Users, Tag, Building2, Settings, Menu, X, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'
import PadelAdminLogo from '@/components/logo/PadelAdminLogo'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useAuth } from '@/contexts/AuthContext'

const baseNavItems = [
  { to: '/tournaments', label: 'Torneos', icon: Trophy },
  { to: '/players', label: 'Jugadores', icon: Users },
  { to: '/categories', label: 'Categorías', icon: Tag },
  { to: '/complexes', label: 'Complejos', icon: Building2 },
]

function NavItem({ to, label, icon: Icon, hideLabel = false }: { to: string; label: string; icon: React.ElementType; hideLabel?: boolean }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
          isActive
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
        )
      }
      title={hideLabel ? label : undefined}
    >
      <Icon size={16} className="shrink-0" />
      {!hideLabel && label}
    </NavLink>
  )
}

function UserFooter({ hideLabels = false }: { hideLabels?: boolean }) {
  const { user, isAdmin, logout } = useAuth()
  if (!user) return null

  if (hideLabels) {
    return (
      <div className="p-3 border-t flex justify-center">
        <Button size="sm" variant="ghost" onClick={logout} title={`Cerrar sesión (${user.email})`}>
          <LogOut size={16} />
        </Button>
      </div>
    )
  }

  return (
    <div className="p-3 border-t space-y-2">
      <div className="px-1 space-y-1">
        <p className="text-xs text-muted-foreground truncate" title={user.email}>{user.email}</p>
        <Badge variant={isAdmin ? 'default' : 'secondary'} className="text-[10px] uppercase">
          {isAdmin ? 'Admin' : 'Viewer'}
        </Badge>
      </div>
      <Button size="sm" variant="ghost" className="w-full justify-start text-muted-foreground" onClick={logout}>
        <LogOut size={14} className="mr-2" />
        Cerrar sesión
      </Button>
    </div>
  )
}

function Sidebar({ hideLabels = false }: { hideLabels?: boolean }) {
  const { isAdmin } = useAuth()

  return (
    <aside className={cn(
      'border-r flex flex-col bg-background',
      hideLabels ? 'w-16' : 'w-56'
    )}>
      <div className={cn(
        'p-4 border-b flex items-center gap-3',
        hideLabels && 'justify-center'
      )}>
        <PadelAdminLogo size={hideLabels ? 32 : 38} className="shrink-0" />
        {!hideLabels && (
          <div>
            <h1 className="text-base font-bold tracking-tight leading-tight">Padel Admin</h1>
            <p className="text-xs text-muted-foreground">Panel de gestión</p>
          </div>
        )}
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {baseNavItems.map(({ to, label, icon }) => (
          <NavItem key={to} to={to} label={label} icon={icon} hideLabel={hideLabels} />
        ))}
      </nav>
      {isAdmin && (
        <div className={cn(
          'p-3 border-t space-y-1',
          hideLabels && 'items-center flex flex-col'
        )}>
          {!hideLabels && (
            <p className="px-3 pb-1 text-[10px] uppercase tracking-widest text-muted-foreground/60 font-semibold">
              Admin
            </p>
          )}
          <NavItem to="/settings" label="Configuración" icon={Settings} hideLabel={hideLabels} />
        </div>
      )}
      <UserFooter hideLabels={hideLabels} />
    </aside>
  )
}

export default function Layout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const { user, isAdmin, logout } = useAuth()

  return (
    <div className="flex h-screen bg-background">
      {/* Desktop Sidebar (lg+) */}
      <div className="hidden lg:block">
        <Sidebar hideLabels={false} />
      </div>

      {/* Tablet Sidebar (md-lg) */}
      <div className="hidden md:block lg:hidden">
        <Sidebar hideLabels={true} />
      </div>

      {/* Main Container */}
      <div className="flex flex-col flex-1 overflow-hidden">
        {/* Mobile Header (< md) */}
        <div className="md:hidden border-b bg-background flex items-center justify-between px-4 py-3">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="mr-2"
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </Button>
          <div className="flex items-center gap-2">
            <PadelAdminLogo size={28} className="shrink-0" />
            <h1 className="text-sm font-bold">Padel Admin</h1>
          </div>
          <div className="w-12" /> {/* Spacer for centering */}
        </div>

        {/* Mobile Drawer Menu (< md) */}
        {mobileMenuOpen && (
          <div className="md:hidden absolute inset-0 top-14 z-40">
            <div
              className="absolute inset-0 bg-black/50"
              onClick={() => setMobileMenuOpen(false)}
            />
            <div className="absolute left-0 top-0 h-full w-56 bg-background border-r shadow-lg flex flex-col">
              <nav className="flex-1 p-3 space-y-1">
                {baseNavItems.map(({ to, label, icon }) => (
                  <div key={to} onClick={() => setMobileMenuOpen(false)}>
                    <NavItem to={to} label={label} icon={icon} hideLabel={false} />
                  </div>
                ))}
              </nav>
              {isAdmin && (
                <div className="p-3 border-t space-y-1">
                  <p className="px-3 pb-1 text-[10px] uppercase tracking-widest text-muted-foreground/60 font-semibold">
                    Admin
                  </p>
                  <div onClick={() => setMobileMenuOpen(false)}>
                    <NavItem to="/settings" label="Configuración" icon={Settings} hideLabel={false} />
                  </div>
                </div>
              )}
              {user && (
                <div className="p-3 border-t space-y-2">
                  <div className="px-1 space-y-1">
                    <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                    <Badge variant={isAdmin ? 'default' : 'secondary'} className="text-[10px] uppercase">
                      {isAdmin ? 'Admin' : 'Viewer'}
                    </Badge>
                  </div>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="w-full justify-start text-muted-foreground"
                    onClick={() => {
                      setMobileMenuOpen(false)
                      logout()
                    }}
                  >
                    <LogOut size={14} className="mr-2" />
                    Cerrar sesión
                  </Button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Main content */}
        <main className="flex-1 overflow-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
