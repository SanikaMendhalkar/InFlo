import TeamMembers from './TeamMembers'
import { useCallback, useEffect, useState } from 'react'
import Login, { type AuthUser } from './Login'
import './App.css'

const API_URL = 'http://localhost:8080/api/work-items'
const TEAMS_URL = 'http://localhost:8080/api/teams'
const PAGE_SIZE = 10

type Team = {
  id: number
  name: string
}

type WorkItem = {
  id: number
  title: string
  description?: string
  type: 'ISSUE' | 'TASK'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'RESOLVED' | 'CLOSED'
  team: string
  createdById: number
  createdByName: string
  assigneeId: number | null
  assigneeName: string | null
  parentId: number | null
  version: number
  createdAt: string
  updatedAt: string
  assignedAt?: string | null
  resolvedAt?: string | null
  escalatedAt?: string | null
  leadHelpRequested?: boolean
}

type PageResponse = {
  content?: WorkItem[]
  totalElements?: number
  totalPages?: number
  number?: number
  size?: number
  first?: boolean
  last?: boolean
  numberOfElements?: number
}

function formatDate(value?: string | null) {
  if (!value) return '—'

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return '—'

  return date.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

function initials(name?: string | null) {
  if (!name) return '—'

  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

function teamLabel(name: string) {
  return name
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function teamCssClass(name: string) {
  return name.toLowerCase() === 'customer_support'
    ? 'support'
    : name.toLowerCase()
}

function StatusBadge({ status }: { status: WorkItem['status'] }) {
  return (
    <span className={`badge status-${status.toLowerCase()}`}>
      <span className="badge-dot" />
      {status.replaceAll('_', ' ')}
    </span>
  )
}

function PriorityBadge({
  priority,
}: {
  priority: WorkItem['priority']
}) {
  const icons: Record<WorkItem['priority'], string> = {
    LOW: '↓',
    MEDIUM: '→',
    HIGH: '↑',
    CRITICAL: '!',
  }

  return (
    <span className={`priority priority-${priority.toLowerCase()}`}>
      <span className="priority-symbol">{icons[priority]}</span>
      {priority}
    </span>
  )
}

function App() {
  const [activeView, setActiveView] = useState<'overview' | 'members'>('overview')
  const [user, setUser] = useState<AuthUser | null>(null)

  const [items, setItems] = useState<WorkItem[]>([])
  const [teams, setTeams] = useState<Team[]>([])

  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [selectedItem, setSelectedItem] = useState<WorkItem | null>(null)
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)

  const [loading, setLoading] = useState(false)
  const [teamsLoading, setTeamsLoading] = useState(false)
  const [error, setError] = useState('')
  const [teamsError, setTeamsError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  // Load only the teams the signed-in user is allowed to see.
  useEffect(() => {
    if (!user) {
      setTeams([])
      setTeamsError('')
      setSelectedTeamId(null)
      return
    }

    const controller = new AbortController()

    async function loadTeams() {
      setTeamsLoading(true)
      setTeamsError('')

      try {
        const response = await fetch(TEAMS_URL, {
          method: 'GET',
          headers: {
            Accept: 'application/json',
           Authorization: `Bearer ${user?.token ?? ''}`,
          },
          signal: controller.signal,
        })

        if (response.status === 401) {
          setUser(null)
          throw new Error('Your session has expired. Please sign in again.')
        }

        if (!response.ok) {
          throw new Error(`Could not load teams (${response.status}).`)
        }

        const data: Team[] = await response.json()

        if (!controller.signal.aborted) {
          setTeams(data)
        }
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') return

        if (!controller.signal.aborted) {
          setTeams([])
          setTeamsError(
            err instanceof Error ? err.message : 'Could not load teams.',
          )
        }
      } finally {
        if (!controller.signal.aborted) {
          setTeamsLoading(false)
        }
      }
    }

    void loadTeams()

    return () => controller.abort()
  }, [user])

  // Fetch one server-paginated page of work items.
  const loadItems = useCallback(
    async (signal: AbortSignal) => {
      if (!user) return

      setLoading(true)
      setError('')

      try {
        const params = new URLSearchParams({
          page: String(page),
          size: String(PAGE_SIZE),
        })

        if (selectedTeamId !== null) {
          params.set('teamId', String(selectedTeamId))
        }

        // A convenience filter, not a replacement for backend authorization.
        if (user.role === 'MEMBER') {
          params.set('assigneeId', String(user.userId))
        }

        const response = await fetch(`${API_URL}?${params.toString()}`, {
          method: 'GET',
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${user.token}`,
          },
          signal,
        })

        if (response.status === 401) {
          setUser(null)
          throw new Error('Your session has expired. Please sign in again.')
        }

        if (!response.ok) {
          throw new Error(
            `The server returned ${response.status} ${response.statusText}.`,
          )
        }

        const data: PageResponse | WorkItem[] = await response.json()
        const isArrayResponse = Array.isArray(data)
        const pageData = isArrayResponse ? null : data
        const records = isArrayResponse ? data : (data.content ?? [])

        if (signal.aborted) return

        setItems(records)
        setTotalElements(pageData?.totalElements ?? records.length)
        setTotalPages(
          pageData?.totalPages ??
            (records.length < PAGE_SIZE ? page + 1 : page + 2),
        )

        setSelectedItem((current) => {
          if (!current) return null
          return records.find((item) => item.id === current.id) ?? null
        })
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') return

        if (!signal.aborted) {
          setError(
            err instanceof Error
              ? err.message
              : 'An unexpected error occurred while loading work items.',
          )
          setItems([])
          setTotalElements(0)
          setTotalPages(0)
          setSelectedItem(null)
        }
      } finally {
        if (!signal.aborted) {
          setLoading(false)
        }
      }
    },
    [page, selectedTeamId, user],
  )

  useEffect(() => {
    if (!user) {
      setItems([])
      setTotalElements(0)
      setTotalPages(0)
      setSelectedItem(null)
      setLoading(false)
      return
    }

    const controller = new AbortController()
    void loadItems(controller.signal)

    return () => controller.abort()
  }, [user, loadItems, reloadKey])

  function handleLogin(loggedInUser: AuthUser) {
    setUser(loggedInUser)
    setPage(0)
    setSelectedTeamId(null)
    setSelectedItem(null)
    setError('')
    setReloadKey((value) => value + 1)
  }

  function handleLogout() {
    setUser(null)
    setItems([])
    setTeams([])
    setSelectedItem(null)
    setSelectedTeamId(null)
    setPage(0)
    setTotalElements(0)
    setTotalPages(0)
    setError('')
    setTeamsError('')
  }

  function toggleTeam(teamId: number) {
    setSelectedTeamId((current) => (current === teamId ? null : teamId))
    setPage(0)
    setSelectedItem(null)
  }

  if (!user) {
    return <Login onLogin={handleLogin} />
  }

  const pageCounts = {
    open: items.filter((item) => item.status === 'OPEN').length,
    inProgress: items.filter((item) => item.status === 'IN_PROGRESS').length,
    blocked: items.filter((item) => item.status === 'BLOCKED').length,
    critical: items.filter((item) => item.priority === 'CRITICAL').length,
  }

  const firstItem =
    totalElements === 0 ? 0 : page * PAGE_SIZE + 1

  const lastItem = Math.min((page + 1) * PAGE_SIZE, totalElements)

  const overviewLabel =
    user.role === 'MANAGER'
      ? 'Organization overview'
      : user.role === 'TEAM_LEAD'
        ? 'Team overview'
        : 'My overview'

  const workItemsLabel =
    user.role === 'MANAGER'
      ? 'All work items'
      : user.role === 'TEAM_LEAD'
        ? 'Team work items'
        : 'My assigned work'

  const selectedTeam = teams.find((team) => team.id === selectedTeamId)

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="InFlo home">
          <span className="brand-mark">
            <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <path
                d="M6 9.5h8.5a4 4 0 0 1 4 4V23M26 22.5h-8.5a4 4 0 0 1-4-4V9"
                stroke="currentColor"
                strokeWidth="3"
                strokeLinecap="round"
              />
              <circle cx="6" cy="9.5" r="3" fill="currentColor" />
              <circle cx="26" cy="22.5" r="3" fill="currentColor" />
            </svg>
          </span>

          <span className="brand-copy">
            <strong>InFlo</strong>
            <small>INFORMATION FLOW</small>
          </span>
        </a>

        <div className="workspace-label">WORKSPACE</div>

        <nav className="sidebar-nav" aria-label="Main navigation">
         <button
  type="button"
  className={`nav-item ${activeView === 'overview' ? 'active' : ''}`}
  onClick={() => setActiveView('overview')}
>
  <span className="nav-icon">◫</span>
  {overviewLabel}
</button>

          <a className="nav-item" href="#work-items">
            <span className="nav-icon">▤</span>
            {workItemsLabel}
            <span className="nav-count">{totalElements}</span>
          </a>
           {user.role !== 'MEMBER' && (
  <button
    type="button"
    className={`nav-item ${activeView === 'members' ? 'active' : ''}`}
    onClick={() => setActiveView('members')}
  >
    <span className="nav-icon">♙</span>
    Team Members
  </button>
)}
          {user.role !== 'MEMBER' && (
            <a className="nav-item" href="#work-items">
              <span className="nav-icon">◎</span>
              {user.role === 'MANAGER' ? 'Incidents and risks' : 'Team blockers'}
            </a>
          )}
        </nav>

        <div className="sidebar-divider" />

        <div className="workspace-label">
          {user.role === 'MANAGER' ? 'TEAMS' : 'YOUR TEAM'}
        </div>

        <div className="team-list">
          {teamsLoading && (
            <p className="muted-text">Loading teams…</p>
          )}

          {teamsError && (
            <p className="muted-text" role="alert">
              {teamsError}
            </p>
          )}

          {!teamsLoading && !teamsError && teams.length === 0 && (
            <p className="muted-text">No teams available.</p>
          )}

          {teams.map((team) => (
            <button
              key={team.id}
              type="button"
              className={`team-item ${
                selectedTeamId === team.id ? 'selected-team' : ''
              }`}
              onClick={() => toggleTeam(team.id)}
              title={`Filter work by ${teamLabel(team.name)}`}
              aria-pressed={selectedTeamId === team.id}
            >
              <span className={`team-dot ${teamCssClass(team.name)}`} />
              <span>{teamLabel(team.name)}</span>
            </button>
          ))}
        </div>

        <div className="sidebar-bottom">
          <div className="help-card">
            <span className="help-icon">✳</span>
            <strong>Keep work flowing</strong>
            <p>Track issues, unblock teams, and keep everyone aligned.</p>
          </div>

          <div className="profile">
            <div className="avatar avatar-purple">
              {initials(user.fullName)}
            </div>

            <div className="profile-copy">
              <strong>{user.fullName}</strong>
              <span>
                {user.role.replaceAll('_', ' ')}
                {user.team ? ` · ${teamLabel(user.team)}` : ''}
              </span>
            </div>
          </div>
        </div>
      </aside>

      <main className="main-area" id="overview">
        <header className="topbar">
          <div className="breadcrumbs">
            <span>Workspace</span>
            <span className="crumb-separator">/</span>
            <strong>{overviewLabel}</strong>
          </div>

          <div className="topbar-actions">
            <span className="environment-label">
              <span className="online-dot" />
              Local environment
            </span>

            <button
              className="icon-button"
              type="button"
              title="Refresh work items"
              aria-label="Refresh work items"
              onClick={() => setReloadKey((value) => value + 1)}
            >
              ↻
            </button>

            <button
              className="secondary-button"
              type="button"
              onClick={handleLogout}
            >
              Sign out
            </button>

            <div className="avatar avatar-purple top-avatar">
              {initials(user.fullName)}
            </div>
          </div>
        </header>

        <div className="page-content">
          {activeView === 'members' && user.role !== 'MEMBER' ? (
  <TeamMembers user={user} />
) : (
  <>
    <section className="welcome-row">
            <div>
              <div className="eyebrow">
                <span className="eyebrow-line" />
                OPERATIONS CENTER
              </div>

              <h1>
                Good work starts with <span>clarity.</span>
              </h1>

              <p className="page-subtitle">
                {user.role === 'MANAGER'
                  ? 'Monitor work across teams, priorities, and incidents.'
                  : user.role === 'TEAM_LEAD'
                    ? 'Coordinate your team, track blockers, and keep work moving.'
                    : 'Focus on your assigned work and keep tasks moving forward.'}
              </p>
            </div>
 
            <div className="date-chip">
              <span>◷</span> Live workspace
            </div>
          </section>
          {error && (
            <div className="error-banner" role="alert">
              <span className="error-icon">!</span>

              <div>
                <strong>Couldn’t load work items</strong>
                <p>{error}</p>
                <span className="error-hint">
                  Check that Spring Boot is running at localhost:8080 and that
                  your session is valid.
                </span>
              </div>

              <button
                type="button"
                className="retry-button"
                onClick={() => setReloadKey((value) => value + 1)}
              >
                Try again
              </button>
            </div>
          )}

          <section className="metrics-grid" aria-label="Work item summary">
            <article className="metric-card">
              <div className="metric-top">
                <span className="metric-label">
                  {user.role === 'MEMBER' ? 'Assigned work items' : 'Total work items'}
                </span>
                <span className="metric-icon icon-blue">▤</span>
              </div>

              <div className="metric-value">
                {loading ? '—' : totalElements.toLocaleString('en-IN')}
              </div>

              <div className="metric-foot">Across matching pages</div>
            </article>

            <article className="metric-card">
              <div className="metric-top">
                <span className="metric-label">Open on this page</span>
                <span className="metric-icon icon-violet">◷</span>
              </div>

              <div className="metric-value">
                {loading ? '—' : pageCounts.open}
              </div>

              <div className="metric-foot">Awaiting action</div>
            </article>

            <article className="metric-card">
              <div className="metric-top">
                <span className="metric-label">In progress on this page</span>
                <span className="metric-icon icon-amber">↗</span>
              </div>

              <div className="metric-value">
                {loading ? '—' : pageCounts.inProgress}
              </div>

              <div className="metric-foot">Being worked on</div>
            </article>

            <article className="metric-card">
              <div className="metric-top">
                <span className="metric-label">Blocked on this page</span>
                <span className="metric-icon icon-red">!</span>
              </div>

              <div className="metric-value">
                {loading ? '—' : pageCounts.blocked}
              </div>

              <div className="metric-foot">
                {pageCounts.critical} critical priority on this page
              </div>
            </article>
          </section>

          <section className="work-section" id="work-items">
            <div className="section-heading">
              <div>
                <div className="section-title-row">
                  <h2>{workItemsLabel}</h2>
                  <span className="section-count">{totalElements}</span>
                </div>

                <p>
                  {user.role === 'MANAGER'
                    ? 'Review workload and priorities across the organization.'
                    : user.role === 'TEAM_LEAD'
                      ? 'Review work assigned to and created within your team.'
                      : 'Review the work assigned to you.'}
                </p>
              </div>

              <button
                className="secondary-button"
                type="button"
                onClick={() => setReloadKey((value) => value + 1)}
                disabled={loading}
              >
                <span className="refresh-symbol">↻</span>
                Refresh
              </button>
            </div>

            <div className="table-toolbar">
              <div className="table-context">
                <span className="context-dot" />
                {selectedTeam
                  ? teamLabel(selectedTeam.name)
                  : user.role === 'MANAGER'
                    ? 'All teams'
                    : user.team
                      ? teamLabel(user.team)
                      : 'Your workspace'}

                <span className="toolbar-divider">/</span>

                <span className="muted-text">
                  Server-side pagination · {PAGE_SIZE} per page
                </span>
              </div>

              <span className="server-label">
                <span className="online-dot" />
                {loading ? 'Loading from backend' : 'Backend connected'}
              </span>
            </div>

            <div className="kanban-board" aria-label="Work items by status">
              {([
                { key: 'OPEN', label: 'Open', color: 'open' },
                { key: 'IN_PROGRESS', label: 'In progress', color: 'progress' },
                { key: 'BLOCKED', label: 'Blocked', color: 'blocked' },
                { key: 'RESOLVED', label: 'Resolved', color: 'resolved' },
                { key: 'CLOSED', label: 'Closed', color: 'closed' },
              ] as const).map((column) => {
                const columnItems = items.filter((item) => item.status === column.key)
                return (
                  <section className="kanban-column" key={column.key} aria-label={`${column.label} work items`}>
                    <header className="kanban-column-header">
                      <span className={`kanban-status-dot ${column.color}`} />
                      <h3>{column.label}</h3>
                      <span className="kanban-count">{columnItems.length}</span>
                    </header>
                    <div className="kanban-cards">
                      {loading && <div className="kanban-empty">Loading work items…</div>}
                      {!loading && columnItems.length === 0 && (
                        <div className="kanban-empty">No items in this status</div>
                      )}
                      {!loading && columnItems.map((item) => (
                        <button
                          type="button"
                          key={item.id}
                          className={`kanban-card ${selectedItem?.id === item.id ? 'kanban-card-selected' : ''}`}
                          onClick={() => setSelectedItem(item)}
                        >
                          <div className="kanban-card-top">
                            <span className={`kanban-type ${item.type === 'ISSUE' ? 'issue' : 'task'}`}>
                              {item.type === 'ISSUE' ? '◇ Issue' : '✓ Task'}
                            </span>
                            <span className="kanban-id">INF-{item.id}</span>
                          </div>
                          <strong className="kanban-card-title">{item.title}</strong>
                          {item.description && <p className="kanban-card-description">{item.description}</p>}
                          <div className="kanban-card-badges">
                            <PriorityBadge priority={item.priority} />
                            <span className={`kanban-team-tag ${teamCssClass(item.team)}`}>{teamLabel(item.team)}</span>
                          </div>
                          <div className="kanban-card-footer">
                            <span className="avatar avatar-small">{initials(item.assigneeName)}</span>
                            <span className="kanban-assignee">{item.assigneeName || 'Unassigned'}</span>
                            <span className="kanban-date">{formatDate(item.createdAt)}</span>
                          </div>
                        </button>
                      ))}
                    </div>
                  </section>
                )
              })}
            </div>

            <div className="table-footer">
              <span className="pagination-summary">
                {loading
                  ? 'Loading work items…'
                  : totalElements === 0
                    ? 'No items to display'
                    : `Showing ${firstItem}–${lastItem} of ${totalElements} work items`}
              </span>

              <div className="pagination-controls">
                <button
                  className="pagination-button"
                  type="button"
                  disabled={loading || page === 0}
                  onClick={() => {
                    setSelectedItem(null)
                    setPage((current) => Math.max(0, current - 1))
                  }}
                  aria-label="Previous page"
                >
                  ←
                </button>

                <span className="pagination-page">
                  Page {page + 1}
                  {totalPages > 0 ? ` of ${totalPages}` : ''}
                </span>

                <button
                  className="pagination-button"
                  type="button"
                  disabled={
                    loading ||
                    (totalPages > 0 && page + 1 >= totalPages) ||
                    items.length < PAGE_SIZE
                  }
                  onClick={() => {
                    setSelectedItem(null)
                    setPage((current) => current + 1)
                  }}
                  aria-label="Next page"
                >
                  →
                </button>
              </div>
            </div>
          </section>

          {selectedItem && (
            <section className="detail-panel" aria-label="Selected work item">
              <div className="detail-header">
                <div>
                  <span className="eyebrow">WORK ITEM · INF-{selectedItem.id}</span>
                  <h2>{selectedItem.title}</h2>
                </div>

                <button
                  type="button"
                  className="icon-button"
                  aria-label="Close work item details"
                  onClick={() => setSelectedItem(null)}
                >
                  ×
                </button>
              </div>

              <div className="detail-badges">
                <StatusBadge status={selectedItem.status} />
                <PriorityBadge priority={selectedItem.priority} />
              </div>

              <p className="detail-description">
                {selectedItem.description || 'No description provided.'}
              </p>

              <div className="detail-meta">
                <div>
                  <span className="muted-text">Team</span>
                  <strong>{teamLabel(selectedItem.team)}</strong>
                </div>

                <div>
                  <span className="muted-text">Assignee</span>
                  <strong>{selectedItem.assigneeName || 'Unassigned'}</strong>
                </div>

                <div>
                  <span className="muted-text">Created by</span>
                  <strong>{selectedItem.createdByName || 'Unknown'}</strong>
                </div>

                <div>
                  <span className="muted-text">Created</span>
                  <strong>{formatDate(selectedItem.createdAt)}</strong>
                </div>

                <div>
                  <span className="muted-text">Last updated</span>
                  <strong>{formatDate(selectedItem.updatedAt)}</strong>
                </div>
              </div>
            </section>
          )}

                            <footer className="app-footer">
            <span>InFlo · Information Flow</span>
            <span>
              Signed in as {user.role.replaceAll('_', ' ').toLowerCase()}
            </span>
          </footer>
        </>
      )}
    </div>
  </main>
</div>
)
}

export default App