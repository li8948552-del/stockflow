const prod = {
  url: {
    API_BASE_URL: import.meta.env.VITE_API_BASE_URL || ''
  }
}

const dev = {
  url: {
    API_BASE_URL: 'http://localhost:8080'
  }
}

export const config = import.meta.env.DEV ? dev : prod
