import { useEffect, useMemo, useState } from 'react'
import Modal from './Modal.jsx'
import RecommendationCard from './RecommendationCard.jsx'
import { assignOrder, createRouteAndAssign, recommendRoutes } from '../services/automation.js'

const AI_COPY = {
  gemini: 'Ranking and reasons come from Google Gemini, on top of routes the backend already validated.',
  'single-candidate': 'Only one route qualifies, so there was nothing to rank - the AI was not consulted.',
  disabled: 'No AI key is configured: the list below is produced by the deterministic rules alone.',
  unavailable: 'AI recommendation is temporarily unavailable. The deterministic engine kept working.',
  'not-requested': 'The AI step was skipped for this call.',
}

function Step({ step }) {
  const tone =
    step.status === 'PASSED' ? 'ok' : step.status === 'FAILED' ? 'bad' : step.status === 'WARNING' ? 'warn' : 'info'
  const icon = tone === 'ok' ? '✓' : tone === 'bad' ? '✕' : tone === 'warn' ? '!' : '·'
  return (
    <li className={`pipe-step pipe-${tone}`}>
      <span className="pipe-icon" aria-hidden>
        {icon}
      </span>
      <span className="pipe-label">{step.label}</span>
      <span className="pipe-detail">{step.detail}</span>
    </li>
  )
}

function Picker({ label, value, onChange, options, emptyText }) {
  return (
    <div className="field">
      <label>{label}</label>
      {options.length === 0 ? (
        <div className="muted small-text">{emptyText}</div>
      ) : (
        <select value={value} onChange={(event) => onChange(event.target.value)}>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </div>
  )
}

/**
 * The panel behind the "Assign Order" button. It never assigns anything by itself: the operator reads
 * the top three validated routes (or the new-route proposal) and confirms the choice.
 */
export default function SmartAssignModal({ order, drivers = [], vehicles = [], onClose, onAssigned, notify }) {
  const [advice, setAdvice] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [driverId, setDriverId] = useState('')
  const [truckId, setTruckId] = useState('')
  const [showDetails, setShowDetails] = useState(false)
  const [busy, setBusy] = useState(false)
  const [newRoute, setNewRoute] = useState({ departureTime: '20:00', travelDuration: 9, maxCapacity: 10000 })

  async function analyse(useAi) {
    setLoading(true)
    setError('')
    try {
      const data = await recommendRoutes(order.orderId, { useAi })
      setAdvice(data)
      const best = data?.candidates?.[0]
      setSelectedId(best ? best.routeId : '')
      if (data?.newRoute) {
        setNewRoute({
          departureTime: data.newRoute.suggestedDepartureTime || '20:00',
          travelDuration: data.newRoute.suggestedTravelDuration ?? 9,
          maxCapacity: data.newRoute.suggestedMaxCapacity ?? Math.max(order.weight, 1000),
        })
        setDriverId(data.newRoute.drivers?.[0]?.id || '')
        setTruckId(data.newRoute.vehicles?.[0]?.id || '')
      }
    } catch (problem) {
      setError(problem.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    analyse(true)
  }, [order.orderId])

  const candidates = advice?.candidates || []
  const selected = useMemo(
    () => candidates.find((candidate) => candidate.routeId === selectedId) || null,
    [candidates, selectedId]
  )

  const freeDrivers = useMemo(
    () => drivers.filter((driver) => !driver.routeId && driver.status === 'AVAILABLE'),
    [drivers]
  )
  const freeTrucks = useMemo(
    () =>
      vehicles.filter(
        (vehicle) => !vehicle.routeId && vehicle.status === 'AVAILABLE' && vehicle.capacity >= order.weight
      ),
    [vehicles, order.weight]
  )

  const needsCrew = selected ? selected.needsDriver || selected.needsTruck : false
  const canPickCrew = selected && !selected.crossWarehouse && needsCrew

  async function confirmAssign(candidate) {
    setBusy(true)
    setError('')
    try {
      const result = await assignOrder(order.orderId, {
        routeId: candidate.routeId,
        driverId: canPickCrew && candidate.needsDriver ? driverId : undefined,
        truckId: canPickCrew && candidate.needsTruck ? truckId : undefined,
      })
      notify(result.message, 'success')
      onAssigned(result)
    } catch (problem) {
      const message = problem.message
      await analyse(false)
      setError(message)
    } finally {
      setBusy(false)
    }
  }

  async function confirmNewRoute() {
    setBusy(true)
    setError('')
    try {
      const result = await createRouteAndAssign({
        orderId: order.orderId,
        driverId,
        truckId,
        departureTime: newRoute.departureTime,
        travelDuration: Number(newRoute.travelDuration) || 9,
        maxCapacity: Number(newRoute.maxCapacity) || order.weight,
      })
      notify(result.message, 'success')
      onAssigned(result)
    } catch (problem) {
      setError(problem.message)
    } finally {
      setBusy(false)
    }
  }

  const plan = advice?.newRoute
  const footer = (
    <div className="assign-foot">
      <button type="button" className="btn" onClick={() => setShowDetails((current) => !current)}>
        {showDetails ? 'Hide details' : 'View all details'}
      </button>
      <div className="assign-foot-right">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>
          Close
        </button>
        <button type="button" className="btn btn-primary" onClick={() => analyse(true)} disabled={loading || busy}>
          {loading ? 'Analysing...' : 'Re-run analysis'}
        </button>
      </div>
    </div>
  )

  return (
    <Modal
      wide
      title={`Smart assignment - order ${order.orderId}`}
      onClose={onClose}
      footer={footer}
    >
      <div className="assign-order-strip">
        <div>
          <span className="muted small-text">Order</span>
          <strong>{order.orderId}</strong>
        </div>
        <div>
          <span className="muted small-text">Lane</span>
          <strong>
            {order.origin} → {order.destination}
          </strong>
        </div>
        <div>
          <span className="muted small-text">Required capacity</span>
          <strong>{order.weight.toLocaleString()} kg</strong>
        </div>
        <div>
          <span className="muted small-text">Pieces / service date</span>
          <strong>
            {order.pieces} / {order.serviceDate || '-'}
          </strong>
        </div>
        <div>
          <span className="muted small-text">Warehouse</span>
          <strong>{advice?.warehouseName || order.warehouseId || '-'}</strong>
        </div>
      </div>

      {advice && advice.alreadyAssigned ? (
        <div className="assign-note">{advice.message}</div>
      ) : null}

      {advice && advice.aiStatus !== 'disabled' ? (
        <div className={`assign-ai${advice.aiUsed ? ' live' : ' muted-note'}`}>
          <strong>{advice.aiUsed ? 'AI assisted' : 'Deterministic'}</strong>
          <span>{AI_COPY[advice.aiStatus] || advice.message}</span>
        </div>
      ) : null}

      {error ? <div className="error-text">{error}</div> : null}
      {loading ? <div className="empty">Analysing every route in the network...</div> : null}

      {!loading && advice ? (
        <>
          <div className="assign-summary">
            <div>
              <strong>{advice.headline}</strong>
              <p>{advice.message}</p>
            </div>
            <span className="assign-count">
              {advice.eligibleCount} of {advice.routesConsidered} routes in all warehouses fit
            </span>
          </div>

          <ul className="pipeline">
            {(advice.steps || []).map((step) => (
              <Step key={step.label} step={step} />
            ))}
          </ul>

          {candidates.length > 0 ? (
            <section className="rec-list">
              <h3 className="section-title">
                Best routes for this order
                <span className="muted small-text">
                  {candidates.length === advice.eligibleCount
                    ? `showing all ${candidates.length}`
                    : `showing the top ${candidates.length} of ${advice.eligibleCount} eligible routes`}
                </span>
              </h3>

              {candidates.map((candidate) => (
                <RecommendationCard
                  key={candidate.routeId}
                  candidate={candidate}
                  recommended={candidate.rank === 1}
                  selected={candidate.routeId === selectedId}
                  warehouseName={advice.warehouseName}
                  showDetails={showDetails}
                  busy={busy}
                  onSelect={(item) => {
                    setSelectedId(item.routeId)
                    setError('')
                  }}
                  onAssign={confirmAssign}
                />
              ))}

              {selected && !selected.crossWarehouse ? (
                <div className="assign-selects">
                  {canPickCrew ? (
                    <>
                      <Picker
                        label={`Driver (only free drivers of ${advice.warehouseName || 'this warehouse'})`}
                        value={driverId}
                        onChange={setDriverId}
                        emptyText="No free driver in this warehouse."
                        options={freeDrivers.map((driver) => ({
                          value: driver.driverId,
                          label: `${driver.driverId} - ${driver.name}`,
                        }))}
                      />
                      <Picker
                        label={`Vehicle (only free trucks of ${advice.warehouseName || 'this warehouse'})`}
                        value={truckId}
                        onChange={setTruckId}
                        emptyText="No free truck in this warehouse can carry this load."
                        options={freeTrucks.map((vehicle) => ({
                          value: vehicle.truckId,
                          label: `${vehicle.truckId} - ${vehicle.type} - ${vehicle.capacity.toLocaleString()} kg`,
                        }))}
                      />
                    </>
                  ) : (
                    <p className="muted small-text">
                      This route is complete: the order is added to the existing load and nothing else has
                      to be picked.
                    </p>
                  )}
                  <button
                    type="button"
                    className="btn btn-green"
                    onClick={() => confirmAssign(selected)}
                    disabled={busy || (canPickCrew && selected.needsDriver && !driverId)}
                    data-testid="confirm-assign"
                  >
                    {busy ? 'Assigning...' : 'Assign order to this route'}
                  </button>
                </div>
              ) : null}

              {selected && selected.crossWarehouse ? (
                <div className="cross-box">
                  <h4>Existing Cross-Warehouse Route</h4>
                  <p>
                    Route <strong>{selected.routeId}</strong> originates from{' '}
                    <strong>{selected.warehouseName || selected.warehouseId}</strong> but has a valid stop
                    at <strong>{selected.pickupLabel}</strong>, which is your current warehouse.
                  </p>
                  <p>
                    Its existing driver ({selected.driverId}) and vehicle ({selected.truckId || 'none'}) are
                    already assigned, so nothing about its crew changes.
                  </p>
                  <p className="cross-note">
                    The order will be loaded at{' '}
                    <strong>
                      {selected.warehouseName || selected.warehouseId} ({selected.pickupLabel})
                    </strong>{' '}
                    when the route reaches this stop.
                  </p>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => confirmAssign(selected)}
                    disabled={busy}
                    data-testid="confirm-cross-assign"
                  >
                    {busy ? 'Adding...' : 'ADD ORDER TO THIS ROUTE'}
                  </button>
                </div>
              ) : null}
            </section>
          ) : null}

          {advice.newRouteRequired && plan ? (
            <section className="newroute-box">
              <h3 className="section-title">
                No suitable existing route
                <span className="badge badge-yellow">New Route Required</span>
              </h3>
              <p>{plan.message}</p>

              <div className="newroute-lane">
                <div>
                  <span className="muted small-text">Origin</span>
                  <strong>{plan.origin}</strong>
                  <span className="muted small-text"> (your warehouse)</span>
                </div>
                <div>
                  <span className="muted small-text">Destination</span>
                  <strong>{plan.destination}</strong>
                  <span className="muted small-text"> (from the order)</span>
                </div>
                <div>
                  <span className="muted small-text">Suggested route id</span>
                  <strong>{plan.suggestedRouteId}</strong>
                </div>
              </div>

              {plan.canCreate ? (
                <>
                  <div className="form-grid">
                    <Picker
                      label="Driver from this warehouse"
                      value={driverId}
                      onChange={setDriverId}
                      options={(plan.drivers || []).map((driver) => ({
                        value: driver.id,
                        label: `${driver.label} - ${driver.note}`,
                      }))}
                      emptyText="No suitable driver/vehicle is currently available in this warehouse."
                    />
                    <Picker
                      label="Vehicle from this warehouse"
                      value={truckId}
                      onChange={setTruckId}
                      options={(plan.vehicles || []).map((vehicle) => ({
                        value: vehicle.id,
                        label: `${vehicle.label} - ${vehicle.note}`,
                      }))}
                      emptyText="No suitable driver/vehicle is currently available in this warehouse."
                    />
                    <div className="field">
                      <label>Departure time</label>
                      <input
                        type="time"
                        value={newRoute.departureTime}
                        onChange={(event) => setNewRoute({ ...newRoute, departureTime: event.target.value })}
                      />
                    </div>
                    <div className="field">
                      <label>Travel duration (hours)</label>
                      <input
                        type="number"
                        min="1"
                        value={newRoute.travelDuration}
                        onChange={(event) =>
                          setNewRoute({ ...newRoute, travelDuration: event.target.value })
                        }
                      />
                    </div>
                    <div className="field">
                      <label>Maximum capacity (kg)</label>
                      <input
                        type="number"
                        min={order.weight}
                        value={newRoute.maxCapacity}
                        onChange={(event) => setNewRoute({ ...newRoute, maxCapacity: event.target.value })}
                      />
                    </div>
                  </div>

                  <button
                    type="button"
                    className="btn btn-green"
                    onClick={confirmNewRoute}
                    disabled={busy || !driverId || !truckId}
                    data-testid="create-and-assign"
                  >
                    {busy ? 'Creating...' : 'CREATE & ASSIGN'}
                  </button>
                  <p className="muted small-text">
                    The route is only created after you confirm it here - the planner never creates routes
                    silently.
                  </p>
                </>
              ) : (
                <div className="assign-note blocked">
                  Nothing was created. Solve the shortage above (free a driver or a truck in{' '}
                  {advice.warehouseName || 'this warehouse'}) and run the analysis again.
                </div>
              )}
            </section>
          ) : null}

          {showDetails ? (
            <section className="assign-details">
              <h3 className="section-title">Routes that were not offered</h3>
              {(advice.rejected || []).length === 0 ? (
                <p className="muted small-text">Nothing was rejected - every route in the network fitted.</p>
              ) : (
                <table className="table">
                  <thead>
                    <tr>
                      <th>Route</th>
                      <th>Warehouse</th>
                      <th>Lane</th>
                      <th>Check that failed</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(advice.rejected || []).map((rejection) => (
                      <tr key={`${rejection.routeId}-${rejection.stage}`}>
                        <td>{rejection.routeId}</td>
                        <td>{rejection.warehouseId || '-'}</td>
                        <td>
                          {rejection.origin} → {rejection.destination}
                        </td>
                        <td>
                          <span className="badge badge-red">{rejection.stage.replace(/_/g, ' ')}</span>
                        </td>
                        <td className="muted">{rejection.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          ) : null}
        </>
      ) : null}
    </Modal>
  )
}
