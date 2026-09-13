import { useEffect, useState } from 'react'
import {
  loadWarehouses,
  loadWarehouseSummary,
  setActiveWarehouse,
  warehouseDesignation,
  warehousePlace,
} from '../services/warehouse.js'

function Count({ label, value, tone }) {
  return (
    <div className={`wh-count${tone ? ` ${tone}` : ''}`}>
      <span className="wh-count-value">{value === null || value === undefined ? '-' : value}</span>
      <span className="wh-count-label">{label}</span>
    </div>
  )
}

/**
 * The entry screen, and the ONE place where the "Warehouse A" designation is the headline.
 *
 * Here it is what tells the four cards apart, so it is shown large with the city underneath. From the
 * moment a depot is opened, the rest of the application names it by its place ("Bengaluru") instead,
 * because that is the token its orders, routes and lanes are written with.
 */
export default function SelectWarehouse({ onSelect, notify }) {
  const [warehouses, setWarehouses] = useState([])
  const [overviews, setOverviews] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true

    loadWarehouses({ force: true })
      .then(async (rows) => {
        if (!alive) return
        setWarehouses(rows)
        setLoading(false)
        const settled = await Promise.allSettled(rows.map((row) => loadWarehouseSummary(row.warehouseId)))
        if (!alive) return
        const next = {}
        settled.forEach((result, index) => {
          next[rows[index].warehouseId] = result.status === 'fulfilled' ? result.value : null
        })
        setOverviews(next)
      })
      .catch((problem) => {
        if (!alive) return
        setError(problem.message)
        setLoading(false)
      })

    return () => {
      alive = false
    }
  }, [])

  function choose(warehouse) {
    setActiveWarehouse(warehouse.warehouseId)
    notify?.(
      `Opened ${warehousePlace(warehouse)}${
        warehouseDesignation(warehouse) ? ` (${warehouseDesignation(warehouse)})` : ''
      }.`,
      'success'
    )
    onSelect?.(warehouse)
  }

  return (
    <div className="warehouse-gate">
      <div className="warehouse-gate-inner">
        <div className="warehouse-gate-head">
          <span className="warehouse-gate-eyebrow">Linehaul Management System</span>
          <h1>Choose a warehouse</h1>
          <p>
            You will work inside one depot at a time. Its orders, routes, trucks and drivers are shown
            on every screen - named after the city it sits in - and when an order has no route, the
            planner looks across all warehouses for the best fit.
          </p>
        </div>

        {error ? <div className="error-text warehouse-gate-error">{error}</div> : null}
        {loading ? <div className="empty">Loading warehouses from the API...</div> : null}

        <div className="warehouse-grid">
          {warehouses.map((warehouse) => {
            const overview = overviews[warehouse.warehouseId]
            const waiting = overview ? overview.unassignedOrders : null
            return (
              <button
                type="button"
                key={warehouse.warehouseId}
                className="warehouse-card"
                data-testid={`warehouse-${warehouse.warehouseId}`}
                onClick={() => choose(warehouse)}
              >
                <div className="warehouse-card-head">
                  <div>
                    {/* the designation identifies the card; the place is what everything else uses */}
                    <h2>{warehouseDesignation(warehouse)}</h2>
                    <span className="warehouse-card-place">
                      {warehousePlace(warehouse)}
                      {warehouse.state ? `, ${warehouse.state}` : ''}
                    </span>
                  </div>
                  <span className="warehouse-card-code">{warehouse.code || warehouse.warehouseId}</span>
                </div>

                {warehouse.description ? (
                  <p className="warehouse-card-note">{warehouse.description}</p>
                ) : null}

                <div className="warehouse-counts">
                  <Count label="Unassigned" value={waiting} tone={waiting > 0 ? 'warn' : 'ok'} />
                  <Count label="Orders" value={overview?.totalOrders} />
                  <Count label="Routes" value={overview?.totalRoutes} />
                  <Count label="Free trucks" value={overview?.availableVehicles} />
                  <Count label="Free drivers" value={overview?.availableDrivers} />
                </div>

                <span className="btn btn-primary warehouse-card-cta">
                  Open {warehousePlace(warehouse)} &rarr;
                </span>
              </button>
            )
          })}
        </div>

        {warehouses.length === 0 && !loading && !error ? (
          <div className="empty">
            No warehouses are set up yet. Start the API and let it seed the demo network, or add
            warehouses in MongoDB.
          </div>
        ) : null}
      </div>
    </div>
  )
}
