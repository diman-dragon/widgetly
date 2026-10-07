import type { ReactNode } from 'react'
import { X } from 'lucide-react'

export function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label={title} onClick={e => e.stopPropagation()}>
        <div className="sheet-head">{title}<button aria-label="Закрыть" onClick={onClose}><X /></button></div>
        {children}
      </div>
    </div>
  )
}
