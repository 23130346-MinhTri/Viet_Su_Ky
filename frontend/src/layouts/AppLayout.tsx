import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/useAuth'

function AppLayout() {
  const { isAuthenticated, account, logout } = useAuth()

  return (
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/">
          VIỆT SỬ KÝ
        </NavLink>
        <nav aria-label="Điều hướng chính">
          <NavLink to="/">Trang chủ</NavLink>
          <NavLink to="/game">Game demo</NavLink>
          {isAuthenticated ? (
            <>
              <NavLink to="/profile">Hồ sơ</NavLink>
              <button className="nav-button" type="button" onClick={logout}>
                Đăng xuất
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Đăng nhập</NavLink>
              <NavLink to="/register">Đăng ký</NavLink>
            </>
          )}
        </nav>
        {account ? <span className="user-pill">{account.displayName}</span> : null}
      </header>
      <main className="page-content">
        <Outlet />
      </main>
    </div>
  )
}

export default AppLayout
