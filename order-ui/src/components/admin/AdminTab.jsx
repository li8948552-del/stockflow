import { Box, LoadingOverlay, Tabs } from '@mantine/core'
import { IconDeviceLaptop, IconUsers } from '@tabler/icons-react'
import UserTable from './UserTable'
import OrderTable from './OrderTable'

function AdminTab(props) {
  return (
    <Tabs defaultValue='users' mt='md'>
      <Tabs.List>
        <Tabs.Tab value='users' leftSection={<IconUsers size={16} />}>
          Users
        </Tabs.Tab>
        <Tabs.Tab value='orders' leftSection={<IconDeviceLaptop size={16} />}>
          Orders
        </Tabs.Tab>
      </Tabs.List>
      <Tabs.Panel value='users' pt='md'>
        <Box pos='relative'>
          <LoadingOverlay visible={props.isUsersLoading} />
          <UserTable
            users={props.users}
            userUsernameSearch={props.userUsernameSearch}
            handleInputChange={props.handleInputChange}
            handleDeleteUser={props.handleDeleteUser}
            handleSearchUser={props.handleSearchUser}
          />
        </Box>
      </Tabs.Panel>
      <Tabs.Panel value='orders' pt='md'>
        <Box pos='relative'>
          <LoadingOverlay visible={props.isOrdersLoading} />
          <OrderTable
            orders={props.orders}
            users={props.users}
            warehouses={props.warehouses}
            filters={props.filters}
            setFilters={props.setFilters}
            handleSearchOrder={props.handleSearchOrder}
            handleCancelOrder={props.handleCancelOrder}
            cancellingId={props.cancellingId}
            processingId={props.processingId}
            handlePayOrder={props.handlePayOrder}
            handleShipOrder={props.handleShipOrder}
            isOrdersLoading={props.isOrdersLoading}
          />
        </Box>
      </Tabs.Panel>
    </Tabs>
  )
}

export default AdminTab
