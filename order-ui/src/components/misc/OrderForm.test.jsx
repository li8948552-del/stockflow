import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test-utils'
import OrderForm from './OrderForm'

const products = [
  { id: 'p1', sku: 'SKU-1', name: 'Keyboard', price: '12.50', active: true },
  { id: 'p2', sku: 'SKU-2', name: 'Mouse', price: '4.00', active: true },
  { id: 'p3', sku: 'SKU-3', name: 'Monitor', price: '20.00', active: true }
]
const warehouses = [
  { id: 'w1', warehouseCode: 'SYD', name: 'Sydney', active: true }
]
const inventory = [
  { productId: 'p1', available: 5 },
  { productId: 'p2', available: 2 },
  { productId: 'p3', available: 4 }
]

function renderForm(overrides = {}) {
  const props = {
    products,
    warehouses,
    inventory,
    isInventoryLoading: false,
    resourcesAvailable: true,
    isDataLoading: false,
    onWarehouseChange: vi.fn(),
    onSubmit: vi.fn().mockResolvedValue({}),
    isSubmitting: false,
    ...overrides
  }
  return { ...render(<OrderForm {...props} />), props }
}

async function choose(label, option) {
  await userEvent.click(screen.getByRole('combobox', { name: label }))
  await userEvent.click(screen.getAllByText(new RegExp(option)).at(-1))
}

describe('OrderForm', () => {
  it('builds the exact structured payload without prices or line numbers', async () => {
    const { props } = renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    await userEvent.clear(screen.getByRole('textbox', { name: 'Quantity' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Quantity' }), '2')
    await userEvent.click(
      screen.getByRole('button', { name: /reserve inventory/i })
    )
    expect(props.onSubmit).toHaveBeenCalledWith({
      warehouseId: 'w1',
      items: [{ productId: 'p1', quantity: 2 }]
    })
    expect(JSON.stringify(props.onSubmit.mock.calls[0][0])).not.toMatch(
      /price|lineNumber|userId|status/
    )
  })

  it('adds and removes stable item rows and prevents duplicate products', async () => {
    renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    expect(
      screen.getByRole('combobox', { name: 'Product 2' })
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('combobox', { name: 'Product 2' }))
    await userEvent.click(screen.getAllByText(/SKU-1/).at(-1))
    expect(screen.getByRole('combobox', { name: 'Product 2' })).toHaveValue('')
    await userEvent.keyboard('{Escape}')
    await userEvent.click(screen.getByRole('button', { name: 'Remove item 2' }))
    expect(
      screen.queryByRole('combobox', { name: 'Product 2' })
    ).not.toBeInTheDocument()
  })

  it('blocks invalid quantities and quantities above available inventory', async () => {
    const { props } = renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    await userEvent.clear(screen.getByRole('textbox', { name: 'Quantity' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Quantity' }), '6')
    await userEvent.click(
      screen.getByRole('button', { name: /reserve inventory/i })
    )
    expect(screen.getByText('Only 5 available')).toBeInTheDocument()
    expect(props.onSubmit).not.toHaveBeenCalled()
  })

  it('shows all required errors after an empty submit attempt', async () => {
    const { props } = renderForm()
    await userEvent.click(
      screen.getByRole('button', { name: /reserve inventory/i })
    )
    expect(screen.getByText('Select a warehouse')).toBeInTheDocument()
    expect(screen.getByText('Select a product')).toBeInTheDocument()
    expect(screen.getByText('Quantity is required')).toBeInTheDocument()
    expect(props.onSubmit).not.toHaveBeenCalled()
  })

  it.each(['0', '-1', '1.5'])(
    'shows an integer error for invalid quantity %s',
    async (quantity) => {
      const { props } = renderForm()
      await choose('Fulfilment warehouse', 'SYD')
      await choose('Product 1', 'SKU-1')
      const input = screen.getByRole('textbox', { name: 'Quantity' })
      await userEvent.type(input, quantity)
      await userEvent.tab()
      expect(
        screen.getByText('Enter a positive whole number')
      ).toBeInTheDocument()
      expect(props.onSubmit).not.toHaveBeenCalled()
    }
  )

  it('shows an availability error and removes it when corrected', async () => {
    renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    const input = screen.getByRole('textbox', { name: 'Quantity' })
    await userEvent.type(input, '6')
    await userEvent.tab()
    expect(screen.getByText('Only 5 available')).toBeInTheDocument()
    await userEvent.clear(input)
    await userEvent.type(input, '5')
    expect(screen.queryByText('Only 5 available')).not.toBeInTheDocument()
  })

  it('prevents a second submission while the first is pending', async () => {
    let resolveSubmit
    const onSubmit = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveSubmit = resolve
        })
    )
    renderForm({ onSubmit })
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    await userEvent.type(screen.getByRole('textbox', { name: 'Quantity' }), '1')
    const submit = screen.getByRole('button', { name: /reserve inventory/i })
    await userEvent.click(submit)
    expect(submit).toBeDisabled()
    await userEvent.click(submit)
    expect(onSubmit).toHaveBeenCalledTimes(1)
    await act(async () => resolveSubmit({}))
  })

  it('refreshes inventory selection when the warehouse changes', async () => {
    const { props } = renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    expect(props.onWarehouseChange).toHaveBeenCalledWith('w1')
  })

  it('keeps touched state attached to stable rows when a middle row is removed', async () => {
    renderForm()
    await choose('Fulfilment warehouse', 'SYD')
    await choose('Product 1', 'SKU-1')
    await userEvent.type(screen.getByRole('textbox', { name: 'Quantity' }), '1')
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await choose('Product 2', 'SKU-2')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[1],
      '3'
    )
    await userEvent.tab()
    expect(screen.getByText('Only 2 available')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Add item' }))
    await choose('Product 3', 'SKU-3')
    await userEvent.type(
      screen.getAllByRole('textbox', { name: 'Quantity' })[2],
      '1'
    )

    await userEvent.click(screen.getByRole('button', { name: 'Remove item 2' }))

    expect(screen.getByRole('combobox', { name: 'Product 2' })).toHaveValue(
      'SKU-3 — Monitor (20.00)'
    )
    expect(screen.queryByText('Only 2 available')).not.toBeInTheDocument()
  })
})
