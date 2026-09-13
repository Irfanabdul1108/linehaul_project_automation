import { useEffect, useRef, useState } from 'react'
import ChatMessage from './ChatMessage.jsx'
import { askAssistant } from '../services/chatService.js'
import { useActiveWarehouseId, loadWarehouses, warehousePlace } from '../services/warehouse.js'

const WELCOME = {
  id: 'welcome',
  from: 'bot',
  text: 'Hi! I am the Linehaul Assistant. Ask me about orders, routes, drivers and vehicles.',
}

const SUGGESTIONS = [
  'How many routes are there?',
  'How many drivers are available?',
  'How many vehicles are available?',
  'How many READY orders are there?',
  'How many orders are unassigned?',
  'Show blocked routes.',
]

export default function ChatAssistant() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([WELCOME])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const nextId = useRef(1)
  const listRef = useRef(null)
  const warehouseId = useActiveWarehouseId()
  const [scope, setScope] = useState('')

  useEffect(() => {
    if (!warehouseId) {
      setScope('')
      return
    }
    loadWarehouses()
      // the assistant answers about a place, so it says the place
      .then((rows) => setScope(warehousePlace(rows.find((row) => row.warehouseId === warehouseId)) || warehouseId))
      .catch(() => setScope(warehouseId))
  }, [warehouseId])

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages, loading, open])

  const send = async (text) => {
    const question = (text || '').trim()
    if (!question || loading) return

    const id = nextId.current++
    setMessages((current) => [...current, { id: `you-${id}`, from: 'user', text: question }])
    setInput('')
    setLoading(true)

    const answer = await askAssistant(question)

    setLoading(false)
    setMessages((current) => [...current, { id: `bot-${id}`, from: 'bot', text: answer }])
  }

  const submit = (event) => {
    event.preventDefault()
    send(input)
  }

  const fresh = !messages.some((message) => message.from === 'user')

  if (!open) {
    return (
      <button type="button" className="chat-fab" onClick={() => setOpen(true)}>
        💬 Ask Linehaul
      </button>
    )
  }

  return (
    <section className="chat-window" aria-label="Linehaul Assistant">
      <div className="chat-head">
        <div>
          <strong>Linehaul Assistant</strong>
          <span className="chat-sub">
            Answers from your current Linehaul data{scope ? ` - ${scope}` : ''}
          </span>
        </div>
        <button type="button" className="close-x" onClick={() => setOpen(false)} aria-label="Close">
          ×
        </button>
      </div>

      <div className="chat-list" ref={listRef}>
        {messages.map((message) => (
          <ChatMessage key={message.id} message={message} />
        ))}

        {loading && (
          <div className="chat-row theirs">
            <div className="chat-bubble chat-loading">Searching Linehaul data...</div>
          </div>
        )}

        {fresh && !loading && (
          <div className="chat-chips">
            {SUGGESTIONS.map((question) => (
              <button type="button" key={question} className="chat-chip" onClick={() => send(question)}>
                {question}
              </button>
            ))}
          </div>
        )}
      </div>

      <form className="chat-form" onSubmit={submit}>
        <input
          type="text"
          value={input}
          autoFocus
          aria-label="Your question"
          placeholder="Ask about orders, routes, drivers or vehicles"
          onChange={(event) => setInput(event.target.value)}
        />
        <button type="submit" className="btn btn-primary" disabled={loading || !input.trim()}>
          Send
        </button>
      </form>
    </section>
  )
}
