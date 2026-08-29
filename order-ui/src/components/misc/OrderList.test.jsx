import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test-utils'
import OrderList from './OrderList'

function order(status = 'RESERVED') {
  return {
    id: 'order-12345678',
    user: { id: 1, username: 'bob' },
    warehouse: { id: 'w1', code: 'SYD', name: 'Sydney' },
    status,
    items: [
      {
        id: 'i2',
        lineNumber: 2,
        productSku: 'B',
        productName: 'Second',
        quantity: 1,
        unitPrice: '2.00',
        lineTotal: '2.00'
      },
      {
        id: 'i1',
        lineNumber: 1,
        productSku: 'A',
        productName: 'First',
        quantity: 2,
        unitPrice: '1.00',
        lineTotal: '2.00'
      }
    ],
    totalAmount: '4.00',
    createdAt: '2026-01-01T00:00:00Z',
    expiresAt: '2026-01-01T00:30:00Z'
  }
}

describe('OrderList', () => {
  it('shows details in line-number order without mutating API items', async () => {
    const value = order()
    render(
      <OrderList orders={[value]} onCancel={vi.fn()} cancellingId={null} />
    )
    await userEvent.click(screen.getByLabelText(/view order/i))
    const rows = screen.getAllByRole('row')
    const firstItemRow = rows.find((row) => within(row).queryByText('First'))
    const secondItemRow = rows.find((row) => within(row).queryByText('Second'))
    expect(rows.indexOf(firstItemRow)).toBeLessThan(rows.indexOf(secondItemRow))
    expect(value.items[0].lineNumber).toBe(2)
  })

  it('confirms and cancels only RESERVED orders', async () => {
    const onCancel = vi
      .fn()
      .mockResolvedValue({ ...order(), status: 'CANCELLED' })
    render(
      <OrderList orders={[order()]} onCancel={onCancel} cancellingId={null} />
    )
    await userEvent.click(screen.getByLabelText(/cancel order/i))
    await userEvent.click(
      await screen.findByRole('button', { name: 'Cancel order' })
    )
    expect(onCancel).toHaveBeenCalledWith('order-12345678')
  })

  it('confirms simulated payment and uses the handler once', async () => {
    const onPay = vi.fn().mockResolvedValue({ ...order(), status: 'PAID' })
    render(
      <OrderList
        orders={[{ ...order(), expiresAt: '2099-01-01T00:30:00Z' }]}
        onCancel={vi.fn()}
        onPay={onPay}
      />
    )
    await userEvent.click(screen.getByLabelText(/simulate payment/i))
    expect(
      screen.getByText(/does not contact a payment provider/i)
    ).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: /confirm simulated payment/i })
    )
    expect(onPay).toHaveBeenCalledTimes(1)
    expect(onPay).toHaveBeenCalledWith('order-12345678')
  })

  it('shows shipment only for PAID orders and never for USER-style handlers', () => {
    const { rerender } = render(
      <OrderList orders={[order('PAID')]} onCancel={vi.fn()} onShip={vi.fn()} />
    )
    expect(screen.getByLabelText(/ship order/i)).toBeInTheDocument()
    rerender(
      <OrderList
        orders={[order('SHIPPED')]}
        onCancel={vi.fn()}
        onShip={vi.fn()}
      />
    )
    expect(screen.queryByLabelText(/ship order/i)).not.toBeInTheDocument()
  })

  it('renders null lifecycle timestamps safely', async () => {
    const value = {
      ...order('PAID'),
      paidAt: null,
      shippedAt: null,
      expiredAt: null
    }
    render(<OrderList orders={[value]} onCancel={vi.fn()} onShip={vi.fn()} />)
    await userEvent.click(screen.getByLabelText(/view order/i))
    expect(screen.queryByText(/invalid date/i)).not.toBeInTheDocument()
  })

  it.each(['CANCELLED', 'PAID', 'SHIPPED', 'EXPIRED'])(
    'does not offer cancellation for %s',
    (status) => {
      render(
        <OrderList
          orders={[order(status)]}
          onCancel={vi.fn()}
          cancellingId={null}
        />
      )
      expect(screen.queryByLabelText(/cancel order/i)).not.toBeInTheDocument()
      expect(screen.getByText(status)).toBeInTheDocument()
    }
  )

  it('shows usernames only for the admin variant', () => {
    const { rerender } = render(
      <OrderList orders={[order()]} showUsername onCancel={vi.fn()} />
    )
    expect(screen.getByText('bob')).toBeInTheDocument()
    rerender(<OrderList orders={[order()]} onCancel={vi.fn()} />)
    expect(screen.queryByText('bob')).not.toBeInTheDocument()
  })

  it('displays the exact server total without recalculating or adding currency', () => {
    const value = order()
    value.totalAmount = '99999999999999999.99'
    value.items[0].lineTotal = '0.01'
    value.items[1].lineTotal = '0.01'
    render(<OrderList orders={[value]} onCancel={vi.fn()} />)
    expect(screen.getByText('99999999999999999.99')).toBeInTheDocument()
    expect(screen.queryByText('$99999999999999999.99')).not.toBeInTheDocument()
  })

  it('updates an open details modal when the server order changes', async () => {
    const initial = order('PAID')
    const { rerender } = render(
      <OrderList orders={[initial]} onCancel={vi.fn()} onShip={vi.fn()} />
    )
    await userEvent.click(screen.getByLabelText(/view order/i))
    expect(screen.getAllByText('PAID').length).toBeGreaterThan(0)

    rerender(
      <OrderList
        orders={[
          { ...initial, status: 'SHIPPED', shippedAt: '2026-01-01T01:00:00Z' }
        ]}
        onCancel={vi.fn()}
        onShip={vi.fn()}
      />
    )
    expect(screen.getAllByText('SHIPPED').length).toBeGreaterThan(0)
    expect(screen.queryByLabelText(/ship order/i)).not.toBeInTheDocument()
  })
})
