const COLORS = {
  READY: 'green',
  ON_TIME: 'green',
  AVAILABLE: 'green',
  COMPLETED: 'green',
  ASSIGNED: 'yellow',
  MANIFESTED: 'yellow',
  AT_RISK: 'yellow',
  BLOCKED: 'red',
  DELAYED: 'red',
  MAINTENANCE: 'red',
  IN_TRANSIT: 'blue',
  DISPATCHED: 'blue',
  DRAFT: 'gray',
  CREATED: 'gray',
}

export function colorOf(status) {
  return COLORS[String(status || '').toUpperCase()] || 'gray'
}

export default function StatusBadge({ status }) {
  const value = String(status || 'UNKNOWN').toUpperCase()
  return <span className={`badge badge-${colorOf(value)}`}>{value.replace(/_/g, ' ')}</span>
}
