import { useEffect, useMemo, useRef, useState } from 'react'
import Modal from './Modal.jsx'
import { createRoute, getLaneDurations, getRoutes, lookupLaneDuration } from '../services/api.js'
import {
  loadWarehouses,
  useActiveWarehouseId,
  warehousePlace,
  samePlace,
} from '../services/warehouse.js'

const EMPTY = {
  routeId: '',
  origin: '',
  destination: '',
  departureTime: '20:00',
  travelDuration: '',
  maxCapacity: 10000,
}

/**
 * Creating a route asks for as little as possible.
 *
 * <p>The origin is pre-filled with the city of the warehouse that is open, because that is where the
 * route starts. Once an origin and a destination are chosen, the travel duration is filled in from
 * the lane memory - the network already knows how long, say, Bengaluru to Hyderabad takes, so nobody
 * has to look it up. The field stays editable: an unusual run can always be typed by hand, and what
 * is typed is what the network learns for next time.</p>
 */
export default function CreateRouteModal({ onClose, onCreated, notify }) {
  const activeWarehouseId = useActiveWarehouseId()
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [places, setPlaces] = useState([])
  const [lane, setLane] = useState({ state: 'idle' })
  // set once, so re-opening the modal does not fight with what the dispatcher typed
  const originPrefilled = useRef(false)
  // a duration the dispatcher typed themselves is never overwritten by the lane memory
  const durationTouched = useRef(false)

  const set = (key) => (event) => {
    if (key === 'travelDuration') {
      durationTouched.current = true
    }
    setForm((current) => ({ ...current, [key]: event.target.value }))
  }

  // Pre-fill the origin with the city of the depot that is open.
  useEffect(() => {
    let alive = true
    loadWarehouses()
      .then((rows) => {
        if (!alive) return
        const current = rows.find((row) => row.warehouseId === activeWarehouseId)
        const place = warehousePlace(current)
        if (place && !originPrefilled.current) {
          originPrefilled.current = true
          setForm((existing) => (existing.origin ? existing : { ...existing, origin: place }))
        }
        setPlaces((existing) => mergePlaces(existing, rows.map(warehousePlace)))
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [activeWarehouseId])

  // Every place the network already serves, so the destination can be picked instead of typed.
  useEffect(() => {
    let alive = true
    Promise.allSettled([getRoutes(), getLaneDurations()]).then(([routeResult, laneResult]) => {
      if (!alive) return
      const fromRoutes =
        routeResult.status === 'fulfilled'
          ? routeResult.value.flatMap((route) => [route.origin, route.destination, ...(route.stops || [])])
          : []
      const fromLanes =
        laneResult.status === 'fulfilled'
          ? laneResult.value.flatMap((row) => [row.origin, row.destination])
          : []
      setPlaces((existing) => mergePlaces(existing, [...fromRoutes, ...fromLanes]))
    })
    return () => {
      alive = false
    }
  }, [])

  // The lane memory answers as soon as both ends are known.
  useEffect(() => {
    const origin = form.origin.trim()
    const destination = form.destination.trim()
    if (!origin || !destination || samePlace(origin, destination)) {
      setLane({ state: 'idle' })
      return undefined
    }

    let alive = true
    setLane({ state: 'loading' })
    const timer = setTimeout(() => {
      lookupLaneDuration(origin, destination)
        .then((answer) => {
          if (!alive) return
          if (answer?.known && answer.travelDuration > 0) {
            setLane({ state: 'known', ...answer })
            if (!durationTouched.current) {
              setForm((current) => ({ ...current, travelDuration: String(answer.travelDuration) }))
            }
          } else {
            setLane({ state: 'unknown' })
          }
        })
        .catch(() => {
          if (alive) setLane({ state: 'unknown' })
        })
    }, 250)

    return () => {
      alive = false
      clearTimeout(timer)
    }
  }, [form.origin, form.destination])

  const sameEnds = useMemo(
    () => Boolean(form.origin.trim() && samePlace(form.origin, form.destination)),
    [form.origin, form.destination]
  )

  async function submit(event) {
    event.preventDefault()
    setError('')
    if (sameEnds) {
      setError('The origin and the destination are the same place.')
      return
    }
    setSaving(true)
    try {
      const created = await createRoute({
        ...form,
        origin: form.origin.trim(),
        destination: form.destination.trim(),
        travelDuration: Number(form.travelDuration) || 0,
        maxCapacity: Number(form.maxCapacity) || 0,
      })
      notify(`Route ${created.routeId} created.`, 'success')
      onCreated(created)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Create Route"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" form="route-form" disabled={saving}>
            {saving ? 'Saving...' : 'Save Route'}
          </button>
        </>
      }
    >
      {error ? <div className="error-text">{error}</div> : null}
      <form id="route-form" onSubmit={submit}>
        <datalist id="known-places">
          {places.map((place) => (
            <option key={place} value={place} />
          ))}
        </datalist>
        <div className="form-grid">
          <div className="field">
            <label>Route ID</label>
            <input value={form.routeId} onChange={set('routeId')} placeholder="LH-1040" required />
          </div>
          <div className="field">
            <label>Origin</label>
            <input
              value={form.origin}
              onChange={set('origin')}
              list="known-places"
              placeholder="Bengaluru"
              required
            />
          </div>
          <div className="field">
            <label>Destination</label>
            <input
              value={form.destination}
              onChange={set('destination')}
              list="known-places"
              placeholder="Hyderabad"
              required
            />
          </div>
          <div className="field">
            <label>Departure time</label>
            <input type="time" value={form.departureTime} onChange={set('departureTime')} required />
          </div>
          <div className="field">
            <label>Travel duration (hours)</label>
            <input
              type="number"
              min="0"
              value={form.travelDuration}
              onChange={set('travelDuration')}
              data-testid="travel-duration"
              required
            />
            <small className="lane-note" data-testid="lane-note">
              {sameEnds
                ? 'Pick two different places.'
                : lane.state === 'loading'
                  ? 'Checking how long this lane takes...'
                  : lane.state === 'known'
                    ? `Filled in automatically: ${lane.origin} to ${lane.destination} takes ${lane.travelDuration} h. Change it if this run is different.`
                    : lane.state === 'unknown'
                      ? 'This lane is new to the network - enter the hours once and it will be remembered.'
                      : 'Pick an origin and a destination and the known duration appears here.'}
            </small>
          </div>
          <div className="field">
            <label>Maximum capacity (kg)</label>
            <input
              type="number"
              min="1"
              value={form.maxCapacity}
              onChange={set('maxCapacity')}
              required
            />
          </div>
        </div>
      </form>
    </Modal>
  )
}

/** Unique, sorted place names - the same place written two ways only appears once. */
function mergePlaces(existing, incoming) {
  const seen = new Map()
  for (const place of [...existing, ...incoming]) {
    const value = (place || '').trim()
    if (!value) continue
    const key = value.toLowerCase()
    if (!seen.has(key)) {
      seen.set(key, value)
    }
  }
  return [...seen.values()].sort((a, b) => a.localeCompare(b))
}
