import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider } from './auth'

function renderApp(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider><App /></AuthProvider>
    </MemoryRouter>,
  )
}

describe('authentication experience', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('redirects protected customer routes to sign in', () => {
    renderApp('/dashboard')
    expect(screen.getByRole('heading', { name: /sign in to your account/i })).toBeInTheDocument()
  })

  it('validates an invalid email without calling the API', async () => {
    const user = userEvent.setup()
    renderApp('/login')
    await user.type(screen.getByLabelText(/email address/i), 'not-an-email')
    await user.type(screen.getByLabelText(/^password/i), 'long-enough-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))
    expect(screen.getByRole('alert')).toHaveTextContent('valid email')
  })

  it('uses the backend login contract and routes by returned role', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      if (String(input).endsWith('/auth/login')) {
        return new Response(JSON.stringify({
          user: { id: '5a6d7c1b-5f8d-4fe9-9238-a97fed16fc22', email: 'admin@ims.test', displayName: 'IMS Admin', role: 'ADMIN' },
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
          expiresIn: 900,
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/login')
    await user.type(screen.getByLabelText(/email address/i), 'admin@ims.test')
    await user.type(screen.getByLabelText(/^password/i), 'correct-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))
    expect(await screen.findByRole('heading', { name: /good work starts/i })).toBeInTheDocument()

    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/login'))
    const body = JSON.parse(String(loginCall?.[1]?.body))
    expect(body).toEqual({ email: 'admin@ims.test', password: 'correct-password' })
    expect(body).not.toHaveProperty('role')
  })

  it('shows Spring problem-detail errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'Invalid credentials' }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    ))
    const user = userEvent.setup()
    renderApp('/login')
    await user.type(screen.getByLabelText(/email address/i), 'customer@ims.test')
    await user.type(screen.getByLabelText(/^password/i), 'incorrect-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials')
  })
})
