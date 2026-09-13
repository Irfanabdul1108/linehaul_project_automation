import { useEffect, useRef, useState } from 'react'
import Modal from './Modal.jsx'
import { createOrder, getRoutes } from '../services/api.js'
import { loadWarehouses, useActiveWarehouseId, warehousePlace } from '../services/warehouse.js'

const EMPTY = {
  orderId: '',
  customer: '',
  origin: '',
  destination: '',
  weight: 500,
  pieces: 1,
  serviceDate: '2026-08-26',
  status: 'READY',
}

const STATUSES = ['DRAFT', 'CREATED', 'READY', 'BLOCKED']

export default function CreateOrderModal({ onClose, onCreated, notify }) {
  const activeWarehouseId = useActiveWarehouseId()
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [places, setPlaces] = useState([])
  const originPrefilled = useRef(false)

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  // An order is picked up where it sits: the city of the warehouse that is open. The depot is named
  // by its place here, never by its "Warehouse X" designation.
  useEffect(() => {
    let alive = true
    loadWarehouses()
      .then((rows) => {
        if (!alive) return
        const place = warehousePlace(rows.find((row) => row.warehouseId === activeWarehouseId))
        if (place && !originPrefilled.current) {
          originPrefilled.current = true
          setForm((current) => (current.origin ? current : { ...current, origin: place }))
        }
        setPlaces((current) => uniquePlaces([...current, ...rows.map(warehousePlace)]))
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [activeWarehouseId])

  // Destinations the network actually serves, so the order can be sent somewhere a route can reach.
  useEffect(() => {
    let alive = true
    getRoutes()
      .then((routes) => {
        if (!alive) return
        setPlaces((current) =>
          uniquePlaces([
            ...current,
            ...routes.flatMap((route) => [route.origin, route.destination, ...(route.stops || [])]),
          ])
        )
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [])

  async function submit(event) {
    event.preventDefault()
    setError('')
    setSaving(true)
    try {
      const created = await createOrder({
        ...form,
        weight: Number(form.weight) || 0,
        pieces: Number(form.pieces) || 0,
      })
      notify(`Order ${created.orderId} created.`, 'success')
      onCreated(created)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Create Order"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" form="order-form" disabled={saving}>
            {saving ? 'Saving...' : 'Save Order'}
          </button>
        </>
      }
    >
      {error ? <div className="error-text">{error}</div> : null}
      <form id="order-form" onSubmit={submit}>
        <datalist id="order-places">
          {places.map((place) => (
            <option key={place} value={place} />
          ))}
        </datalist>
        <div className="form-grid">
          <div className="field">
            <label>Order ID</label>
            <input value={form.orderId} onChange={set('orderId')} placeholder="LH-1050" required />
          </div>
          <div className="field">
            <label>Customer</label>
            <input value={form.customer} onChange={set('customer')} placeholder="ABC Logistics" required />
          </div>
          <div className="field">
            <label>Origin</label>
            <input
              value={form.origin}
              onChange={set('origin')}
              list="order-places"
              placeholder="Bengaluru"
              required
            />
          </div>
          <div className="field">
            <label>Destination</label>
            <input
              value={form.destination}
              onChange={set('destination')}
              list="order-places"
              placeholder="Hyderabad"
              required
            />
          </div>
          <div className="field">
            <label>Weight (kg)</label>
            <input type="number" min="0" value={form.weight} onChange={set('weight')} required />
          </div>
          <div className="field">
            <label>Pieces</label>
            <input type="number" min="0" value={form.pieces} onChange={set('pieces')} required />
          </div>
          <div className="field">
            <label>Service date</label>
            <input type="date" value={form.serviceDate} onChange={set('serviceDate')} />
          </div>
          <div className="field">
            <label>Status</label>
            <select value={form.status} onChange={set('status')}>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
        </div>
      </form>
    </Modal>
  )
}

/** Unique, sorted place names for the origin/destination pickers. */
function uniquePlaces(values) {
  const seen = new Map()
  for (const value of values) {
    const place = (value || '').trim()
    if (!place) continue
    const key = place.toLowerCase()
    if (!seen.has(key)) {
      seen.set(key, place)
    }
  }
  return [...seen.values()].sort((a, b) => a.localeCompare(b))
}
