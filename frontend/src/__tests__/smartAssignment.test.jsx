import { describe, it, expect, vi, beforeEach } from 'vitest'
import React from 'react'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'

vi.mock('../services/automation.js', () => ({
  recommendRoutes: vi.fn(),
  assignOrder: vi.fn(),
  createRouteAndAssign: vi.fn(),
  autoAssignOrder: vi.fn(),
  assignAllOrders: vi.fn(),
  ApiError: class ApiError extends Error {},
}))

import { recommendRoutes, assignOrder, createRouteAndAssign } from '../services/automation.js'
import SmartAssignModal from '../components/SmartAssignModal.jsx'
import AutoAssignModal from '../components/AutoAssignModal.jsx'

const notify = vi.fn()

const order = {
  orderId: 'LH-5001',
  customer: 'Coastal Traders',
  origin: 'Warehouse A',
  destination: 'Hyderabad',
  weight: 1500,
  pieces: 12,
  serviceDate: '2026-09-15',
  status: 'READY',
  warehouseId: 'WH-A',
}

const exact = {
  rank: 1,
  routeId: 'LH-1029',
  warehouseId: 'WH-A',
  warehouseName: 'Warehouse A',
  crossWarehouse: false,
  origin: 'Warehouse A',
  destination: 'Hyderabad',
  stopSequence: ['Warehouse A', 'Vijayawada', 'Hyderabad'],
  stops: ['Vijayawada'],
  matchType: 'EXACT',
  matchLabel: 'Exact lane match: Warehouse A to Hyderabad is the same lane as the order.',
  pickupIndex: 0,
  dropoffIndex: 2,
  pickupLabel: 'Warehouse A',
  orderWeight: 1500,
  currentWeight: 6200,
  maxCapacity: 10000,
  availableCapacity: 3800,
  loadAfter: 7700,
  fillPercentAfter: 77,
  capacityPercent: 62,
  truckId: 'T-101',
  truckType: 'Truck',
  truckCapacity: 10000,
  driverId: 'D-103',
  driverName: 'Rahul Verma',
  status: 'READY',
  departureTime: '20:00',
  etaKnown: true,
  orderEta: '02:30 AM',
  travelTime: '9h 0m',
  needsDriver: false,
  needsTruck: false,
  score: 100,
  reasons: ['Exact lane match'],
  warnings: [],
  scoreBreakdown: [{ label: 'Destination match', points: 40, detail: 'same lane' }],
}

const cross = {
  ...exact,
  rank: 2,
  routeId: 'LH-2022',
  warehouseId: 'WH-B',
  warehouseName: 'Warehouse B',
  crossWarehouse: true,
  origin: 'Warehouse B',
  stopSequence: ['Warehouse B', 'Warehouse A', 'Vijayawada', 'Hyderabad'],
  pickupIndex: 1,
  dropoffIndex: 3,
  pickupLabel: 'Warehouse A',
  matchType: 'DESTINATION',
  matchLabel: 'Route ends in Hyderabad, so the order is delivered at the final stop.',
  currentWeight: 5000,
  maxCapacity: 12000,
  availableCapacity: 7000,
  loadAfter: 6500,
  fillPercentAfter: 54,
  truckId: 'T-204',
  truckCapacity: 12000,
  driverId: 'D-203',
  driverName: 'Suresh Babu',
  score: 82,
  warnings: ['This route originates in WH-B. The order will be loaded at Warehouse A when the route reaches that stop.'],
}

const ADVICE = {
  orderId: 'LH-5001',
  warehouseId: 'WH-A',
  warehouseName: 'Warehouse A',
  candidates: [exact, cross],
  eligibleCount: 2,
  routesConsidered: 20,
  headline: 'LH-1029 is the best fit for LH-5001',
  message: 'Candidates ranked by gemini-2.5-flash-lite on top of the routes the engine validated.',
  aiStatus: 'gemini',
  aiUsed: true,
  newRouteRequired: false,
  steps: [
    { label: 'Read order', status: 'INFO', detail: 'LH-5001: Warehouse A to Hyderabad, 1,500 kg' },
    { label: 'Destination compatibility', status: 'PASSED', detail: '1 route(s) end in Hyderabad' },
    { label: 'Capacity available', status: 'PASSED', detail: '2 of 20 inspected route(s) can still carry 1,500 kg' },
  ],
  rejected: [
    {
      routeId: 'LH-1030',
      warehouseId: 'WH-A',
      origin: 'Warehouse A',
      destination: 'Chennai',
      stage: 'ROUTE_CAPACITY',
      reason: 'Not enough room: 7,500 kg of 8,000 kg is already loaded.',
    },
  ],
}

const NEW_ROUTE_ADVICE = {
  ...ADVICE,
  candidates: [],
  eligibleCount: 0,
  headline: 'No existing route can carry this order',
  message:
    'No existing route can carry this order, so a new route from Warehouse A to Chennai is recommended. 2 driver(s) and 1 truck(s) at this warehouse are free.',
  aiStatus: 'disabled',
  aiUsed: false,
  newRouteRequired: true,
  newRoute: {
    required: true,
    canCreate: true,
    origin: 'Warehouse A',
    destination: 'Chennai',
    suggestedRouteId: 'LH-4001',
    suggestedMaxCapacity: 4000,
    suggestedTravelDuration: 9,
    suggestedDepartureTime: '20:00',
    message: 'A new route is recommended.',
    drivers: [{ id: 'D-104', label: 'D-104 - Sameer Khan', note: 'Free at WH-A' }],
    vehicles: [{ id: 'T-103', label: 'T-103 - Van - 4,000 kg', note: 'Carries up to 4,000 kg' }],
  },
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('Smart assignment panel', () => {
  it('shows the ranked candidates with the reason for the recommendation', async () => {
    recommendRoutes.mockResolvedValue(ADVICE)
    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    expect(await screen.findByText('LH-1029')).toBeInTheDocument()
    expect(screen.getByText('⭐ RECOMMENDED')).toBeInTheDocument()
    expect(screen.getByText(/Exact lane match/)).toBeInTheDocument()
    expect(screen.getByText('LH-2022')).toBeInTheDocument()
    expect(screen.getAllByText(/capacity available:/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/02:30 AM/).length).toBeGreaterThan(0)
    expect(screen.getByText(/2 of 20 inspected route\(s\)/)).toBeInTheDocument()
    expect(screen.getByText(/in all warehouses fit/)).toBeInTheDocument()
  })

  it('explains the cross-warehouse route instead of hiding it', async () => {
    recommendRoutes.mockResolvedValue(ADVICE)
    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    await screen.findByText('LH-2022')
    fireEvent.click(screen.getByText('Select this route'))

    expect(await screen.findByText('Existing Cross-Warehouse Route')).toBeInTheDocument()
    expect(screen.getByText(/but has a valid stop at/)).toBeInTheDocument()
    expect(screen.getAllByText('Warehouse B').length).toBeGreaterThan(0)
    expect(screen.getByText('ADD ORDER TO THIS ROUTE')).toBeInTheDocument()
  })

  it('assigns the selected order through the assignment endpoint', async () => {
    recommendRoutes.mockResolvedValue(ADVICE)
    assignOrder.mockResolvedValue({ message: 'Order LH-5001 assigned to route LH-1029.' })
    const onAssigned = vi.fn()
    render(<SmartAssignModal order={order} notify={notify} onAssigned={onAssigned} onClose={() => {}} />)

    await screen.findByText('LH-1029')
    fireEvent.click(screen.getByTestId('confirm-assign'))

    await waitFor(() =>
      expect(assignOrder).toHaveBeenCalledWith('LH-5001', {
        routeId: 'LH-1029',
        driverId: undefined,
        truckId: undefined,
      })
    )
    expect(notify).toHaveBeenCalledWith('Order LH-5001 assigned to route LH-1029.', 'success')
    expect(onAssigned).toHaveBeenCalled()
  })

  it('lets the dispatcher pick a driver and a truck of this warehouse for a route that needs crew', async () => {
    recommendRoutes.mockResolvedValue({
      ...ADVICE,
      candidates: [{ ...exact, needsDriver: true, needsTruck: true, driverId: null, truckId: null }],
    })
    assignOrder.mockResolvedValue({ message: 'assigned' })

    render(
      <SmartAssignModal
        order={order}
        notify={notify}
        drivers={[
          { driverId: 'D-104', name: 'Sameer Khan', status: 'AVAILABLE', routeId: null },
          { driverId: 'D-103', name: 'Rahul Verma', status: 'ASSIGNED', routeId: 'LH-1029' },
        ]}
        vehicles={[{ truckId: 'T-103', type: 'Van', capacity: 4000, status: 'AVAILABLE', routeId: null }]}
        onClose={() => {}}
      />
    )

    await screen.findByText('LH-1029')
    const selects = await screen.findAllByRole('combobox')
    fireEvent.change(selects[0], { target: { value: 'D-104' } })
    fireEvent.change(selects[1], { target: { value: 'T-103' } })
    fireEvent.click(screen.getByTestId('confirm-assign'))

    await waitFor(() =>
      expect(assignOrder).toHaveBeenCalledWith('LH-5001', {
        routeId: 'LH-1029',
        driverId: 'D-104',
        truckId: 'T-103',
      })
    )
    // the assigned driver of another route is not offered at all
    expect(screen.queryByText('D-103 - Rahul Verma')).not.toBeInTheDocument()
  })

  it('proposes a new route and only creates it after an explicit confirmation', async () => {
    recommendRoutes.mockResolvedValue(NEW_ROUTE_ADVICE)
    createRouteAndAssign.mockResolvedValue({
      message: 'New route LH-4001 created and LH-5001 assigned successfully.',
      newRouteCreated: true,
    })

    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    expect(await screen.findByText('No suitable existing route')).toBeInTheDocument()
    expect(screen.getByText('New Route Required')).toBeInTheDocument()
    expect(screen.getByText('LH-4001')).toBeInTheDocument()

    const button = screen.getByTestId('create-and-assign')
    expect(button).toBeEnabled()
    fireEvent.click(button)

    await waitFor(() =>
      expect(createRouteAndAssign).toHaveBeenCalledWith({
        orderId: 'LH-5001',
        driverId: 'D-104',
        truckId: 'T-103',
        departureTime: '20:00',
        travelDuration: 9,
        maxCapacity: 4000,
      })
    )
  })

  it('refuses to invent a route when the warehouse has no suitable driver or truck', async () => {
    recommendRoutes.mockResolvedValue({
      ...NEW_ROUTE_ADVICE,
      message: 'No suitable driver/vehicle is currently available in this warehouse. Free drivers at WH-A: 0.',
      newRoute: { ...NEW_ROUTE_ADVICE.newRoute, canCreate: false, drivers: [], vehicles: [] },
    })

    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    expect(
      await screen.findByText(/No suitable driver\/vehicle is currently available in this warehouse/)
    ).toBeInTheDocument()
    expect(screen.queryByTestId('create-and-assign')).not.toBeInTheDocument()
    expect(screen.getByText(/Nothing was created/)).toBeInTheDocument()
  })

  it('says so when the AI is unavailable but keeps the deterministic ranking on screen', async () => {
    recommendRoutes.mockResolvedValue({
      ...ADVICE,
      aiStatus: 'unavailable',
      aiUsed: false,
      message:
        'AI recommendation is temporarily unavailable - the order of the list comes from the deterministic rules.',
    })

    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    expect((await screen.findAllByText(/AI recommendation is temporarily unavailable/)).length).toBeGreaterThan(0)
    expect(screen.getByText('Deterministic')).toBeInTheDocument()
    expect(screen.getByText('LH-1029')).toBeInTheDocument()
  })

  it('shows which routes were rejected and why in the details view', async () => {
    recommendRoutes.mockResolvedValue(ADVICE)
    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    await screen.findByText('LH-1029')
    fireEvent.click(screen.getByText('View all details'))

    expect(await screen.findByText('Routes that were not offered')).toBeInTheDocument()
    expect(screen.getByText('Not enough room: 7,500 kg of 8,000 kg is already loaded.')).toBeInTheDocument()
    expect(screen.getByText('ROUTE CAPACITY'))
  })

  it('surfaces the capacity refusal the backend sent instead of pretending success', async () => {
    recommendRoutes.mockResolvedValue(ADVICE)
    assignOrder.mockRejectedValue(new Error('Assignment refused - Not enough room on LH-1030.'))

    render(<SmartAssignModal order={order} notify={notify} onClose={() => {}} />)

    await screen.findByText('LH-1029')
    fireEvent.click(screen.getByTestId('confirm-assign'))

    expect(await screen.findByText(/Assignment refused - Not enough room on LH-1030./)).toBeInTheDocument()
    expect(notify).not.toHaveBeenCalled()
  })
})

describe('Assign All Orders run', () => {
  it('survives the React StrictMode remount and still processes every order', async () => {
    const { autoAssignOrder } = await import('../services/automation.js')
    autoAssignOrder.mockResolvedValue({ orderId: 'x', action: 'ASSIGNED', routeId: 'LH-1029', reason: 'ok' })
    const many = Array.from({ length: 5 }, (_, i) => ({
      orderId: `LH-50${i}`,
      origin: 'Warehouse A',
      destination: 'Hyderabad',
      weight: 500,
    }))

    render(
      <React.StrictMode>
        <AutoAssignModal orders={many} notify={notify} onClose={() => {}} />
      </React.StrictMode>
    )

    await waitFor(() => expect(autoAssignOrder).toHaveBeenCalledTimes(5))
    expect(screen.getAllByText('Assigned').length).toBe(6) // five rows plus the tally
    expect(autoAssignOrder.mock.calls.map((call) => call[0])).toEqual(['LH-500', 'LH-501', 'LH-502', 'LH-503', 'LH-504'])
  })

  const orders = [
    { orderId: 'LH-5001', origin: 'Warehouse A', destination: 'Hyderabad', weight: 1500 },
    { orderId: 'LH-5002', origin: 'Warehouse A', destination: 'Chennai', weight: 900 },
    { orderId: 'LH-5006', origin: 'Warehouse A', destination: 'Hyderabad', weight: 9500 },
  ]

  it('reports every order it could not place instead of hiding it', async () => {
    const { autoAssignOrder } = await import('../services/automation.js')
    autoAssignOrder
      .mockResolvedValueOnce({ orderId: 'LH-5001', action: 'ASSIGNED', routeId: 'LH-1029', reason: 'Matched on EXACT' })
      .mockResolvedValueOnce({
        orderId: 'LH-5002',
        action: 'NEW_ROUTE_REQUIRED',
        routeId: 'LH-4001',
        reason: 'No existing route can carry this order.',
      })
      .mockResolvedValueOnce({
        orderId: 'LH-5006',
        action: 'NEEDS_REVIEW',
        reason: 'No route fits and a new one cannot be created.',
      })

    const onFinished = vi.fn()
    const onOpenOrder = vi.fn()
    render(
      <AutoAssignModal
        orders={orders}
        warehouseName="Warehouse A"
        notify={notify}
        onClose={() => {}}
        onFinished={onFinished}
        onOpenOrder={onOpenOrder}
      />
    )

    expect((await screen.findAllByText('Assigned')).length).toBeGreaterThan(0)
    await waitFor(() => expect(autoAssignOrder).toHaveBeenCalledTimes(3))
    await waitFor(() => expect(onFinished).toHaveBeenCalledTimes(1))

    expect(screen.getByTestId('auto-list')).toHaveTextContent('LH-5001')
    expect(screen.getByTestId('auto-list')).toHaveTextContent('route LH-1029')
    expect(screen.getAllByText('New Routes Required').length).toBeGreaterThan(1)
    expect(screen.getAllByText('Needs Review').length).toBeGreaterThan(1)
    expect(screen.getByText(/need a person/)).toBeInTheDocument()

    fireEvent.click(screen.getAllByText('Handle this order')[0])
    expect(onOpenOrder).toHaveBeenCalledWith(orders[1])
  })

  it('keeps going when one order fails and reports it', async () => {
    const { autoAssignOrder } = await import('../services/automation.js')
    autoAssignOrder
      .mockRejectedValueOnce(new Error('The Linehaul API could not be reached.'))
      .mockResolvedValue({ orderId: 'LH-5002', action: 'ASSIGNED', routeId: 'LH-1030', reason: 'ok' })

    render(<AutoAssignModal orders={orders.slice(1)} notify={notify} onClose={() => {}} />)

    expect(await screen.findByText(/The Linehaul API could not be reached/)).toBeInTheDocument()
    expect(screen.queryByText('Matched on EXACT')).not.toBeInTheDocument()
  })
})
