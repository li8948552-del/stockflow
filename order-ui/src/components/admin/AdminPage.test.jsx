import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { makeAdminUser, render, seedLocalStorage } from '../../test-utils'
import AdminPage from './AdminPage'
import { orderApi } from '../misc/OrderApi'

vi.mock('../misc/OrderApi')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function order(id, status) {
  return {
    id,
    user: { id: 1, username: 'alice' },
    warehouse: { id: 'w1', code: 'SYD', name: 'Sydney' },
    status,
    items: [],
    totalAmount: '0.00',
    createdAt: '2026-01-01T00:00:00Z',
    expiresAt: '2026-01-01T00:30:00Z'
  }
}

describe('AdminPage order filtering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    seedLocalStorage(makeAdminUser())
    orderApi.getUsers.mockResolvedValue({
      data: [{ id: 1, username: 'alice' }]
    })
    orderApi.getWarehouses.mockResolvedValue({
      data: [{ id: 'w1', warehouseCode: 'SYD', name: 'Sydney' }]
    })
  })

  it('allows only the newest filter request to update orders and loading', async () => {
    const reserved = deferred()
    const cancelled = deferred()
    orderApi.getOrders
      .mockResolvedValueOnce({ data: [] })
      .mockImplementationOnce(() => reserved.promise)
      .mockImplementationOnce(() => cancelled.promise)

    render(<AdminPage />)
    await userEvent.click(await screen.findByRole('tab', { name: 'Orders' }))
    const statusFilter = screen.getByRole('combobox', {
      name: 'Filter by status'
    })

    await userEvent.click(statusFilter)
    await userEvent.click(screen.getByText('RESERVED'))
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    await userEvent.click(statusFilter)
    await userEvent.click(screen.getByText('CANCELLED'))
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    cancelled.resolve({ data: [order('cancelled-order', 'CANCELLED')] })
    await screen.findByRole('row', { name: /CANCELLED/i })
    reserved.resolve({ data: [order('reserved-order', 'RESERVED')] })

    await waitFor(() => {
      expect(
        screen.getByRole('combobox', { name: 'Filter by status' })
      ).toHaveValue('CANCELLED')
      expect(
        screen.queryByRole('row', { name: /RESERVED/i })
      ).not.toBeInTheDocument()
    })
    expect(orderApi.getOrders.mock.calls[1][1]).toEqual({ status: 'RESERVED' })
    expect(orderApi.getOrders.mock.calls[2][1]).toEqual({ status: 'CANCELLED' })
    expect(orderApi.getOrders.mock.calls[1][2].aborted).toBe(true)
  })

  it('does not show an error for an aborted request', async () => {
    const pending = deferred()
    orderApi.getOrders
      .mockResolvedValueOnce({ data: [] })
      .mockImplementationOnce(() => pending.promise)
      .mockResolvedValueOnce({ data: [order('latest-order', 'CANCELLED')] })

    render(<AdminPage />)
    await userEvent.click(await screen.findByRole('tab', { name: 'Orders' }))
    const statusFilter = screen.getByRole('combobox', {
      name: 'Filter by status'
    })
    await userEvent.click(statusFilter)
    await userEvent.click(screen.getByText('RESERVED'))
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    await userEvent.click(statusFilter)
    await userEvent.click(screen.getByText('CANCELLED'))
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    pending.reject({ code: 'ERR_CANCELED' })

    await screen.findByRole('row', { name: /CANCELLED/i })
    expect(screen.queryByText('Could not load orders.')).not.toBeInTheDocument()
  })

  it('aborts the active order request when unmounted', async () => {
    const pending = deferred()
    orderApi.getOrders.mockImplementationOnce(() => pending.promise)
    const { unmount } = render(<AdminPage />)
    await waitFor(() => expect(orderApi.getOrders).toHaveBeenCalledTimes(1))
    const signal = orderApi.getOrders.mock.calls[0][2]

    unmount()

    expect(signal.aborted).toBe(true)
    pending.resolve({ data: [order('late-order', 'RESERVED')] })
  })
})
