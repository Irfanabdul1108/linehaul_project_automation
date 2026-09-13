import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'

import { dropRejection, laneText, routeReaches, stopSequence, unloadIndex } from '../services/routeLane.js'
import {
  placeToken,
  samePlace,
  warehouseDesignation,
  warehouseLabel,
  warehousePlace,
  setActiveWarehouse,
} from '../services/warehouse.js'

vi.mock('../services/api.js', () => ({
  createRoute: vi.fn(),
  getRoutes: vi.fn(),
  getLaneDurations: vi.fn(),
  lookupLaneDuration: vi.fn(),
}))

import { createRoute, getRoutes, getLaneDurations, lookupLaneDuration } from '../services/api.js'
import CreateRouteModal from '../components/CreateRouteModal.jsx'

const WAREHOUSES = [
  {
    warehouseId: 'WH-A',
    name: 'Warehouse A',
    code: 'WH-A',
    hubLocation: 'Bengaluru',
    city: 'Bengaluru',
    state: 'Karnataka',
  },
  {
    warehouseId: 'WH-B',
    name: 'Warehouse B',
    code: 'WH-B',
    hubLocation: 'Chennai',
    city: 'Chennai',
    state: 'Tamil Nadu',
  },
]

const route = (overrides = {}) => ({
  routeId: 'LH-1029',
  origin: 'Bengaluru',
  destination: 'Hyderabad',
  stops: [],
  status: 'READY',
  maxCapacity: 10000,
  currentWeight: 0,
  ...overrides,
})

const order = (overrides = {}) => ({
  orderId: 'LH-5001',
  destination: 'Hyderabad',
  weight: 1500,
  ...overrides,
})

// ---------------------------------------------------------------- requirement 1

describe('a route may only take an order it can deliver', () => {
  it('reads the lane as origin, stops and destination in travel order', () => {
    const viaRoute = route({ destination: 'Vizag', stops: ['Warangal', 'Hyderabad'] })

    expect(stopSequence(viaRoute)).toEqual(['Bengaluru', 'Warangal', 'Hyderabad', 'Vizag'])
    expect(laneText(viaRoute)).toBe('Bengaluru > Warangal > Hyderabad > Vizag')
  })

  it('accepts a route that ends at the destination', () => {
    expect(routeReaches(route({ destination: 'Vizag' }), 'Vizag')).toBe(true)
    expect(unloadIndex(route({ destination: 'Vizag' }), 'Vizag')).toBe(1)
  })

  it('accepts a route that passes the destination on the way', () => {
    const viaRoute = route({ destination: 'Vizag', stops: ['Warangal', 'Hyderabad'] })

    expect(routeReaches(viaRoute, 'Hyderabad')).toBe(true)
    expect(unloadIndex(viaRoute, 'Hyderabad')).toBe(2)
  })

  it('refuses a route that simply goes somewhere else', () => {
    expect(routeReaches(route({ destination: 'Chennai' }), 'Vizag')).toBe(false)
  })

  it('never treats the origin as a drop-off point', () => {
    expect(routeReaches(route({ origin: 'Bengaluru', destination: 'Chennai' }), 'Bengaluru')).toBe(false)
  })

  it('explains the refusal in the operator\'s own words', () => {
    const rejection = dropRejection(
      route({ routeId: 'LH-1030', destination: 'Chennai' }),
      order({ orderId: 'LH-5010', destination: 'Vizag' })
    )

    expect(rejection).toBe(
      'Destination did not match: LH-5010 is going to Vizag, but route LH-1030 runs Bengaluru > Chennai.'
    )
  })

  it('lets a matching order through, and still guards status and capacity', () => {
    expect(dropRejection(route(), order())).toBeNull()

    expect(dropRejection(route({ status: 'IN_TRANSIT' }), order())).toContain('in transit')
    expect(dropRejection(route({ currentWeight: 9000 }), order())).toContain('only 1,000 kg free')
  })

  it('ignores case and generic words so one place is one place', () => {
    expect(samePlace('Hyderabad Hub', 'hyderabad')).toBe(true)
    expect(samePlace('Bengaluru', 'Chennai')).toBe(false)
    expect(placeToken('  WAREHOUSE-A ')).toBe('a')
    expect(routeReaches(route({ destination: 'Hyderabad Hub' }), 'hyderabad')).toBe(true)
  })
})

// ---------------------------------------------------------------- requirement 2

describe('a depot is named by its place, except in the switcher', () => {
  it('uses the city everywhere the operator works', () => {
    expect(warehousePlace(WAREHOUSES[0])).toBe('Bengaluru')
    expect(warehousePlace(WAREHOUSES[1])).toBe('Chennai')
  })

  it('keeps the Warehouse X designation available for the switcher alone', () => {
    expect(warehouseDesignation(WAREHOUSES[0])).toBe('Warehouse A')
    expect(warehouseLabel(WAREHOUSES[0])).toBe('Warehouse A - Bengaluru')
  })

  it('falls back gracefully when a depot has no city on record', () => {
    expect(warehousePlace({ warehouseId: 'WH-Z', name: 'Warehouse Z' })).toBe('Warehouse Z')
    expect(warehousePlace(null)).toBe('')
  })
})

// ---------------------------------------------------------------- requirement 3

describe('creating a route fills the duration in by itself', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActiveWarehouse('WH-A')
    getRoutes.mockResolvedValue([
      { routeId: 'LH-1029', origin: 'Bengaluru', destination: 'Hyderabad', stops: [] },
    ])
    getLaneDurations.mockResolvedValue([
      { origin: 'Bengaluru', destination: 'Hyderabad', travelDuration: 9, samples: 2 },
    ])
    lookupLaneDuration.mockResolvedValue({
      origin: 'Bengaluru',
      destination: 'Hyderabad',
      known: true,
      travelDuration: 9,
      samples: 2,
    })
    global.fetch = vi.fn(async () => ({
      ok: true,
      json: async () => WAREHOUSES,
      text: async () => JSON.stringify(WAREHOUSES),
    }))
  })

  it('starts the route at the city of the warehouse that is open', async () => {
    render(<CreateRouteModal onClose={vi.fn()} onCreated={vi.fn()} notify={vi.fn()} />)

    await waitFor(() => expect(screen.getByPlaceholderText('Bengaluru').value).toBe('Bengaluru'))
  })

  it('remembers the duration of a known lane so nobody has to type it', async () => {
    render(<CreateRouteModal onClose={vi.fn()} onCreated={vi.fn()} notify={vi.fn()} />)

    await waitFor(() => expect(screen.getByPlaceholderText('Bengaluru').value).toBe('Bengaluru'))
    fireEvent.change(screen.getByPlaceholderText('Hyderabad'), { target: { value: 'Hyderabad' } })

    await waitFor(() => expect(screen.getByTestId('travel-duration').value).toBe('9'))
    expect(lookupLaneDuration).toHaveBeenCalledWith('Bengaluru', 'Hyderabad')
    expect(screen.getByTestId('lane-note').textContent).toContain('Bengaluru to Hyderabad takes 9 h')
  })

  it('says so plainly when the lane is new, and keeps the field free', async () => {
    lookupLaneDuration.mockResolvedValue({ known: false, travelDuration: null })
    render(<CreateRouteModal onClose={vi.fn()} onCreated={vi.fn()} notify={vi.fn()} />)

    await waitFor(() => expect(screen.getByPlaceholderText('Bengaluru').value).toBe('Bengaluru'))
    fireEvent.change(screen.getByPlaceholderText('Hyderabad'), { target: { value: 'Leh' } })

    await waitFor(() =>
      expect(screen.getByTestId('lane-note').textContent).toContain('new to the network')
    )
    expect(screen.getByTestId('travel-duration').value).toBe('')
  })

  it('never overwrites a duration the dispatcher typed themselves', async () => {
    render(<CreateRouteModal onClose={vi.fn()} onCreated={vi.fn()} notify={vi.fn()} />)

    await waitFor(() => expect(screen.getByPlaceholderText('Bengaluru').value).toBe('Bengaluru'))
    fireEvent.change(screen.getByTestId('travel-duration'), { target: { value: '14' } })
    fireEvent.change(screen.getByPlaceholderText('Hyderabad'), { target: { value: 'Hyderabad' } })

    await waitFor(() => expect(lookupLaneDuration).toHaveBeenCalled())
    expect(screen.getByTestId('travel-duration').value).toBe('14')
  })

  it('refuses a route that starts and ends in the same place', async () => {
    const { container } = render(
      <CreateRouteModal onClose={vi.fn()} onCreated={vi.fn()} notify={vi.fn()} />
    )

    await waitFor(() => expect(screen.getByPlaceholderText('Bengaluru').value).toBe('Bengaluru'))
    fireEvent.change(screen.getByPlaceholderText('Hyderabad'), { target: { value: 'bengaluru' } })
    fireEvent.change(screen.getByPlaceholderText('LH-1040'), { target: { value: 'LH-9001' } })
    fireEvent.submit(container.querySelector('form#route-form'))

    expect(await screen.findByText('The origin and the destination are the same place.')).toBeInTheDocument()
    expect(createRoute).not.toHaveBeenCalled()
  })
})
