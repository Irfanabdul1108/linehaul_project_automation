import { scopedUrl } from './warehouse.js'

// scopedUrl() appends ?warehouseId=... for the depot that is selected on the start screen, so every
// call below stays scoped without changing its own signature. With no depot selected the URL is
// untouched and the API answers for the whole network, exactly as before.
async function request(url, options = {}) {
  const response = await fetch(scopedUrl(url), {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }
    }
  }

  if (!response.ok) {
    throw new Error(data?.message || 'Something went wrong. Please try again.')
  }
  return data
}

export const getOrders = (params = {}) => {
  const query = new URLSearchParams()
  if (params.search) query.set('search', params.search)
  if (params.status) query.set('status', params.status)
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request(`/api/orders${suffix}`)
}

export const getOrder = (orderId) => request(`/api/orders/${orderId}`)
export const createOrder = (order) => request('/api/orders', { method: 'POST', body: JSON.stringify(order) })
export const updateOrder = (orderId, order) => request(`/api/orders/${orderId}`, { method: 'PUT', body: JSON.stringify(order) })
export const getRoutes = (search = '') => request(`/api/routes${search ? `?search=${encodeURIComponent(search)}` : ''}`)
export const getRoute = (routeId) => request(`/api/routes/${routeId}`)
export const getRouteOrders = (routeId) => request(`/api/routes/${routeId}/orders`)
export const createRoute = (route) => request('/api/routes', { method: 'POST', body: JSON.stringify(route) })
export const assignOrderToRoute = (routeId, orderId) => request(`/api/routes/${routeId}/orders/${orderId}`, { method: 'POST' })
export const removeOrderFromRoute = (routeId, orderId) => request(`/api/routes/${routeId}/orders/${orderId}`, { method: 'DELETE' })
export const assignTruck = (routeId, truckId) => request(`/api/routes/${routeId}/truck?truckId=${encodeURIComponent(truckId)}`, { method: 'PUT' })
export const unassignTruck = (routeId) => request(`/api/routes/${routeId}/truck`, { method: 'DELETE' })
export const assignDriver = (routeId, driverId) => request(`/api/routes/${routeId}/driver?driverId=${encodeURIComponent(driverId)}`, { method: 'PUT' })
export const unassignDriver = (routeId) => request(`/api/routes/${routeId}/driver`, { method: 'DELETE' })
export const dispatchRoute = (routeId) => request(`/api/routes/${routeId}/dispatch`, { method: 'POST' })
export const getVehicles = () => request('/api/vehicles')
export const createVehicle = (vehicle) => request('/api/vehicles', { method: 'POST', body: JSON.stringify(vehicle) })
export const getDrivers = () => request('/api/drivers')
export const createDriver = (driver) => request('/api/drivers', { method: 'POST', body: JSON.stringify(driver) })
export const getDashboard = () => request('/api/dashboard/summary')
export const getWarehouses = () => request('/api/warehouses')

// Lane memory: how long the network already knows a drive between two places takes, so the Create
// Route form can fill the duration in instead of asking for it again. Not warehouse scoped - a lane
// belongs to the network, not to one depot.
export const getLaneDurations = () => request('/api/lane-durations')
export const lookupLaneDuration = (origin, destination) =>
  request(
    `/api/lane-durations/lookup?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(
      destination
    )}`
  )
