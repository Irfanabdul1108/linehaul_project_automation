import StatusBadge from './StatusBadge.jsx'

export default function OrderCard({ order, onClick }) {
  return (
    <div className="drag-order" onClick={() => onClick?.(order)} style={{ cursor: 'pointer' }}>
      <div className="id">{order.orderId}</div>
      <div className="meta">
        {order.customer} - {order.origin} &rarr; {order.destination}
      </div>
      <div className="meta">
        {order.weight.toLocaleString()} kg - {order.eta || 'no ETA yet'}{' '}
        <StatusBadge status={order.status} />
      </div>
    </div>
  )
}
