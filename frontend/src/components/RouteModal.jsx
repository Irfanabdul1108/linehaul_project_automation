import Modal from './Modal.jsx'
import StatusBadge from './StatusBadge.jsx'
import ProgressBar from './ProgressBar.jsx'
import RouteMap from './RouteMap.jsx'

function Check({ ok }) {
  return <span className={`tick ${ok ? 'ok' : 'no'}`}>{ok ? '\u2713' : '\u2717'}</span>
}

export default function RouteModal({
  route,
  orders = [],
  vehicles = [],
  drivers = [],
  onClose,
  onAssignTruck,
  onAssignDriver,
  onRelease,
  onDispatch,
  onRemoveOrder,
}) {
  if (!route) return null

  const truck = vehicles.find((v) => v.truckId === route.truckId)
  const driver = drivers.find((d) => d.driverId === route.driverId)
  const ready = String(route.readiness).toUpperCase() === 'READY'
  const locked = ['DISPATCHED', 'IN_TRANSIT', 'COMPLETED'].includes(
    String(route.status).toUpperCase()
  )

  return (
    <Modal title={`Route ${route.routeId}`} onClose={onClose} wide>
      <div className="grid-2">
        <div>
          <div className="kv">
            <span className="k">Route ID</span>
            <span>{route.routeId}</span>
          </div>
          <div className="kv">
            <span className="k">Origin</span>
            <span>{route.origin}</span>
          </div>
          <div className="kv">
            <span className="k">Destination</span>
            <span>{route.destination}</span>
          </div>
          <div className="kv">
            <span className="k">Departure</span>
            <span>{route.departureTime}</span>
          </div>
          <div className="kv">
            <span className="k">Travel duration</span>
            <span>{route.travelDuration} hours</span>
          </div>
          <div className="kv">
            <span className="k">ETA</span>
            <span>{route.eta || '-'}</span>
          </div>
          <div className="kv">
            <span className="k">Orders</span>
            <span>{route.orderIds?.length ?? 0}</span>
          </div>
          <div className="kv">
            <span className="k">Weight</span>
            <span>
              {route.currentWeight.toLocaleString()} / {route.maxCapacity.toLocaleString()} kg
            </span>
          </div>
          <div className="kv">
            <span className="k">Truck</span>
            <span>
              {route.truckId ? `${route.truckId}${truck ? ` - ${truck.type}` : ''}` : 'not assigned'}
            </span>
          </div>
          <div className="kv">
            <span className="k">Driver</span>
            <span>{route.driverId ? `${route.driverId}${driver ? ` - ${driver.name}` : ''}` : 'not assigned'}</span>
          </div>
          <div className="kv">
            <span className="k">Status</span>
            <StatusBadge status={route.status} />
          </div>

          <div style={{ marginTop: 10 }}>
            <ProgressBar percent={route.capacityPercent} />
            <div className="progress-label">
              <span>Capacity</span>
              <span>{route.capacityPercent}%</span>
            </div>
          </div>
        </div>

        <div>
          <RouteMap
            origin={route.origin}
            destination={route.destination}
            hours={route.travelDuration}
            eta={route.eta}
          />
        </div>
      </div>

      <h4 style={{ marginTop: 20, marginBottom: 6 }}>Route readiness</h4>
      <ul className="check-list">
        <li>
          <span>Orders</span>
          <Check ok={route.hasOrders} />
        </li>
        <li>
          <span>Truck</span>
          <Check ok={route.hasTruck} />
        </li>
        <li>
          <span>Driver</span>
          <Check ok={route.hasDriver} />
        </li>
        <li>
          <span>Capacity</span>
          <Check ok={route.capacityOk} />
        </li>
      </ul>

      {!locked ? (
        <div className="btn-row" style={{ marginBottom: 14 }}>
          <button className="btn btn-sm" onClick={() => onAssignTruck(route)}>
            Assign Truck
          </button>
          <button className="btn btn-sm" onClick={() => onAssignDriver(route)}>
            Assign Driver
          </button>
          {route.hasTruck ? (
            <button className="btn btn-sm" onClick={() => onRelease('truck', route)}>
              Remove Truck
            </button>
          ) : null}
          {route.hasDriver ? (
            <button className="btn btn-sm" onClick={() => onRelease('driver', route)}>
              Remove Driver
            </button>
          ) : null}
        </div>
      ) : null}

      {ready ? (
        <div className="readiness-box ready">
          <strong>READY TO DISPATCH</strong>
          Orders, truck, driver and capacity are all in place.
          <div style={{ marginTop: 10 }}>
            <button className="btn btn-green btn-sm" onClick={() => onDispatch(route)} disabled={locked}>
              DISPATCH
            </button>
          </div>
        </div>
      ) : (
        <div className="readiness-box blocked">
          <strong>BLOCKED</strong>
          Reason: {route.readinessReason}
          <div className="btn-row" style={{ marginTop: 10 }}>
            {!route.hasTruck ? (
              <button className="btn btn-sm" onClick={() => onAssignTruck(route)} disabled={locked}>
                Assign Truck
              </button>
            ) : null}
            {!route.hasDriver ? (
              <button className="btn btn-sm" onClick={() => onAssignDriver(route)} disabled={locked}>
                Assign Driver
              </button>
            ) : null}
            {!route.hasOrders ? (
              <span className="muted">Drag an order onto this route to fix it.</span>
            ) : null}
          </div>
        </div>
      )}

      <h4 style={{ marginBottom: 6 }}>Orders on this route</h4>
      {orders.length === 0 ? (
        <div className="empty">No orders assigned yet.</div>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Customer</th>
              <th>Weight</th>
              <th>Status</th>
              <th>ETA</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.orderId}>
                <td>{order.orderId}</td>
                <td>{order.customer}</td>
                <td>{order.weight.toLocaleString()} kg</td>
                <td>
                  <StatusBadge status={order.status} />
                </td>
                <td>{order.eta || '-'}</td>
                <td>
                  {!locked ? (
                    <button
                      className="btn btn-sm"
                      onClick={() => onRemoveOrder(route, order)}
                    >
                      Remove
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Modal>
  )
}
