
export default function ChatMessage({ message }) {
  const mine = message.from === 'user'

  return (
    <div className={`chat-row ${mine ? 'mine' : 'theirs'}`}>
      <div className="chat-bubble">{message.text}</div>
    </div>
  )
}
