import { useCallback, useRef, useState } from 'react'


export function useToasts() {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const notify = useCallback((message, type = 'success') => {
    const id = nextId.current++
    setToasts((current) => [...current, { id, message, type }])
    setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, 3500)
  }, [])

  return { toasts, notify }
}

export default function Toasts({ toasts }) {
  return (
    <div className="toasts">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast ${toast.type}`}>
          {toast.message}
        </div>
      ))}
    </div>
  )
}
