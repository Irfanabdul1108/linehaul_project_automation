import { samePlace } from './warehouse.js'

/**
 * The lane of a route, and the one question the drag-and-drop board has to answer before it lets go
 * of an order: can this route actually deliver it?
 *
 * The rule is the same one the backend applies in RouteService.assignOrder() and
 * RouteMatchingService: a route qualifies when the order's destination is its final stop or one of
 * its intermediate stops. Checking it here as well is purely for the operator's benefit - the drop
 * zone can refuse the order instantly and say why, instead of firing a request that comes back with
 * an error. The backend stays the authority.
 */

/** Origin, intermediate stops and destination of a route, in travel order. */
export function stopSequence(route) {
  if (!route) {
    return []
  }
  if (Array.isArray(route.stopSequence) && route.stopSequence.length > 0) {
    return route.stopSequence.filter(Boolean)
  }
  return [route.origin, ...(Array.isArray(route.stops) ? route.stops : []), route.destination].filter(
    (stop) => stop && String(stop).trim()
  )
}

/** Where along the route the order leaves it, or -1 when the route never gets there. */
export function unloadIndex(route, destination) {
  const sequence = stopSequence(route)
  if (sequence.length === 0 || !destination) {
    return -1
  }
  const last = sequence.length - 1
  if (samePlace(sequence[last], destination)) {
    return last
  }
  for (let i = 1; i < last; i++) {
    if (samePlace(sequence[i], destination)) {
      return i
    }
  }
  return -1
}

/** True when the route brings freight to this destination. */
export function routeReaches(route, destination) {
  return unloadIndex(route, destination) >= 0
}

/** Readable lane, e.g. "Bengaluru > Hyderabad > Vizag". */
export function laneText(route) {
  return stopSequence(route).join(' > ')
}

/**
 * Why an order may not be dropped on a route, or null when it may.
 * The wording is deliberately the operator's language: which order, going where, and what the route
 * actually does instead.
 */
export function dropRejection(route, order) {
  if (!route || !order) {
    return null
  }
  const status = String(route.status || '').toUpperCase()
  if (['DISPATCHED', 'IN_TRANSIT', 'COMPLETED'].includes(status)) {
    return `Route ${route.routeId} is ${status.replace(/_/g, ' ').toLowerCase()} and cannot take new orders.`
  }
  if (order.destination && !routeReaches(route, order.destination)) {
    return `Destination did not match: ${order.orderId} is going to ${order.destination}, but route ${route.routeId} runs ${laneText(route)}.`
  }
  const free = (route.maxCapacity || 0) - (route.currentWeight || 0)
  if (order.weight > free) {
    return `Route ${route.routeId} has only ${free.toLocaleString()} kg free and ${order.orderId} needs ${Number(
      order.weight || 0
    ).toLocaleString()} kg.`
  }
  return null
}
