import { useSyncExternalStore } from 'react'

const STORAGE_KEY = 'linehaul.warehouse'
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

let listeners = new Set()
let cache = null

function read() {
  try {
    return globalThis.localStorage?.getItem(STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

let active = read()

function emit() {
  listeners.forEach((listener) => listener())
}

export function getActiveWarehouseId() {
  return active
}

export function setActiveWarehouse(warehouseId) {
  active = warehouseId || ''
  try {
    if (active) {
      globalThis.localStorage?.setItem(STORAGE_KEY, active)
    } else {
      globalThis.localStorage?.removeItem(STORAGE_KEY)
    }
  } catch {
    /* private mode: the selection simply lives for this tab only */
  }
  emit()
}

export function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Reactive hook: the current warehouse id, or "" when none was picked yet. */
export function useActiveWarehouseId() {
  return useSyncExternalStore(subscribe, getActiveWarehouseId, () => '')
}

/** Appends the warehouse scope to an /api path. Empty paths and non-API paths are left alone. */
export function scopedUrl(path, warehouseId = getActiveWarehouseId()) {
  const url = `${API_BASE_URL}${path}`
  if (!warehouseId || !path.startsWith('/api/')) {
    return url
  }
  return `${url}${path.includes('?') ? '&' : '?'}warehouseId=${encodeURIComponent(warehouseId)}`
}

/** The list of depots, fetched once and then served from the module cache. */
export async function loadWarehouses({ force = false } = {}) {
  if (cache && !force) {
    return cache
  }
  const response = await fetch(`${API_BASE_URL}/api/warehouses`, {
    headers: { 'Content-Type': 'application/json' },
  })
  if (!response.ok) {
    throw new Error('The warehouse list could not be loaded. Is the Linehaul API running?')
  }
  const rows = await response.json()
  cache = Array.isArray(rows) ? rows : []
  return cache
}

export function forgetWarehouses() {
  cache = null
}

/**
 * Counters for one depot, for the start screen. This one deliberately does NOT go through
 * scopedUrl(): the picker has to ask about a warehouse the operator has not selected yet.
 */
export async function loadWarehouseSummary(warehouseId) {
  const response = await fetch(
    `${API_BASE_URL}/api/dashboard/summary?warehouseId=${encodeURIComponent(warehouseId)}`,
    { headers: { 'Content-Type': 'application/json' } }
  )
  if (!response.ok) {
    throw new Error('The warehouse summary could not be loaded.')
  }
  return response.json()
}

/**
 * The name of a depot in the WAREHOUSE SWITCHER, where the designation is what tells depots apart:
 * "Warehouse A - Bengaluru". Everywhere else use placeOf()/warehousePlace() instead - a lane, a route
 * and an order name the city the truck drives to, never "Warehouse A".
 */
export function warehouseLabel(warehouse) {
  if (!warehouse) {
    return ''
  }
  const place = warehousePlace(warehouse)
  if (!warehouse.name || warehouse.name === place) {
    return place
  }
  return `${warehouse.name}${place ? ` - ${place}` : ''}`
}

/** The place a depot IS: its city, falling back to the hub location and then the designation. */
export function warehousePlace(warehouse) {
  if (!warehouse) {
    return ''
  }
  return warehouse.city || warehouse.hubLocation || warehouse.name || warehouse.warehouseId || ''
}

/** The designation used only by the switcher, e.g. "Warehouse A". */
export function warehouseDesignation(warehouse) {
  if (!warehouse) {
    return ''
  }
  return warehouse.name || warehouse.code || warehouse.warehouseId || ''
}

const NOISE = new Set(['warehouse', 'hub', 'dc', 'depot', 'terminal', 'yard', 'gate', 'bay', 'the', 'of', 'and'])

/**
 * Comparable form of a place name - the browser-side twin of the backend's LocationMatcher.token(),
 * so the drag-and-drop board refuses exactly the routes the API would refuse.
 */
export function placeToken(raw) {
  if (!raw) {
    return ''
  }
  const cleaned = String(raw)
    .toLowerCase()
    .replace(/[^a-z0-9 ]/g, ' ')
    .trim()
    .replace(/\s+/g, ' ')
  if (!cleaned) {
    return ''
  }
  const kept = cleaned.split(' ').filter((word) => !NOISE.has(word))
  return kept.length ? kept.join('') : cleaned.replace(/ /g, '')
}

/** True when two free-text place names mean the same place. */
export function samePlace(left, right) {
  const a = placeToken(left)
  const b = placeToken(right)
  return Boolean(a) && a === b
}
