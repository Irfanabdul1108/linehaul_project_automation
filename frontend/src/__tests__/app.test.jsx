import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../services/api.js', () => ({
  getDashboard: vi.fn(),
  getOrders: vi.fn(),
  getRoutes: vi.fn(),
  getRouteOrders: vi.fn(),
  getVehicles: vi.fn(),
  getDrivers: vi.fn(),
  assignOrderToRoute: vi.fn(),
  removeOrderFromRoute: vi.fn(),
  assignTruck: vi.fn(),
  assignDriver: vi.fn(),
  unassignTruck: vi.fn(),
  unassignDriver: vi.fn(),
  dispatchRoute: vi.fn(),
  createOrder: vi.fn(),
  createRoute: vi.fn(),
  getWarehouses: vi.fn(),
  getLaneDurations: vi.fn(),
  lookupLaneDuration: vi.fn(),
}))

import {
  getDashboard,
  getOrders,
  getRoutes,
  getRouteOrders,
  getVehicles,
  getDrivers,
  assignOrderToRoute,
  dispatchRoute,
  getWarehouses,
  getLaneDurations,
  lookupLaneDuration,
} from '../services/api.js'

import Dashboard from '../pages/Dashboard.jsx'
import { createRoute } from '../services/api.js'
import Orders from '../pages/Orders.jsx'
import RoutesPage from '../pages/Routes.jsx'

const notify = vi.fn()

const ORDERS = [
  {
    orderId: 'LH-1001',
    customer: 'ABC Logistics',
    origin: 'EXPINTL',
    destination: 'ATLTEST',
    weight: 850,
    pieces: 12,
    serviceDate: '2026-08-25',
    status: 'READY',
    eta: null,
    routeId: null,
  },
  {
    orderId: 'LH-1002',
    customer: 'Fast Freight Co',
    origin: 'Dallas',
    destination: 'Chicago',
    weight: 1200,
    pieces: 8,
    serviceDate: '2026-08-25',
    status: 'ASSIGNED',
    eta: '05:00 AM (+1 day)',
    routeId: 'LH-1029',
  },
  {
    orderId: 'LH-1003',
    customer: 'Sunrise Cargo',
    origin: 'Austin',
    destination: 'Dallas',
    weight: 700,
    pieces: 5,
    serviceDate: '2026-08-26',
    status: 'READY',
    eta: null,
    routeId: null,
  },
]

const ROUTES = [
  {
    routeId: 'LH-1029',
    origin: 'EXPINTL',
    destination: 'ATLTEST',
    departureTime: '20:00',
    travelDuration: 9,
    eta: '05:00 AM (+1 day)',
    status: 'READY',
    orderIds: ['LH-1002'],
    truckId: 'T-182',
    driverId: 'D-101',
    maxCapacity: 10000,
    currentWeight: 1200,
    capacityPercent: 12,
    readiness: 'READY',
    readinessReason: 'Ready to dispatch',
    hasOrders: true,
    hasTruck: true,
    hasDriver: true,
    capacityOk: true,
  },
  {
    routeId: 'LH-1030',
    origin: 'Dallas',
    destination: 'Chicago',
    departureTime: '21:00',
    travelDuration: 14,
    eta: '11:00 AM (+1 day)',
    status: 'BLOCKED',
    orderIds: [],
    truckId: 'T-204',
    driverId: null,
    maxCapacity: 8000,
    currentWeight: 0,
    capacityPercent: 0,
    readiness: 'BLOCKED',
    readinessReason: 'Driver not assigned',
    hasOrders: false,
    hasTruck: true,
    hasDriver: false,
    capacityOk: true,
  },
]

const VEHICLES = [
  { truckId: 'T-182', type: 'Truck', capacity: 10000, status: 'ASSIGNED', routeId: 'LH-1029' },
  { truckId: 'T-407', type: 'Van', capacity: 4000, status: 'AVAILABLE', routeId: null },
]

const DRIVERS = [
  { driverId: 'D-101', name: 'John Smith', status: 'ASSIGNED', routeId: 'LH-1029' },
  { driverId: 'D-102', name: 'Maria Garcia', status: 'AVAILABLE', routeId: null },
]

beforeEach(() => {
  vi.clearAllMocks()
  getDashboard.mockResolvedValue({
    totalOrders: 32,
    totalRoutes: 5,
    activeRoutes: 1,
    readyRoutes: 1,
    blockedRoutes: 3,
    totalVehicles: 5,
    totalDrivers: 5,
    ordersToDispatch: ORDERS,
    activeRouteList: ROUTES,
  })
  getOrders.mockResolvedValue(ORDERS)
  getRoutes.mockResolvedValue(ROUTES)
  getRouteOrders.mockResolvedValue([ORDERS[1]])
  getVehicles.mockResolvedValue(VEHICLES)
  getDrivers.mockResolvedValue(DRIVERS)
  getWarehouses.mockResolvedValue([])
  getLaneDurations.mockResolvedValue([])
  lookupLaneDuration.mockResolvedValue({ known: false })
  global.fetch = vi.fn(async () => ({
    ok: true,
    json: async () => [],
    text: async () => '[]',
  }))
})

function renderPage(page) {
  return render(<MemoryRouter>{page}</MemoryRouter>)
}

describe('Dashboard', () => {
  it('shows the four summary cards from /api/dashboard/summary', async () => {
    renderPage(<Dashboard notify={notify} />)

    expect(await screen.findByText('Total Orders')).toBeInTheDocument()
    expect(screen.getByText('32')).toBeInTheDocument()
    expect(screen.getAllByText('Active Routes').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('Ready Routes')).toBeInTheDocument()
    expect(screen.getByText('Blocked Routes')).toBeInTheDocument()
    expect(getDashboard).toHaveBeenCalledTimes(1)
  })

  it('lists orders to dispatch and the active routes with their ETA', async () => {
    renderPage(<Dashboard notify={notify} />)

    expect(await screen.findByText('Orders to Dispatch')).toBeInTheDocument()
    expect(await screen.findAllByText('LH-1001')).not.toHaveLength(0)
    expect(await screen.findAllByText('05:00 AM (+1 day)')).not.toHaveLength(0)
  })
})

describe('Orders page', () => {
  it('renders the order table with the required columns', async () => {
    renderPage(<Orders notify={notify} />)

    expect(await screen.findByText('LH-1001')).toBeInTheDocument()
    expect(screen.getByText('ABC Logistics')).toBeInTheDocument()
    expect(screen.getByText('850 kg')).toBeInTheDocument()
    for (const column of ['Order ID', 'Customer', 'Origin', 'Destination', 'Weight', 'Status', 'ETA', 'Route']) {
      expect(screen.getByText(column)).toBeInTheDocument()
    }
  })

  it('searches by order ID and sends the term to the API', async () => {
    getOrders.mockImplementation(async ({ search } = {}) =>
      ORDERS.filter((o) => !search || o.orderId.includes(search))
    )
    renderPage(<Orders notify={notify} />)

    await screen.findByText('LH-1001')
    fireEvent.change(screen.getByPlaceholderText('Search by Order ID...'), {
      target: { value: 'LH-1002' },
    })

    await waitFor(() => expect(screen.queryByText('LH-1001')).not.toBeInTheDocument())
    expect(screen.getByText('LH-1002')).toBeInTheDocument()
    expect(getOrders).toHaveBeenLastCalledWith({ search: 'LH-1002', status: '' })
  })

  it('opens the order details modal with the lifecycle timeline', async () => {
    renderPage(<Orders notify={notify} />)

    fireEvent.click(await screen.findByText('LH-1001'))

    expect(await screen.findByText('Order LH-1001')).toBeInTheDocument()
    expect(screen.getByText('Timeline')).toBeInTheDocument()
    for (const step of ['Created', 'Ready', 'Manifested', 'Assigned', 'Dispatched', 'In Transit', 'Completed']) {
      expect(screen.getByText(step)).toBeInTheDocument()
    }
    expect(screen.getAllByText('12').length).toBeGreaterThan(0) 
  })
})

describe('Routes page', () => {
  it('shows unassigned orders, the drop zones and route readiness', async () => {
    renderPage(<RoutesPage notify={notify} />)

    expect(await screen.findByText('Unassigned Orders')).toBeInTheDocument()
    expect(await screen.findAllByText('Drop orders here')).toHaveLength(2)
    expect(screen.getAllByText('READY').length).toBeGreaterThan(0)
    expect(screen.getAllByText('BLOCKED').length).toBeGreaterThan(0)
  })

  it('assigns an order to a route that reaches its destination', async () => {
    assignOrderToRoute.mockResolvedValue(ROUTES[0])
    renderPage(<RoutesPage notify={notify} />)

    // LH-1001 goes to ATLTEST, and LH-1029 is the run that ends there
    const dragged = await screen.findByText('LH-1001')
    const target = screen.getByTestId('drop-LH-1029')

    fireEvent.dragStart(dragged)
    fireEvent.dragOver(target)
    fireEvent.drop(target, { dataTransfer: { getData: () => 'LH-1001' } })

    await waitFor(() =>
      expect(assignOrderToRoute).toHaveBeenCalledWith('LH-1029', 'LH-1001')
    )
    await waitFor(() =>
      expect(notify).toHaveBeenCalledWith(
        'Order LH-1001 assigned to route LH-1029.',
        'success'
      )
    )
  })

  /**
   * Requirement 1: only the routes that actually serve the order's destination may accept it. A
   * Chicago run must refuse an ATLTEST order instead of quietly swallowing it.
   */
  it('refuses a drop on a route that does not reach the destination and says so', async () => {
    renderPage(<RoutesPage notify={notify} />)

    const dragged = await screen.findByText('LH-1001')
    const wrongRoute = screen.getByTestId('drop-LH-1030')

    fireEvent.dragStart(dragged)
    fireEvent.drop(wrongRoute, { dataTransfer: { getData: () => 'LH-1001' } })

    await waitFor(() =>
      expect(notify).toHaveBeenCalledWith(
        'Destination did not match: LH-1001 is going to ATLTEST, but route LH-1030 runs Dallas > Chicago.',
        'error'
      )
    )
    // nothing was sent to the API, so the order is still waiting in the pile
    expect(assignOrderToRoute).not.toHaveBeenCalled()
    expect(await screen.findByText('LH-1001')).toBeInTheDocument()
  })

  it('marks the routes that cannot take the dragged order while it is in hand', async () => {
    renderPage(<RoutesPage notify={notify} />)

    fireEvent.dragStart(await screen.findByText('LH-1001'))

    expect(screen.getByTestId('drop-LH-1029').className).toContain('can-accept')
    expect(screen.getByTestId('drop-LH-1030').className).toContain('cannot-accept')
    expect(screen.getByText('Does not reach ATLTEST')).toBeInTheDocument()
  })

  it('reports "Route capacity exceeded." from the API and keeps the order unassigned', async () => {
    // LH-1003 runs Austin to Dallas, and LH-1030 is the route that starts in Dallas... so the one
    // route that serves it is the one the API then refuses on capacity.
    assignOrderToRoute.mockRejectedValue(new Error('Route capacity exceeded.'))
    getRoutes.mockResolvedValue([
      ROUTES[0],
      { ...ROUTES[1], origin: 'Austin', destination: 'Dallas' },
    ])
    renderPage(<RoutesPage notify={notify} />)

    const dragged = await screen.findByText('LH-1003')
    fireEvent.dragStart(dragged)
    fireEvent.drop(screen.getByTestId('drop-LH-1030'), {
      dataTransfer: { getData: () => 'LH-1003' },
    })

    await waitFor(() =>
      expect(notify).toHaveBeenCalledWith('Route capacity exceeded.', 'error')
    )
    expect(await screen.findByText('LH-1003')).toBeInTheDocument()
  })

  it('refuses to dispatch a blocked route and shows the reason from the backend', async () => {
    dispatchRoute.mockRejectedValue(
      new Error('Route cannot be dispatched. Reason: driver not assigned.')
    )
    renderPage(<RoutesPage notify={notify} />)

    const dispatchButtons = await screen.findAllByText('Dispatch')
    fireEvent.click(dispatchButtons[1]) 

    await waitFor(() =>
      expect(notify).toHaveBeenCalledWith(
        'Route cannot be dispatched. Reason: driver not assigned.',
        'error'
      )
    )
  })

  it('opens the route details modal with readiness checklist and its orders', async () => {
    renderPage(<RoutesPage notify={notify} />)

    const viewButtons = await screen.findAllByText('View')
    fireEvent.click(viewButtons[0])

    expect(await screen.findByText('Route LH-1029')).toBeInTheDocument()
    expect(screen.getByText('Route readiness')).toBeInTheDocument()
    expect(screen.getByText('Orders on this route')).toBeInTheDocument()
    expect((await screen.findAllByText('LH-1002')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('EXPINTL')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('ATLTEST')).length).toBeGreaterThan(0)
  })
})

const EXTRA_ROUTES = ['LH-2001', 'LH-2002', 'LH-2003', 'LH-2004', 'LH-2005'].map((id) => ({
  routeId: id,
  origin: 'Austin',
  destination: 'Dallas',
  departureTime: '19:00',
  travelDuration: 5,
  eta: '12:00 AM',
  status: 'DRAFT',
  orderIds: [],
  truckId: null,
  driverId: null,
  maxCapacity: 5000,
  currentWeight: 0,
  capacityPercent: 0,
  readiness: 'BLOCKED',
  readinessReason: 'No orders assigned',
  hasOrders: false,
  hasTruck: false,
  hasDriver: false,
  capacityOk: true,
}))

const SEVEN_ROUTES = [...ROUTES, ...EXTRA_ROUTES]

describe('Routes pagination', () => {
  it('shows six routes per page and moves between pages', async () => {
    getRoutes.mockResolvedValue(SEVEN_ROUTES)
    renderPage(<RoutesPage notify={notify} />)

    expect(await screen.findAllByText('Drop orders here')).toHaveLength(6)
    expect(screen.getAllByText('Page 1 of 2 - 7 routes, 6 per page').length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByText('Next \u2192')[0])
    await screen.findAllByText('LH-2005')
    expect(screen.getAllByText('Drop orders here')).toHaveLength(1)
    expect(screen.queryByText('LH-1029')).not.toBeInTheDocument()

    fireEvent.click(screen.getAllByText('\u2190 Prev')[0])
    await screen.findAllByText('LH-1029')
  })

  it('jumps to the page that contains a newly created route', async () => {
    getRoutes.mockResolvedValue(SEVEN_ROUTES)
    const created = {
      routeId: 'R-9',
      origin: 'EXPINTL',
      destination: 'Chicago',
      departureTime: '20:00',
      travelDuration: 9,
      eta: '05:00 AM (+1 day)',
      status: 'DRAFT',
      orderIds: [],
      truckId: null,
      driverId: null,
      maxCapacity: 1000,
      currentWeight: 0,
      capacityPercent: 0,
      readiness: 'BLOCKED',
      readinessReason: 'No orders assigned',
      hasOrders: false,
      hasTruck: false,
      hasDriver: false,
      capacityOk: true,
    }
    createRoute.mockResolvedValue(created)
    getRoutes.mockImplementation(async () =>
      created.seen ? [...SEVEN_ROUTES, created] : SEVEN_ROUTES
    )
    createRoute.mockImplementation(async (r) => {
      created.seen = true
      return created
    })

    const { container } = renderPage(<RoutesPage notify={notify} />)
    await screen.findAllByText('Page 1 of 2 - 7 routes, 6 per page')

    fireEvent.click(screen.getByText('+ Create Route'))
    fireEvent.change(screen.getByPlaceholderText('LH-1040'), { target: { value: 'R-9' } })
    fireEvent.submit(container.querySelector('form#route-form'))

    expect((await screen.findAllByText('R-9')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Page 2 of 2 - 8 routes, 6 per page').length).toBeGreaterThan(0)
  })
})
