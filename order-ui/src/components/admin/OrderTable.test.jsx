import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test-utils'
import OrderTable from './OrderTable'

const props = {
  orders: [],
  users: [{ id: 7, username: 'alice' }],
  warehouses: [{ id: 'w1', warehouseCode: 'SYD', name: 'Sydney' }],
  filters: { userId: null, status: null, warehouseId: null },
  setFilters: vi.fn(),
  handleSearchOrder: vi.fn((event) => event.preventDefault()),
  handleCancelOrder: vi.fn(),
  cancellingId: null,
  isOrdersLoading: false
}

describe('admin OrderTable', () => {
  it('offers only backend-supported filters', () => {
    render(<OrderTable {...props} />)
    expect(
      screen.getByRole('combobox', { name: 'Filter by user' })
    ).toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: 'Filter by status' })
    ).toBeInTheDocument()
    expect(
      screen.getByRole('combobox', { name: 'Filter by warehouse' })
    ).toBeInTheDocument()
    expect(
      screen.queryByPlaceholderText(/description/i)
    ).not.toBeInTheDocument()
  })

  it('submits the selected filters', async () => {
    render(<OrderTable {...props} />)
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    expect(props.handleSearchOrder).toHaveBeenCalled()
  })
})
