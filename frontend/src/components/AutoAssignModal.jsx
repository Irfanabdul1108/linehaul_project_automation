import { useEffect, useRef, useState } from 'react'
import Modal from './Modal.jsx'
import { autoAssignOrder } from '../services/automation.js'

const ICONS = {
  RUNNING: '⏳',
  ASSIGNED: '✅',
  NEW_ROUTE_REQUIRED: '➕',
  NEEDS_REVIEW: '⚠',
  SKIPPED: '⏭',
  FAILED: '⚠',
}

const TITLES = {
  ASSIGNED: 'Assigned',
  NEW_ROUTE_REQUIRED: 'New Routes Required',
  NEEDS_REVIEW: 'Needs Review',
  SKIPPED: 'Skipped',
  FAILED: 'Failed',
}

const GROUPS = ['ASSIGNED', 'NEW_ROUTE_REQUIRED', 'NEEDS_REVIEW', 'SKIPPED']

/**
 * The "Assign All Orders" run. Each unassigned order of the warehouse goes through the same engine the
 * single-order button uses - one request per order, so the list below grows while the planner works,
 * and the summary at the top is only ever as optimistic as the results.
 */
export default function AutoAssignModal({ orders, warehouseName, onClose, onFinished, onOpenOrder, notify }) {
  const [rows, setRows] = useState(orders.map((order) => ({ order, state: 'QUEUED' })))
  const rowsRef = useRef(rows)
  const [running, setRunning] = useState(true)
  const [done, setDone] = useState(false)
  const started = useRef(false)

  // React.StrictMode mounts, unmounts and remounts effects in development. The run is therefore
  // guarded by a ref instead of being cancelled on unmount: a cancellation here would abort the batch
  // after the first order, which is exactly what a developer would see and nobody else would.
  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true

    async function run() {
      for (let index = 0; index < orders.length; index++) {
        const order = orders[index]
        setRows((current) => {
          const next = current.map((row, i) => (i === index ? { ...row, state: 'RUNNING' } : row))
          rowsRef.current = next
          return next
        })
        try {
          const outcome = await autoAssignOrder(order.orderId)
          setRows((current) => {
            const next = current.map((row, i) =>
              i === index ? { ...row, state: outcome.action || 'NEEDS_REVIEW', outcome } : row
            )
            rowsRef.current = next
            return next
          })
        } catch (problem) {
          setRows((current) => {
            const next = current.map((row, i) =>
              i === index
                ? { ...row, state: 'FAILED', outcome: { orderId: order.orderId, reason: problem.message } }
                : row
            )
            rowsRef.current = next
            return next
          })
        }
      }
      setRunning(false)
      setDone(true)
      const tallyOf = (key) => rowsRef.current.filter((row) => row.state === key).length
      notify?.(
        `Auto assignment done: ${tallyOf('ASSIGNED')} assigned, ${tallyOf('NEW_ROUTE_REQUIRED')} need a new ` +
          `route, ${tallyOf('NEEDS_REVIEW') + tallyOf('FAILED')} need a dispatcher.`,
        tallyOf('ASSIGNED') > 0 ? 'success' : 'error'
      )
      onFinished?.()
    }

    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const tally = GROUPS.reduce((acc, key) => {
    acc[key] = rows.filter((row) => row.state === key).length
    return acc
  }, {})
  const processed = rows.filter((row) => row.state !== 'QUEUED' && row.state !== 'RUNNING').length

  const footer = (
    <div className="assign-foot">
      <span className="muted small-text">
        {running ? `Working through ${orders.length} unassigned orders...` : `${processed} of ${orders.length} orders processed`}
      </span>
      <div className="assign-foot-right">
        <button type="button" className="btn" onClick={onClose}>
          {running ? 'Stop and close' : 'Close'}
        </button>
      </div>
    </div>
  )

  return (
    <Modal wide title={`Auto assignment - ${warehouseName || 'current warehouse'}`} onClose={onClose} footer={footer}>
      {orders.length === 0 ? (
        <div className="empty">Nothing to do: every order in this warehouse already has a route.</div>
      ) : (
        <>
          <div className="auto-summary">
            {GROUPS.map((key) => (
              <div key={key} className={`auto-tally auto-tally-${key.toLowerCase()}`}>
                <span className="auto-tally-value">{tally[key] || 0}</span>
                <span className="auto-tally-label">{TITLES[key]}</span>
              </div>
            ))}
          </div>

          {done && (tally.NEW_ROUTE_REQUIRED || 0) + (tally.NEEDS_REVIEW || 0) === 0 ? (
            <div className="auto-note ok">
              Every unassigned order in this warehouse found a route. Open a route to check the load and
              dispatch it when it is ready.
            </div>
          ) : null}
          {done && (tally.NEW_ROUTE_REQUIRED || 0) + (tally.NEEDS_REVIEW || 0) > 0 ? (
            <div className="auto-note warn">
              {tally.NEW_ROUTE_REQUIRED || 0} order(s) need a new route and {tally.NEEDS_REVIEW || 0} need a
              person. Nothing was forced onto a route it does not fit.
            </div>
          ) : null}

          <ul className="auto-list" data-testid="auto-list">
            {rows.map((row) => (
              <li key={row.order.orderId} className={`auto-row auto-row-${String(row.state).toLowerCase()}`}>
                <span className="auto-icon" aria-hidden>
                  {row.state === 'QUEUED' ? '·' : ICONS[row.state] || '·'}
                </span>
                <span className="auto-id">{row.order.orderId}</span>
                <span className="auto-lane">
                  {row.order.origin} → {row.order.destination}
                </span>
                <span className="auto-weight">{row.order.weight.toLocaleString()} kg</span>
                <span className={`auto-state state-${String(row.state).toLowerCase()}`}>
                  {row.state === 'QUEUED'
                    ? 'Queued'
                    : row.state === 'RUNNING'
                      ? 'Analysing...'
                      : TITLES[row.state] || row.state}
                </span>
                <span className="auto-detail">
                  {row.outcome?.routeId ? `route ${row.outcome.routeId} · ` : ''}
                  {row.outcome?.reason || row.outcome?.message || ''}
                </span>
                {row.outcome && row.state !== 'ASSIGNED' && onOpenOrder ? (
                  <button type="button" className="btn btn-sm" onClick={() => onOpenOrder(row.order)}>
                    Handle this order
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        </>
      )}
    </Modal>
  )
}
