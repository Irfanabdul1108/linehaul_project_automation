import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { getOrders, createOrder, getRoutes } from '../services/api.js'
import { setActiveWarehouse, getActiveWarehouseId } from '../services/warehouse.js'
import SelectWarehouse from '../pages/SelectWarehouse.jsx'
import App from '../App.jsx'

const WAREHOUSES = [
  {
    warehouseId: 'WH-A',
    name: 'Warehouse A',
    code: 'WH-A',
    hubLocation: 'Bengaluru',
    city: 'Bengaluru',
    state: 'Karnataka',
    description: 'South consolidation centre.',
  },
  { warehouseId: 'WH-B', name: 'Warehouse B', code: 'WH-B', hubLocation: 'Chennai', city: 'Chennai', state: 'Tamil Nadu', description: '' },
  { warehouseId: 'WH-C', name: 'Warehouse C', code: 'WH-C', hubLocation: 'Pune', city: 'Pune', state: 'Maharashtra', description: '' },
  { warehouseId: 'WH-D', name: 'Warehouse D', code: 'WH-D', hubLocation: 'Kolkata', city: 'Kolkata', state: 'West Bengal', description: '' },
]

const ok = (payload) => ({ ok: true, json: async () => payload, text: async () => JSON.stringify(payload) })

let requested

async function fakeFetch(url, options) {
  requested.push({ url: String(url), method: options?.method || 'GET', body: options?.body })
  if (String(url).includes('/api/warehouses')) return ok(WAREHOUSES)
  if (String(url).includes('/api/dashboard/summary')) {
    return ok({
      warehouseName: 'Bengaluru',
      warehouseDesignation: 'Warehouse A',
      totalOrders: 19,
      unassignedOrders: 10,
      assignedOrders: 9,
      totalRoutes: 6,
      availableVehicles: 1,
      availableDrivers: 2,
      networkRoutes: 20,
      networkWarehouses: 4,
      ordersToDispatch: [],
      activeRouteList: [],
      unassignedOrderList: [],
    })
  }
  if (String(url).includes('/api/orders')) return ok([{ orderId: 'LH-5001' }])
  if (String(url).includes('/api/routes')) return ok([])
  return ok({})
}

beforeEach(() => {
  requested = []
  setActiveWarehouse('')
  global.fetch = vi.fn(fakeFetch)
})

afterEach(() => {
  setActiveWarehouse('')
})

describe('warehouse scope on the api client', () => {
  it('leaves the URLs alone while no warehouse is selected', async () => {
    await getOrders({ search: '', status: '' })
    await getRoutes()

    expect(requested[0].url).toBe('/api/orders')
    expect(requested[1].url).toBe('/api/routes')
  })

  it('scopes every read to the selected warehouse', async () => {
    setActiveWarehouse('WH-B')

    await getOrders({ search: 'LH-5', status: 'READY' })

    expect(requested[0].url).toBe('/api/orders?search=LH-5&status=READY&warehouseId=WH-B')
  })

  it('scopes writes too, so a new order lands in the warehouse on screen', async () => {
    setActiveWarehouse('WH-C')

    await createOrder({ orderId: 'LH-9001', customer: 'New Co', origin: 'Warehouse C', destination: 'Hyderabad', weight: 500, pieces: 2, serviceDate: '2026-09-20', status: 'READY' })

    expect(requested[0].url).toBe('/api/orders?warehouseId=WH-C')
    expect(requested[0].method).toBe('POST')
  })

  it('remembers the choice for the next visit', () => {
    setActiveWarehouse('WH-A')
    expect(getActiveWarehouseId()).toBe('WH-A')
    expect(globalThis.localStorage.getItem('linehaul.warehouse')).toBe('WH-A')

    setActiveWarehouse('')
    expect(getActiveWarehouseId()).toBe('')
  })
})

describe('the application behind the gate', () => {
  it('shows the picker first and the warehouse console after a choice', async () => {
    const view = render(
      <MemoryRouter>
        <App />
      </MemoryRouter>
    )

    expect(await screen.findByText('Choose a warehouse')).toBeInTheDocument()
    const card = await screen.findByTestId('warehouse-WH-A')
    fireEvent.click(card)

    expect(await screen.findByText('Working in')).toBeInTheDocument()
    // both the sidebar and the header chip name the depot by its PLACE, not by its designation
    expect((await screen.findAllByText('Bengaluru')).length).toBe(2)
    // the "Warehouse A" designation is still there, but only as the quiet secondary line
    expect((await screen.findAllByText('Warehouse A')).length).toBe(2)
    // the dashboard asks for its numbers scoped to the chosen depot, never for the whole network
    expect(requested.some((entry) => entry.url.includes('/api/dashboard/summary?warehouseId=WH-A'))).toBe(true)
    view.unmount()
  })
})

describe('warehouse start screen', () => {
  it('lists all four warehouses with what is waiting in them', async () => {
    render(<SelectWarehouse notify={vi.fn()} />)

    expect(await screen.findByText('Choose a warehouse')).toBeInTheDocument()
    for (const warehouse of WAREHOUSES) {
      expect(screen.getByTestId(`warehouse-${warehouse.warehouseId}`)).toBeInTheDocument()
    }
    expect(screen.getByText('Bengaluru, Karnataka')).toBeInTheDocument()
    expect((await screen.findAllByText('10')).length).toBeGreaterThan(0)
  })

  /**
   * The switcher is the one screen where "Warehouse A" is the headline - it is what tells the four
   * cards apart. Everywhere else the depot is called by its city.
   */
  it('is the only place that leads with the Warehouse X designation', async () => {
    render(<SelectWarehouse notify={vi.fn()} />)

    const card = await screen.findByTestId('warehouse-WH-A')
    expect(card.querySelector('h2').textContent).toBe('Warehouse A')
    expect(card.textContent).toContain('Bengaluru, Karnataka')
    // and the call to action invites the operator into the place, not into the designation
    expect(card.textContent).toContain('Open Bengaluru')
  })

  it('stores the clicked warehouse and hands over', async () => {
    const onSelect = vi.fn()
    render(<SelectWarehouse onSelect={onSelect} notify={vi.fn()} />)

    fireEvent.click(await screen.findByTestId('warehouse-WH-D'))

    await waitFor(() => expect(onSelect).toHaveBeenCalled())
    expect(getActiveWarehouseId()).toBe('WH-D')
  })

  it('explains itself when the API is not reachable', async () => {
    global.fetch = vi.fn(async () => ({ ok: false, json: async () => ({}) }))

    render(<SelectWarehouse notify={vi.fn()} />)

    expect(await screen.findByText(/warehouse list could not be loaded/i)).toBeInTheDocument()
  })
})
