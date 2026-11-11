import {
  INITIAL_INCIDENTS,
  INITIAL_SERVICES,
  INITIAL_CATEGORIES,
  INITIAL_USERS,
} from './mockData';
import {
  INCIDENT_STATUS,
  PRIORITY_LEVEL,
  WEIGHTS,
  NOTE_TYPE,
  CATEGORY_SOURCE,
} from '../constants/itsmConstants';

const USE_MOCK = true;
const API_BASE = import.meta.env.VITE_API_URL || '/api';

// In-memory / localStorage mock store so changes persist across page reloads
const STORAGE_KEY = 'iitsm_mock_incidents';
function getStoredIncidents() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch (e) {
    console.error('Failed reading localStorage', e);
  }
  return [...INITIAL_INCIDENTS];
}

function saveStoredIncidents(incidents) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(incidents));
  } catch (e) {
    console.error('Failed saving to localStorage', e);
  }
}

let mockIncidents = getStoredIncidents();

// Helper to calculate Priority per §K: base = I * U * C; bump; score; level
export function calculatePriorityScore(impact, urgency, businessCriticality, affectedUsers) {
  const i = WEIGHTS[impact] || 1;
  const u = WEIGHTS[urgency] || 1;
  const c = WEIGHTS[businessCriticality] || 1;
  const base = i * u * c;

  let bump = 0;
  if (affectedUsers >= 200) bump = 12;
  else if (affectedUsers >= 50) bump = 8;
  else if (affectedUsers >= 10) bump = 4;

  const score = base + bump;
  let level = PRIORITY_LEVEL.P4;
  if (score >= 36) level = PRIORITY_LEVEL.P1;
  else if (score >= 18) level = PRIORITY_LEVEL.P2;
  else if (score >= 8) level = PRIORITY_LEVEL.P3;

  return { score, level };
}

// Calculate mock SLA targets from spec
function calculateSlaDeadlines(priority, createdAtDate) {
  const t = createdAtDate.getTime();
  let respMinutes = 240;
  let resMinutes = 1440;

  if (priority === PRIORITY_LEVEL.P1) {
    respMinutes = 5;
    resMinutes = 30;
  } else if (priority === PRIORITY_LEVEL.P2) {
    respMinutes = 15;
    resMinutes = 120;
  } else if (priority === PRIORITY_LEVEL.P3) {
    respMinutes = 60;
    resMinutes = 480;
  }

  return {
    responseDeadline: new Date(t + respMinutes * 60000).toISOString(),
    resolutionDeadline: new Date(t + resMinutes * 60000).toISOString(),
  };
}

/**
 * Standard HTTP fetcher for real backend mode
 */
async function http(path, options = {}) {
  const token = localStorage.getItem('iitsm_jwt');
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorPayload = null;
    try {
      errorPayload = await response.json();
    } catch {
      // not JSON
    }
    const error = new Error(errorPayload?.message || `HTTP ${response.status} Error`);
    error.status = response.status;
    error.code = errorPayload?.code;
    error.details = errorPayload?.details;
    throw error;
  }

  if (response.status === 204) return null;
  return response.json();
}

/**
 * Incident API client
 */
export const incidentApi = {
  // GET /api/incidents
  async listVisibleIncidents(actor) {
    if (!USE_MOCK) {
      return http('/incidents');
    }

    mockIncidents = getStoredIncidents();
    if (!actor) return mockIncidents;

    // Role-based visibility (§Q.2)
    if (actor.role === 'EMPLOYEE') {
      return mockIncidents.filter((inc) => inc.reporterId === actor.id);
    }
    if (actor.role === 'TECHNICIAN' || actor.role === 'TEAM_LEAD') {
      return mockIncidents.filter((inc) => inc.assignedTechnicianId === actor.id);
    }
    // Administrator & IT Manager view all
    return [...mockIncidents];
  },

  // GET /api/incidents/{id}
  async getIncident(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}`);
    }

    mockIncidents = getStoredIncidents();
    const found = mockIncidents.find((i) => i.id === id);
    if (!found) {
      const err = new Error(`Incident not found: ${id}`);
      err.code = 'INCIDENT_NOT_FOUND';
      err.status = 404;
      throw err;
    }
    return { ...found };
  },

  // POST /api/incidents
  async createIncident(payload, actor) {
    if (!USE_MOCK) {
      return http('/incidents', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
    }

    mockIncidents = getStoredIncidents();
    const service = INITIAL_SERVICES.find((s) => s.id === payload.serviceId);
    const { score, level } = calculatePriorityScore(
      payload.impact,
      payload.urgency,
      service ? service.businessCriticality : 'MEDIUM',
      payload.affectedUsers
    );

    const now = new Date();
    const deadlines = calculateSlaDeadlines(level, now);
    const newId = `INC${1000 + mockIncidents.length + 1}`;

    // Auto-assign to first available technician if any
    const availableTech = INITIAL_USERS.find(
      (u) => (u.role === 'TECHNICIAN' || u.role === 'TEAM_LEAD') && u.available && u.active
    );

    const newIncident = {
      id: newId,
      title: payload.title,
      description: payload.description,
      reporterId: actor ? actor.id : 'EMP01',
      serviceId: payload.serviceId,
      categoryId: payload.categoryId,
      suggestedCategoryId: payload.suggestedCategoryId || null,
      categorySource: payload.categorySource || CATEGORY_SOURCE.MANUAL,
      impact: payload.impact,
      urgency: payload.urgency,
      affectedUsers: Number(payload.affectedUsers),
      priority: level,
      priorityScore: score,
      status: availableTech ? INCIDENT_STATUS.ASSIGNED : INCIDENT_STATUS.CLASSIFIED,
      assignedTechnicianId: availableTech ? availableTech.id : null,
      createdAt: now.toISOString(),
      responseDeadline: deadlines.responseDeadline,
      resolutionDeadline: deadlines.resolutionDeadline,
      acknowledgedAt: null,
      resolvedAt: null,
      verifiedAt: null,
      closedAt: null,
      notes: [],
      resolution: null,
      feedback: null,
    };

    mockIncidents.unshift(newIncident);
    saveStoredIncidents(mockIncidents);
    return { ...newIncident };
  },

  // POST /api/incidents/suggest-category
  async suggestCategory(title, description) {
    if (!USE_MOCK) {
      return http('/incidents/suggest-category', {
        method: 'POST',
        body: JSON.stringify({ title, description }),
      });
    }

    const text = `${title} ${description}`.toLowerCase();
    if (text.includes('vpn') || text.includes('network') || text.includes('firewall') || text.includes('dns')) {
      return { categoryId: 'NETWORK', confidence: 0.92, reason: 'Matched keywords: vpn/network/firewall', source: CATEGORY_SOURCE.KEYWORD };
    }
    if (text.includes('database') || text.includes('postgres') || text.includes('query') || text.includes('sql')) {
      return { categoryId: 'DATABASE', confidence: 0.88, reason: 'Matched keywords: database/sql/postgres', source: CATEGORY_SOURCE.KEYWORD };
    }
    if (text.includes('login') || text.includes('sso') || text.includes('auth') || text.includes('password')) {
      return { categoryId: 'AUTHENTICATION', confidence: 0.9, reason: 'Matched keywords: login/auth/sso', source: CATEGORY_SOURCE.KEYWORD };
    }
    if (text.includes('disk') || text.includes('drive') || text.includes('storage') || text.includes('backup')) {
      return { categoryId: 'STORAGE', confidence: 0.85, reason: 'Matched keywords: storage/drive', source: CATEGORY_SOURCE.KEYWORD };
    }
    if (text.includes('laptop') || text.includes('monitor') || text.includes('cable') || text.includes('hardware')) {
      return { categoryId: 'HARDWARE', confidence: 0.86, reason: 'Matched keywords: hardware/cable/monitor', source: CATEGORY_SOURCE.KEYWORD };
    }
    if (text.includes('payment') || text.includes('checkout') || text.includes('app') || text.includes('api')) {
      return { categoryId: 'APPLICATION', confidence: 0.82, reason: 'Matched keywords: app/payment', source: CATEGORY_SOURCE.KEYWORD };
    }

    return null;
  },

  // POST /api/incidents/{id}/assign
  async assignIncident(id, technicianId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/assign`, {
        method: 'POST',
        body: JSON.stringify({ technicianId }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.assignedTechnicianId = technicianId;
      inc.status = INCIDENT_STATUS.ASSIGNED;
    });
  },

  // POST /api/incidents/{id}/reassign
  async reassignIncident(id, technicianId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/reassign`, {
        method: 'POST',
        body: JSON.stringify({ technicianId }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.assignedTechnicianId = technicianId;
      inc.status = INCIDENT_STATUS.ASSIGNED;
    });
  },

  // POST /api/incidents/{id}/auto-assign
  async autoAssignIncident(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/auto-assign`, { method: 'POST' });
    }

    const availableTech = INITIAL_USERS.find(
      (u) => (u.role === 'TECHNICIAN' || u.role === 'TEAM_LEAD') && u.available && u.active
    );
    if (!availableTech) {
      throw new Error('No available technicians found for auto-assignment');
    }

    return this._updateIncident(id, (inc) => {
      inc.assignedTechnicianId = availableTech.id;
      inc.status = INCIDENT_STATUS.ASSIGNED;
    });
  },

  // POST /api/incidents/{id}/acknowledge
  async acknowledgeIncident(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/acknowledge`, { method: 'POST' });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.ACKNOWLEDGED;
      if (!inc.acknowledgedAt) {
        inc.acknowledgedAt = new Date().toISOString();
      }
    });
  },

  // POST /api/incidents/{id}/start-work
  async startWork(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/start-work`, { method: 'POST' });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.IN_PROGRESS;
    });
  },

  // POST /api/incidents/{id}/notes
  async addInvestigationNote(id, text, authorId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/notes`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.notes.push({
        authorId: authorId || 'TECH01',
        type: NOTE_TYPE.INVESTIGATION,
        text,
        createdAt: new Date().toISOString(),
      });
    });
  },

  // POST /api/incidents/{id}/request-info
  async requestInfo(id, text, authorId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/request-info`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.WAITING_FOR_USER;
      inc.notes.push({
        authorId: authorId || 'TECH01',
        type: NOTE_TYPE.INFO_REQUEST,
        text,
        createdAt: new Date().toISOString(),
      });
    });
  },

  // POST /api/incidents/{id}/respond-info
  async respondToInfo(id, text, authorId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/respond-info`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.IN_PROGRESS;
      inc.notes.push({
        authorId: authorId || 'EMP01',
        type: NOTE_TYPE.INFO_RESPONSE,
        text,
        createdAt: new Date().toISOString(),
      });
    });
  },

  // POST /api/incidents/{id}/additional-info
  async addAdditionalInfo(id, text, authorId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/additional-info`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.notes.push({
        authorId: authorId || 'EMP01',
        type: NOTE_TYPE.ADDITIONAL_INFO,
        text,
        createdAt: new Date().toISOString(),
      });
    });
  },

  // POST /api/incidents/{id}/resolve
  async resolveIncident(id, text, resolverId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/resolve`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      });
    }

    const nowIso = new Date().toISOString();
    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.RESOLVED;
      inc.resolvedAt = nowIso;
      inc.resolution = {
        resolverId: resolverId || 'TECH01',
        summary: text,
        resolvedAt: nowIso,
      };
    });
  },

  // POST /api/incidents/{id}/verify
  async verifyResolution(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/verify`, { method: 'POST' });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.VERIFIED;
      inc.verifiedAt = new Date().toISOString();
    });
  },

  // POST /api/incidents/{id}/reject-resolution
  async rejectResolution(id) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/reject-resolution`, { method: 'POST' });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.IN_PROGRESS;
      inc.resolution = null;
      inc.resolvedAt = null;
    });
  },

  // POST /api/incidents/{id}/close
  async closeIncident(id, closureNote, closedById) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/close`, {
        method: 'POST',
        body: JSON.stringify({ closureNote }),
      });
    }

    return this._updateIncident(id, (inc) => {
      inc.status = INCIDENT_STATUS.CLOSED;
      inc.closedAt = new Date().toISOString();
      inc.closedById = closedById || 'ADMIN01';
      inc.closureNote = closureNote || null;
    });
  },

  // POST /api/incidents/{id}/feedback
  async submitFeedback(id, rating, comment, authorId) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/feedback`, {
        method: 'POST',
        body: JSON.stringify({ rating, comment }),
      });
    }

    return this._updateIncident(id, (inc) => {
      if (inc.feedback) {
        throw new Error('Feedback already submitted for this incident');
      }
      inc.feedback = {
        authorId: authorId || 'EMP01',
        rating: Number(rating),
        comment: comment || '',
        submittedAt: new Date().toISOString(),
      };
    });
  },

  // PUT /api/incidents/{id}/priority-inputs
  async updatePriorityInputs(id, impact, urgency) {
    if (!USE_MOCK) {
      return http(`/incidents/${id}/priority-inputs`, {
        method: 'PUT',
        body: JSON.stringify({ impact, urgency }),
      });
    }

    return this._updateIncident(id, (inc) => {
      const service = INITIAL_SERVICES.find((s) => s.id === inc.serviceId);
      const { score, level } = calculatePriorityScore(
        impact,
        urgency,
        service ? service.businessCriticality : 'MEDIUM',
        inc.affectedUsers
      );
      inc.impact = impact;
      inc.urgency = urgency;
      inc.priority = level;
      inc.priorityScore = score;
    });
  },

  // Internal helper to update and persist mock store
  _updateIncident(id, mutator) {
    mockIncidents = getStoredIncidents();
    const idx = mockIncidents.findIndex((i) => i.id === id);
    if (idx === -1) {
      throw new Error(`Incident not found: ${id}`);
    }
    const item = { ...mockIncidents[idx] };
    mutator(item);
    mockIncidents[idx] = item;
    saveStoredIncidents(mockIncidents);
    return { ...item };
  },

  // Reset mock data to original seed
  resetMockData() {
    mockIncidents = [...INITIAL_INCIDENTS];
    saveStoredIncidents(mockIncidents);
    return mockIncidents;
  },
};

/**
 * Reference data API
 */
export const referenceApi = {
  async listServices() {
    return [...INITIAL_SERVICES];
  },
  async listCategories() {
    return [...INITIAL_CATEGORIES];
  },
  async listUsers() {
    return [...INITIAL_USERS];
  },
  async listTechnicians() {
    return INITIAL_USERS.filter((u) => u.role === 'TECHNICIAN' || u.role === 'TEAM_LEAD');
  },
};
