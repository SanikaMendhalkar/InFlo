
import { useCallback, useEffect, useState } from 'react'
import type { AuthUser } from './Login'

const API_URL = 'http://localhost:8080/api/teams/members'

type TeamMember = {
  id: number
  fullName: string
  email: string
  role: 'MANAGER' | 'TEAM_LEAD' | 'MEMBER'
  teamName: string | null
  active: boolean
  activeWorkItems: number
}

type Props = {
  user: AuthUser
}

export default function TeamMembers({ user }: Props) {
  const [members, setMembers] = useState<TeamMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  const loadMembers = useCallback(async (signal: AbortSignal) => {
    setLoading(true)
    setError('')

    try {
      const response = await fetch(API_URL, {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${user.token}`,
        },
        signal,
      })

      if (response.status === 401) {
        throw new Error('Your session has expired. Please sign in again.')
      }

      if (response.status === 403) {
        throw new Error(
          'You do not have permission to view team members.',
        )
      }

      if (!response.ok) {
        throw new Error(`Could not load team members (${response.status}).`)
      }

      const data: TeamMember[] = await response.json()

      if (!signal.aborted) {
        setMembers(data)
      }
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') return

      if (!signal.aborted) {
        setError(
          err instanceof Error
            ? err.message
            : 'An unexpected error occurred.',
        )
      }
    } finally {
      if (!signal.aborted) {
        setLoading(false)
      }
    }
  }, [user.token])

  useEffect(() => {
    const controller = new AbortController()
    void loadMembers(controller.signal)

    return () => controller.abort()
  }, [loadMembers, reloadKey])

  const totalWork = members.reduce(
    (total, member) => total + member.activeWorkItems,
    0,
  )

  return (
    <section className="members-section" id="team-members">
      <div className="section-heading">
        <div>
          <div className="section-title-row">
            <h2>Team Members</h2>
            <span className="section-count">{members.length}</span>
          </div>
          <p>
            View team composition and compare active workloads.
          </p>
        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={() => setReloadKey((value) => value + 1)}
          disabled={loading}
        >
          Refresh members
        </button>
      </div>

      <div className="members-summary">
        <article className="metric-card">
          <span className="metric-label">Visible members</span>
          <div className="metric-value">
            {loading ? '—' : members.length}
          </div>
        </article>

        <article className="metric-card">
          <span className="metric-label">Active work items</span>
          <div className="metric-value">
            {loading ? '—' : totalWork}
          </div>
        </article>

        <article className="metric-card">
          <span className="metric-label">Members with assigned work</span>
          <div className="metric-value">
            {loading
              ? '—'
              : members.filter(
                  (member) => member.activeWorkItems > 0,
                ).length}
          </div>
        </article>
      </div>

      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}

      <div className="members-table-wrap">
        <table className="members-table">
          <thead>
            <tr>
              <th>Member</th>
              <th>Team</th>
              <th>Role</th>
              <th>Active work</th>
              <th>Account</th>
            </tr>
          </thead>

          <tbody>
            {loading && (
              <tr>
                <td colSpan={5}>Loading team members…</td>
              </tr>
            )}

            {!loading && !error && members.length === 0 && (
              <tr>
                <td colSpan={5}>No team members found.</td>
              </tr>
            )}

            {!loading && members.map((member) => (
              <tr key={member.id}>
                <td>
                  <div className="member-identity">
                    <span className="avatar avatar-small">
                      {member.fullName
                        .split(/\s+/)
                        .slice(0, 2)
                        .map((part) => part[0]?.toUpperCase() ?? '')
                        .join('')}
                    </span>
                    <div>
                      <strong>{member.fullName}</strong>
                      <span>{member.email}</span>
                    </div>
                  </div>
                </td>

                <td>
                  {(member.teamName ?? '—')
                    .replaceAll('_', ' ')}
                </td>

                <td>{member.role.replaceAll('_', ' ')}</td>

                <td>
                  <span
                    className={
                      member.activeWorkItems > 0
                        ? 'workload-count has-work'
                        : 'workload-count'
                    }
                  >
                    {member.activeWorkItems}
                  </span>
                </td>

                <td>
                  <span
                    className={
                      member.active
                        ? 'member-status active'
                        : 'member-status inactive'
                    }
                  >
                    {member.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
