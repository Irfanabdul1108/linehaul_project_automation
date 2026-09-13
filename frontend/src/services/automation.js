import { getActiveWarehouseId, scopedUrl } from './warehouse.js'

/**
 * Calls the smart assignment endpoints.
 *
 * Every request carries the selected warehouse twice - as a query parameter (via scopedUrl) and in
 * the body - because the backend prefers the body and older deployments only understand the query.
 * Failures keep the exact message the API sent, so "Route capacity exceeded." or
 * "AI recommendation is temporarily unavailable" always reach the dispatcher unchanged.
 */

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function call(path, body = null, method = 'POST') {
  let response
  try {
    response = await fetch(scopedUrl(path), {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === null ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('The Linehaul API could not be reached. Is the backend running on port 8080?', 0)
  }

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }
    }
  }
  if (!response.ok) {
    throw new ApiError(data?.message || `The request failed (HTTP ${response.status}).`, response.status)
  }
  return data
}

function withDepot(body) {
  const depot = getActiveWarehouseId()
  return { ...(body || {}), ...(depot ? { warehouseId: depot } : {}) }
}

/** Step 1: read the order, look at every route, come back with the ranked candidates. */
export function recommendRoutes(orderId, { useAi = true } = {}) {
  return call(`/api/orders/${encodeURIComponent(orderId)}/recommend-routes`, withDepot({ useAi }))
}

/** Step 2: load the chosen order onto the chosen route, optionally with a driver and a truck. */
export function assignOrder(orderId, { routeId, driverId, truckId }) {
  return call(`/api/orders/${encodeURIComponent(orderId)}/assign`, withDepot({
    orderId,
    routeId,
    driverId: driverId || undefined,
    truckId: truckId || undefined,
  }))
}

/** No route fitted and the dispatcher confirmed: create the route and put the order on it. */
export function createRouteAndAssign(payload) {
  return call('/api/routes/create-and-assign', withDepot(payload))
}

/** One order of the "Assign All Orders" run, so its progress can be shown as it happens. */
export function autoAssignOrder(orderId) {
  return call(`/api/orders/${encodeURIComponent(orderId)}/auto-assign`)
}

/** The whole batch in one go; used for the summary line and by anyone scripting against the API. */
export function assignAllOrders({ useAi = false } = {}) {
  return call('/api/orders/assign-all', withDepot({ useAi }))
}
