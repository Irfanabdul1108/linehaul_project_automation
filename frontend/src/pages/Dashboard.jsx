import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboard, getDrivers, getVehicles } from '../services/api.js'
import SmartAssignModal from '../components/SmartAssignModal.jsx'
import AutoAssignModal from '../components/AutoAssignModal.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import ProgressBar from '../components/ProgressBar.jsx'
import OrderModal from '../components/OrderModal.jsx'

export default function Dashboard({ notify }) {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [order, setOrder] = useState(null)
  const [smartOrder, setSmartOrder] = useState(null)
  const [autoOpen, setAutoOpen] = useState(false)
  const [drivers, setDrivers] = useState([])
  const [vehicles, setVehicles] = useState([])

  function load() {
    return getDashboard()
      .then(setSummary)
      .catch((err) => setError(err.message))
  }

  useEffect(() => {
    load()
    getDrivers().then(setDrivers).catch(() => {})
    getVehicles().then(setVehicles).catch(() => {})
  }, [])

  if (error) return <div className="error-text">{error}</div>
  if (!summary) return <div className="empty">Loading dashboard...</div>

  const cards = [
    { label: 'Total Orders', value: summary.totalOrders, tone: '' },
    { label: 'Unassigned', value: summary.unassignedOrders ?? 0, tone: 'yellow' },
    { label: 'Active Routes', value: summary.activeRoutes, tone: '' },
    { label: 'Ready Routes', value: summary.readyRoutes, tone: 'green' },
    { label: 'Blocked Routes', value: summary.blockedRoutes, tone: 'red' },
  ]

  return (
    <>
      <section className="panel auto-panel" data-testid="auto-panel">
        <div className="panel-head">
          <span>
            Smart assignment
            <small className="auto-panel-scope">
              {summary.warehouseName || 'all warehouses'}
              {summary.warehouseDesignation && summary.warehouseDesignation !== summary.warehouseName
                ? ` · ${summary.warehouseDesignation}`
                : ''}
            </small>
          </span>
          <button
            type="button"
            className="btn btn-sm btn-primary"
            onClick={() => setAutoOpen(true)}
            disabled={(summary.unassignedOrders || 0) === 0}
          >
            ⚡ Assign all {summary.unassignedOrders || 0} unassigned orders
          </button>
        </div>
        <div className="auto-panel-body">
          <div className="auto-panel-text">
            {summary.unassignedOrders > 0 ? (
              <>
                <strong>
                  {summary.unassignedOrders} order(s) in {summary.warehouseName || 'this warehouse'} have
                  no route yet.
                </strong>
                <p>
                  The planner reads each order, inspects every route in all{' '}
                  {summary.networkWarehouses || 4} warehouses, checks the destination, the loading stop,
                  the remaining capacity and the truck, then ranks what is left. You confirm the result -
                  nothing is assigned behind your back.
                </p>
              </>
            ) : (
              <>
                <strong>Every order in this warehouse is on a route.</strong>
                <p>New orders appear here as soon as they are created.</p>
              </>
            )}
          </div>
          <div className="auto-panel-stats">
            <div>
              <span>{summary.networkRoutes || 0}</span>
              <small>routes in the network</small>
            </div>
            <div>
              <span>{summary.availableVehicles || 0}</span>
              <small>free trucks here</small>
            </div>
            <div>
              <span>{summary.availableDrivers || 0}</span>
              <small>free drivers here</small>
            </div>
          </div>
        </div>
      </section>

      <div className="cards">
        {cards.map((card) => (
          <div key={card.label} className={`card stat-card ${card.tone}`}>
            <div className="label">{card.label}</div>
            <div className="value">{card.value}</div>
          </div>
        ))}
      </div>

      <div className="cards">
        <div className="card stat-card gray">
          <div className="label">Waiting for a route</div>
          <div className="value">{summary.ordersToDispatch.length}</div>
        </div>
        <div className="card stat-card">
          <div className="label">Trucks</div>
          <div className="value">{summary.totalVehicles}</div>
        </div>
        <div className="card stat-card">
          <div className="label">Drivers</div>
          <div className="value">{summary.totalDrivers}</div>
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <span>Orders to Dispatch</span>
          <Link className="btn btn-sm" to="/orders">
            Open Orders
          </Link>
        </div>
        {summary.ordersToDispatch.length === 0 ? (
          <div className="empty">Every order is on a route. Nothing waiting.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Customer</th>
                <th>Origin</th>
                <th>Destination</th>
                <th>Weight</th>
                <th>Status</th>
                <th>ETA</th>
                <th>Route</th>
              </tr>
            </thead>
            <tbody>
              {summary.ordersToDispatch.slice(0, 12).map((order) => (
                <tr key={order.orderId} className="clickable" onClick={() => setOrder(order)}>
                  <td>{order.orderId}</td>
                  <td>{order.customer}</td>
                  <td>{order.origin}</td>
                  <td>{order.destination}</td>
                  <td>{order.weight.toLocaleString()} kg</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                  <td>{order.eta || '-'}</td>
                  <td>{order.routeId || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {summary.ordersToDispatch.length > 12 ? (
          <div className="panel-body muted">
            Showing 12 of {summary.ordersToDispatch.length} orders.
          </div>
        ) : null}
      </div>

      <div className="panel">
        <div className="panel-head">
          <span>Active Routes</span>
          <Link className="btn btn-sm" to="/routes">
            Open Routes
          </Link>
        </div>
        {summary.activeRouteList.length === 0 ? (
          <div className="empty">No routes yet.</div>
        ) : (
          <div className="panel-body">
            <div className="route-grid">
              {summary.activeRouteList.map((route) => (
                <div key={route.routeId} className="route-card">
                  <h3>{route.routeId}</h3>
                  <div className="route-lane">
                    {route.origin} &rarr; {route.destination}
                  </div>
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
                  <ProgressBar percent={route.capacityPercent} />
                  <div className="progress-label">
                    <span>
                      {route.currentWeight.toLocaleString()} / {route.maxCapacity.toLocaleString()} kg
                    </span>
                    <span>{route.capacityPercent}%</span>
                  </div>
                  <div className="kv" style={{ marginTop: 6 }}>
                    <span className="k">Status</span>
                    <StatusBadge status={route.status} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {order ? <OrderModal order={order} onClose={() => setOrder(null)} /> : null}

      {smartOrder ? (
        <SmartAssignModal
          order={smartOrder}
          drivers={drivers}
          vehicles={vehicles}
          notify={notify}
          onClose={() => setSmartOrder(null)}
          onAssigned={load}
        />
      ) : null}

      {autoOpen ? (
        <AutoAssignModal
          orders={summary.unassignedOrderList || []}
          warehouseName={summary.warehouseName}
          notify={notify}
          onClose={() => setAutoOpen(false)}
          onFinished={load}
          onOpenOrder={(picked) => {
            setAutoOpen(false)
            setSmartOrder(picked)
          }}
        />
      ) : null}
    </>
  )
}
