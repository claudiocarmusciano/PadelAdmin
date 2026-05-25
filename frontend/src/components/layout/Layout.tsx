import { useState } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import { Trophy, Users, Tag, Building2, Settings, Menu, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import PadelAdminLogo from '@/components/logo/PadelAdminLogo'
import { Button } from '@/components/ui/button'

const navItems = [
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

function Sidebar({ hideLabels = false }: { hideLabels?: boolean }) {
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
        {navItems.map(({ to, label, icon }) => (
          <NavItem key={to} to={to} label={label} icon={icon} hideLabel={hideLabels} />
        ))}
      </nav>
      {/* Admin section */}
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
      {!hideLabels && (
        <div className="px-4 py-3 border-t text-xs text-muted-foreground">v1.0.0</div>
      )}
    </aside>
  )
}

export default function Layout() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

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
            <div className="flex flex-col">
              <h1 className="text-sm font-bold">Padel Admin</h1>
            </div>
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
            <div className="absolute left-0 top-0 h-full w-56 bg-background border-r shadow-lg">
              <nav className="p-3 space-y-1">
                {navItems.map(({ to, label, icon }) => (
                  <div key={to} onClick={() => setMobileMenuOpen(false)}>
                    <NavItem to={to} label={label} icon={icon} hideLabel={false} />
                  </div>
                ))}
              </nav>
              {/* Admin section */}
              <div className="p-3 border-t space-y-1">
                <p className="px-3 pb-1 text-[10px] uppercase tracking-widest text-muted-foreground/60 font-semibold">
                  Admin
                </p>
                <div onClick={() => setMobileMenuOpen(false)}>
                  <NavItem to="/settings" label="Configuración" icon={Settings} hideLabel={false} />
                </div>
              </div>
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
