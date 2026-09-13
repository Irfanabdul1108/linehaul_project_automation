import { useEffect, useState } from 'react'
import {
  getRoutes,
  getOrders,
  getVehicles,
  getDrivers,
  getRouteOrders,
  assignOrderToRoute,
  removeOrderFromRoute,
  assignTruck,
  unassignTruck,
  assignDriver,
  unassignDriver,
  dispatchRoute,
} from '../services/api.js'
import ProgressBar from '../components/ProgressBar.jsx'
import RouteCard from '../components/RouteCard.jsx'
import RouteModal from '../components/RouteModal.jsx'
import AssignModal from '../components/AssignModal.jsx'
import CreateRouteModal from '../components/CreateRouteModal.jsx'
import SmartAssignModal from '../components/SmartAssignModal.jsx'
import AutoAssignModal from '../components/AutoAssignModal.jsx'
import { dropRejection, routeReaches } from '../services/routeLane.js'

const EDITABLE = ['DRAFT', 'READY', 'BLOCKED']

const PAGE_SIZE = 6

function PaginationBar({ page, totalPages, totalRoutes, onPage }) {
  if (totalPages <= 1) return null
  return (
    <div className="pagination">
      <button className="btn btn-sm" disabled={page === 1} onClick={() => onPage(page - 1)}>
        &larr; Prev
      </button>
      {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
        <button
          key={n}
          className={`page-btn${n === page ? ' active' : ''}`}
          onClick={() => onPage(n)}
        >
          {n}
        </button>
      ))}
      <button
        className="btn btn-sm"
        disabled={page === totalPages}
        onClick={() => onPage(page + 1)}
      >
        Next &rarr;
      </button>
      <span className="muted" style={{ fontSize: 12 }}>
        Page {page} of {totalPages} - {totalRoutes} routes, {PAGE_SIZE} per page
      </span>
    </div>
  )
}

export default function RoutesPage({ notify }) {
  const [routes, setRoutes] = useState([])
  const [orders, setOrders] = useState([])
  const [vehicles, setVehicles] = useState([])
  const [drivers, setDrivers] = useState([])
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')

  const [viewing, setViewing] = useState(null) 
  const [routeOrders, setRouteOrders] = useState([])
  const [picker, setPicker] = useState(null)
  const [showCreate, setShowCreate] = useState(false)
  const [dragging, setDragging] = useState(null) 
  const [overRoute, setOverRoute] = useState(null) 
  const [page, setPage] = useState(1) 
  const [smartOrder, setSmartOrder] = useState(null)
  const [autoOpen, setAutoOpen] = useState(false)

  async function loadAll() {
    try {
      setError('')
      const [routeData, orderData, vehicleData, driverData] = await Promise.all([
        getRoutes(search),
        getOrders(),
        getVehicles(),
        getDrivers(),
      ])
      setRoutes(routeData)
      setOrders(orderData)
      setVehicles(vehicleData)
      setDrivers(driverData)
      return routeData
    } catch (err) {
      setError(err.message)
      return []
    }
  }

  useEffect(() => {
    loadAll()
  }, [search])

  const totalPages = Math.max(1, Math.ceil(routes.length / PAGE_SIZE))
  useEffect(() => {
    setPage((current) => Math.min(Math.max(1, current), totalPages))
  }, [totalPages])

  const pageRoutes = routes.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  const unassigned = orders.filter(
    (order) => !order.routeId && ['READY', 'CREATED', 'DRAFT'].includes(String(order.status).toUpperCase())
  )

  const driverName = (id) => drivers.find((d) => d.driverId === id)?.name
  const truckType = (id) => vehicles.find((v) => v.truckId === id)?.type

  /**
   * A route may only take an order it can actually deliver. The check runs here first so the operator
   * gets an instant "destination did not match" toast instead of a round trip that fails - the
   * backend enforces the very same rule, so nothing can slip through if this screen is stale.
   */
  async function dropOrder(route, order) {
    const rejection = dropRejection(route, order)
    if (rejection) {
      notify(rejection, 'error')
      return
    }
    try {
      const updated = await assignOrderToRoute(route.routeId, order.orderId)
      notify(`Order ${order.orderId} assigned to route ${route.routeId}.`, 'success')
      await loadAll()
      if (viewing?.routeId === updated.routeId) {
        openDetails(updated)
      }
    } catch (err) {
      notify(err.message, 'error')
    }
  }
  async function openDetails(route) {
    setViewing(route)
    try {
      setRouteOrders(await getRouteOrders(route.routeId))
    } catch {
      setRouteOrders([])
    }
  }

  async function refreshViewing(routeId) {
    const fresh = (await getRoutes()).find((r) => r.routeId === routeId)
    if (fresh) openDetails(fresh)
  }

  async function confirmPicker(value) {
    const { kind, route } = picker
    try {
      if (kind === 'truck') {
        await assignTruck(route.routeId, value)
        notify('Truck assigned.', 'success')
      } else {
        await assignDriver(route.routeId, value)
        notify('Driver assigned.', 'success')
      }
      setPicker(null)
      await loadAll()
      if (viewing?.routeId === route.routeId) {
        await refreshViewing(route.routeId)
      }
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  async function release(kind, route) {
    try {
      if (kind === 'truck') {
        await unassignTruck(route.routeId)
        notify('Truck removed from route.', 'success')
      } else {
        await unassignDriver(route.routeId)
        notify('Driver removed from route.', 'success')
      }
      await loadAll()
      if (viewing?.routeId === route.routeId) {
        await refreshViewing(route.routeId)
      }
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  async function dispatch(route) {
    try {
      await dispatchRoute(route.routeId)
      notify(`Route ${route.routeId} dispatched.`, 'success')
      setViewing(null)
      await loadAll()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  async function removeOrder(route, order) {
    try {
      await removeOrderFromRoute(route.routeId, order.orderId)
      notify(`Order ${order.orderId} removed from route.`, 'success')
      await loadAll()
      if (viewing?.routeId === route.routeId) {
        await refreshViewing(route.routeId)
      }
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  const availableTrucks = vehicles.filter((v) => !v.routeId && v.status === 'AVAILABLE')
  const availableDrivers = drivers.filter((d) => !d.routeId && d.status === 'AVAILABLE')

  return (
    <>
      <div className="page-head">
        <div>
          <h2 className="page-title">Routes</h2>
          <p className="page-sub">
            Drag an order from the left onto a route to assign it. Capacity, ETA and readiness update
            automatically.
          </p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Create Route
        </button>
      </div>

      {error ? <div className="error-text">{error}</div> : null}

      <div className="board" style={{ marginBottom: 22 }}>
        <div className="pile">
          <div className="pile-head">
            <span>
              Unassigned Orders
              <small>
                {unassigned.length} waiting for a route - use Smart Assign, or drag one onto a route
              </small>
            </span>
            <button
              type="button"
              className="btn btn-sm btn-primary"
              onClick={() => setAutoOpen(true)}
              disabled={unassigned.length === 0}
              data-testid="assign-all"
            >
              ⚡ Assign All Orders
            </button>
          </div>
          {unassigned.length === 0 ? (
            <div className="empty">No unassigned orders.</div>
          ) : (
            unassigned.map((order) => (
              <div
                key={order.orderId}
                className={`drag-order${dragging?.orderId === order.orderId ? ' dragging' : ''}`}
                draggable
                onDragStart={() => setDragging(order)}
                onDragEnd={() => {
                  setDragging(null)
                  setOverRoute(null)
                }}
              >
                <div className="id">{order.orderId}</div>
                <div className="meta">
                  {order.weight.toLocaleString()} kg - {order.origin} &rarr; {order.destination}
                </div>
                <button
                  type="button"
                  className="btn btn-xs pile-assign"
                  onClick={(event) => {
                    event.stopPropagation()
                    setSmartOrder(order)
                  }}
                  data-testid={`smart-${order.orderId}`}
                >
                  ✨ Assign Order
                </button>
              </div>
            ))
          )}
        </div>

        <div className="route-grid">
          {pageRoutes.map((route) => {
            const editable = EDITABLE.includes(String(route.status).toUpperCase())
            // While an order is in hand, a route that cannot deliver it is shown as closed rather
            // than as a valid target - so the mismatch is visible before the drop, not after it.
            const accepts = !dragging || routeReaches(route, dragging.destination)
            return (
              <div
                key={route.routeId}
                className={`route-card drop-zone${overRoute === route.routeId ? ' over' : ''}${
                  dragging && editable ? (accepts ? ' can-accept' : ' cannot-accept') : ''
                }`}
                data-testid={`drop-${route.routeId}`}
                onDragOver={(event) => {
                  if (!editable) return
                  // Refusing to preventDefault() on a mismatching route makes the browser itself show
                  // the "not allowed" cursor.
                  if (!accepts) return
                  event.preventDefault()
                  setOverRoute(route.routeId)
                }}
                onDragLeave={() => setOverRoute((current) => (current === route.routeId ? null : current))}
                onDrop={(event) => {
                  event.preventDefault()
                  setOverRoute(null)
                  if (dragging) dropOrder(route, dragging)
                  setDragging(null)
                }}
              >
                <h3>{route.routeId}</h3>
                <div className="route-lane">
                  {route.origin} &rarr; {route.destination}
                  {route.stops?.length ? (
                    <span className="route-via">via {route.stops.join(', ')}</span>
                  ) : null}
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
                  <span>{route.capacityPercent}% Capacity</span>
                </div>
                <div className="drop-hint">
                  {!editable
                    ? `Route is ${route.status.toLowerCase()}`
                    : dragging && !accepts
                      ? `Does not reach ${dragging.destination}`
                      : 'Drop orders here'}
                </div>
              </div>
            )
          })}
          {pageRoutes.length === 0 ? <div className="empty">No routes yet. Create one.</div> : null}
        </div>
      </div>

      <div className="filters">
        <input
          placeholder="Search by Route ID..."
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(1)
          }}
        />
      </div>

      <PaginationBar page={page} totalPages={totalPages} totalRoutes={routes.length} onPage={setPage} />

      <div className="route-grid">
        {pageRoutes.map((route) => (
          <RouteCard
            key={route.routeId}
            route={route}
            truckName={truckType(route.truckId)}
            driverName={driverName(route.driverId)}
            onView={openDetails}
            onAssignTruck={(r) => setPicker({ kind: 'truck', route: r })}
            onAssignDriver={(r) => setPicker({ kind: 'driver', route: r })}
            onDispatch={dispatch}
          />
        ))}
      </div>

      <PaginationBar page={page} totalPages={totalPages} totalRoutes={routes.length} onPage={setPage} />

      {viewing ? (
        <RouteModal
          route={viewing}
          orders={routeOrders}
          vehicles={vehicles}
          drivers={drivers}
          onClose={() => setViewing(null)}
          onAssignTruck={(r) => setPicker({ kind: 'truck', route: r })}
          onAssignDriver={(r) => setPicker({ kind: 'driver', route: r })}
          onRelease={release}
          onDispatch={dispatch}
          onRemoveOrder={removeOrder}
        />
      ) : null}

      {picker ? (
        <AssignModal
          title={picker.kind === 'truck' ? 'Assign Truck' : 'Assign Driver'}
          label={picker.kind === 'truck' ? 'Available trucks' : 'Available drivers'}
          emptyText={
            picker.kind === 'truck'
              ? 'No truck is available right now.'
              : 'No driver is available right now.'
          }
          options={
            picker.kind === 'truck'
              ? availableTrucks.map((v) => ({
                  value: v.truckId,
                  label: `${v.truckId} - ${v.type} - ${v.capacity.toLocaleString()} kg`,
                }))
              : availableDrivers.map((d) => ({ value: d.driverId, label: `${d.driverId} - ${d.name}` }))
          }
          onConfirm={confirmPicker}
          onClose={() => setPicker(null)}
        />
      ) : null}

      {smartOrder ? (
        <SmartAssignModal
          order={smartOrder}
          drivers={drivers}
          vehicles={vehicles}
          notify={notify}
          onClose={() => setSmartOrder(null)}
          onAssigned={async () => {
            setSmartOrder(null)
            await loadAll()
          }}
        />
      ) : null}

      {autoOpen ? (
        <AutoAssignModal
          orders={unassigned}
          notify={notify}
          onClose={() => setAutoOpen(false)}
          onFinished={loadAll}
          onOpenOrder={(order) => {
            setAutoOpen(false)
            setSmartOrder(order)
          }}
        />
      ) : null}

      {showCreate ? (
        <CreateRouteModal
          notify={notify}
          onClose={() => setShowCreate(false)}
          onCreated={async (created) => {
            setShowCreate(false)
            const routeData = await loadAll()
            const index = routeData.findIndex((r) => r.routeId === created.routeId)
            if (index >= 0) setPage(Math.floor(index / PAGE_SIZE) + 1)
          }}
        />
      ) : null}
    </>
  )
}
