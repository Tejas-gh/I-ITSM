import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { INITIAL_USERS } from '../api/mockData';
import { ROLE_GROUPS } from '../constants/itsmConstants';
import { USE_MOCK, authApi, tokenStore, decodeJwt, UNAUTHORIZED_EVENT } from '../api/apiClient';

const AuthContext = createContext(null);

/** Builds the signed-in user from the JWT issued by the backend (sub = user id, role = Role name). */
function userFromToken(token) {
  const claims = token ? decodeJwt(token) : null;
  if (!claims || !claims.sub || !claims.role) return null;
  if (claims.exp && claims.exp * 1000 <= Date.now()) return null;
  return { id: claims.sub, name: claims.sub, role: claims.role, active: true };
}

function initialUser() {
  if (USE_MOCK) {
    try {
      const saved = localStorage.getItem('iitsm_active_user_id');
      const found = INITIAL_USERS.find((u) => u.id === saved);
      if (found) return found;
    } catch {
      // fallback
    }
    return INITIAL_USERS[0]; // Jane Doe (EMP01)
  }
  const user = userFromToken(tokenStore.get());
  if (!user) tokenStore.clear();
  return user;
}

export function AuthProvider({ children }) {
  // Mock mode only: a fixed list of demo users that can be switched from the navbar.
  const [availableUsers] = useState(USE_MOCK ? INITIAL_USERS : []);
  const [currentUser, setCurrentUser] = useState(initialUser);

  const selectUser = (userId) => {
    if (!USE_MOCK) return;
    const user = availableUsers.find((u) => u.id === userId);
    if (user) {
      setCurrentUser(user);
      try {
        localStorage.setItem('iitsm_active_user_id', user.id);
      } catch {
        // ignore
      }
    }
  };

  const login = useCallback(async (id, password) => {
    await authApi.login(id, password);
    const user = userFromToken(tokenStore.get());
    if (!user) {
      tokenStore.clear();
      throw new Error('The server returned an unusable token. Please try again.');
    }
    setCurrentUser(user);
    return user;
  }, []);

  const logout = useCallback(() => {
    authApi.logout();
    setCurrentUser(null);
  }, []);

  // The API client fires this when the backend rejects the stored token (expired or invalid).
  useEffect(() => {
    if (USE_MOCK) return undefined;
    const onUnauthorized = () => setCurrentUser(null);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  const role = currentUser?.role;
  const isAuthenticated = currentUser !== null;
  const isEmployee = ROLE_GROUPS.EMPLOYEE.includes(role);
  const isTechnician = ROLE_GROUPS.TECHNICIAN.includes(role);
  const isAdmin = ROLE_GROUPS.ADMINISTRATOR.includes(role);

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        availableUsers,
        selectUser,
        login,
        logout,
        isAuthenticated,
        isEmployee,
        isTechnician,
        isAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
