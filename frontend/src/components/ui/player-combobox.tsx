import { useState } from 'react'
import { Check, ChevronsUpDown } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import type { Player } from '@/types'

interface Props {
  players: Player[]
  value: string        // playerId como string, '' si no hay selección
  onSelect: (playerId: string) => void
  placeholder?: string
  disabled?: boolean
}

export function PlayerCombobox({
  players,
  value,
  onSelect,
  placeholder = 'Seleccioná jugador',
  disabled,
}: Props) {
  const [open, setOpen] = useState(false)

  // Ordenados alfabéticamente por apellido, luego nombre
  const sorted = [...players].sort((a, b) => {
    const la = `${a.lastName} ${a.firstName}`.toLowerCase()
    const lb = `${b.lastName} ${b.firstName}`.toLowerCase()
    return la.localeCompare(lb, 'es')
  })

  const selected = players.find((p) => String(p.id) === value)
  const label = selected
    ? `${selected.lastName}, ${selected.firstName}`
    : placeholder

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn(
            'w-full justify-between font-normal',
            !selected && 'text-muted-foreground'
          )}
        >
          <span className="truncate">{label}</span>
          <ChevronsUpDown size={14} className="ml-2 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command>
          <CommandInput placeholder="Buscar jugador..." />
          <CommandList>
            <CommandEmpty>No se encontró ningún jugador.</CommandEmpty>
            <CommandGroup>
              {sorted.map((p) => (
                <CommandItem
                  key={p.id}
                  value={`${p.lastName} ${p.firstName}`}
                  onSelect={() => {
                    onSelect(String(p.id))
                    setOpen(false)
                  }}
                >
                  <Check
                    size={14}
                    className={cn(
                      'mr-2 shrink-0',
                      value === String(p.id) ? 'opacity-100' : 'opacity-0'
                    )}
                  />
                  {p.lastName}, {p.firstName}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
