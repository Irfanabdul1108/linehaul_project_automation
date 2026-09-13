import StatusBadge from './StatusBadge.jsx'
import ProgressBar from './ProgressBar.jsx'
import { useActiveWarehouseId } from '../services/warehouse.js'

export default function RouteCard({
  route,
  truckName,
  driverName,
  onView,
  onAssignTruck,
  onAssignDriver,
  onDispatch,
}) {
  const activeWarehouse = useActiveWarehouseId()
  const foreign = route.warehouseId && activeWarehouse && route.warehouseId !== activeWarehouse
  const locked = ['DISPATCHED', 'IN_TRANSIT', 'COMPLETED'].includes(
    String(route.status).toUpperCase()
  )
  const ready = String(route.readiness).toUpperCase() === 'READY'

  return (
    <div className="route-card">
      <h3>{route.routeId}</h3>
      <div className="route-lane">
        {route.origin} &rarr; {route.destination}
        {route.stops?.length ? <span className="route-via">via {route.stops.join(', ')}</span> : null}
      </div>
      {foreign ? (
        <div className="route-owner">
          <span className="badge badge-gray">{route.warehouseId} route</span>
        </div>
      ) : null}

      <div className="kv">
        <span className="k">Departure</span>
        <span>{route.departureTime}</span>
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

      <ProgressBar percent={route.capacityPercent} />
      <div className="progress-label">
        <span>Capacity</span>
        <span>{route.capacityPercent}%</span>
      </div>

      <div className="kv">
        <span className="k">Truck</span>
        <span>{route.truckId ? `${route.truckId}${truckName ? ` - ${truckName}` : ''}` : '-'}</span>
      </div>
      <div className="kv">
        <span className="k">Driver</span>
        <span>{route.driverId ? `${route.driverId}${driverName ? ` - ${driverName}` : ''}` : '-'}</span>
      </div>
      <div className="kv">
        <span className="k">Status</span>
        <StatusBadge status={route.status} />
      </div>

      <div className="btn-row" style={{ marginTop: 12 }}>
        <button className="btn btn-sm" onClick={() => onView(route)}>
          View
        </button>
        <button className="btn btn-sm" onClick={() => onAssignTruck(route)} disabled={locked}>
          Assign Truck
        </button>
        <button className="btn btn-sm" onClick={() => onAssignDriver(route)} disabled={locked}>
          Assign Driver
        </button>
        <button
          className={`btn btn-sm ${ready ? 'btn-green' : ''}`}
          onClick={() => onDispatch(route)}
          disabled={locked}
        >
          Dispatch
        </button>
      </div>
    </div>
  )
}
