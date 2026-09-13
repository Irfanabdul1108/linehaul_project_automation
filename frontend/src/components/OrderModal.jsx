import Modal from './Modal.jsx'
import StatusBadge from './StatusBadge.jsx'

const STEPS = [
  { key: 'CREATED', label: 'Created' },
  { key: 'READY', label: 'Ready' },
  { key: 'MANIFESTED', label: 'Manifested' },
  { key: 'ASSIGNED', label: 'Assigned' },
  { key: 'DISPATCHED', label: 'Dispatched' },
  { key: 'IN_TRANSIT', label: 'In Transit' },
  { key: 'COMPLETED', label: 'Completed' },
]

function timelineState(status) {
  const current = String(status || '').toUpperCase()
  const index = STEPS.findIndex((step) => step.key === current)
  if (index === -1) {
   
    return { current: -1, lastDone: -1 }
  }
  return { current: index, lastDone: index - 1 }
}

export default function OrderModal({ order, onClose, footer }) {
  if (!order) return null

  const { current, lastDone } = timelineState(order.status)
  const blocked = String(order.status).toUpperCase() === 'BLOCKED'

  return (
    <Modal title={`Order ${order.orderId}`} onClose={onClose} footer={footer}>
      <div className="grid-2">
        <div>
          <div className="kv">
            <span className="k">Order ID</span>
            <span>{order.orderId}</span>
          </div>
          <div className="kv">
            <span className="k">Customer</span>
            <span>{order.customer}</span>
          </div>
          <div className="kv">
            <span className="k">Origin</span>
            <span>{order.origin}</span>
          </div>
          <div className="kv">
            <span className="k">Destination</span>
            <span>{order.destination}</span>
          </div>
        </div>
        <div>
          <div className="kv">
            <span className="k">Weight</span>
            <span>{order.weight.toLocaleString()} kg</span>
          </div>
          <div className="kv">
            <span className="k">Pieces</span>
            <span>{order.pieces}</span>
          </div>
          <div className="kv">
            <span className="k">Service date</span>
            <span>{order.serviceDate || '-'}</span>
          </div>
          <div className="kv">
            <span className="k">Status</span>
            <StatusBadge status={order.status} />
          </div>
          <div className="kv">
            <span className="k">ETA</span>
            <span>{order.eta || '-'}</span>
          </div>
          <div className="kv">
            <span className="k">Assigned route</span>
            <span>{order.routeId || 'not assigned'}</span>
          </div>
        </div>
      </div>

      {blocked ? (
        <div className="readiness-box blocked" style={{ marginTop: 14 }}>
          <strong>Order is blocked</strong>
          This order cannot move until the problem on it is fixed.
        </div>
      ) : null}

      <h4 style={{ marginBottom: 0, marginTop: 18 }}>Timeline</h4>
      <ul className="timeline">
        {STEPS.map((step, index) => {
          const className =
            index === current ? 'current' : index <= lastDone ? 'done' : ''
          return (
            <li key={step.key} className={className}>
              {step.label}
            </li>
          )
        })}
      </ul>
    </Modal>
  )
}
