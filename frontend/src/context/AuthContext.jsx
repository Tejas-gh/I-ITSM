import React, { createContext, useContext, useState, useEffect } from 'react';
import { INITIAL_USERS } from '../api/mockData';
import { ROLES, ROLE_GROUPS } from '../constants/itsmConstants';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [availableUsers] = useState(INITIAL_USERS);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('iitsm_active_user_id');
      const found = INITIAL_USERS.find((u) => u.id === saved);
      if (found) return found;
    } catch {
      // fallback
    }
    return INITIAL_USERS[0]; // Jane Doe (EMP01)
  });

  const selectUser = (userId) => {
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

  const isEmployee = ROLE_GROUPS.EMPLOYEE.includes(currentUser.role);
  const isTechnician = ROLE_GROUPS.TECHNICIAN.includes(currentUser.role);
  const isAdmin = ROLE_GROUPS.ADMINISTRATOR.includes(currentUser.role);

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        availableUsers,
        selectUser,
        isEmployee,
        isTechnician,
        isAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
