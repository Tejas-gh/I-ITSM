import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { incidentApi, referenceApi, escalationApi } from '../api/apiClient';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import LifecycleStepper from '../components/LifecycleStepper';
import ActionModal from '../components/ActionModal';
import AuditTimeline from '../components/AuditTimeline';
import { noteLabel, mergeRejectionRemarks } from '../components/noteLabel';
import {
  INCIDENT_STATUS,
  ROLE_GROUPS,
} from '../constants/itsmConstants';

export default function IncidentDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [incident, setIncident] = useState(null);
  const [technicians, setTechnicians] = useState([]);
  const [escalations, setEscalations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Modal control
  const [modalConfig, setModalConfig] = useState({
    isOpen: false,
    title: '',
    description: '',
    actionLabel: 'Submit',
    actionType: 'text',
    onConfirm: () => {},
  });

  const loadIncident = async () => {
    try {
      const data = await incidentApi.getIncident(id);
      setIncident(data);

      const escList = await escalationApi.getIncidentEscalations(id).catch(() => []);
      setEscalations(escList || []);

      if (ROLE_GROUPS.ADMINISTRATOR.includes(currentUser.role)) {
        const techList = await referenceApi.listTechnicians();
        setTechnicians(techList);
      } else {
        setTechnicians([]);
      }
    } catch (err) {
      setError(err.message || 'Failed to load incident details');
    }
  };

  useEffect(() => {
    let ignore = false;
    const fetchInitial = async () => {
      try {
        setLoading(true);
        setError(null);
        const [data, escList] = await Promise.all([
          incidentApi.getIncident(id),
          escalationApi.getIncidentEscalations(id).catch(() => []),
        ]);
        let techList = [];

        if (ROLE_GROUPS.ADMINISTRATOR.includes(currentUser.role)) {
          techList = await referenceApi.listTechnicians();
        }

        if (!ignore) {
          setIncident(data);
          setEscalations(escList || []);
          setTechnicians(techList);
        }
      } catch (err) {
        if (!ignore) {
          setError(err.message || 'Failed to load incident details');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };
    fetchInitial();
    return () => {
      ignore = true;
    };
  }, [id, currentUser]);

  if (loading) {
    return <div style={{ padding: '60px', textAlign: 'center', color: '#64748b' }}>Loading incident {id}...</div>;
  }

  if (error || !incident) {
    return (
      <div style={{ maxWidth: '800px', margin: '40px auto', padding: '24px', backgroundColor: '#fee2e2', borderRadius: '8px' }}>
        <h3 style={{ margin: 0, color: '#991b1b' }}>Error Loading Incident</h3>
        <p style={{ color: '#7f1d1d', margin: '8px 0 16px' }}>{error}</p>
        <button
          onClick={() => navigate('/incidents')}
          style={{ padding: '8px 16px', borderRadius: '6px', border: 'none', background: '#991b1b', color: '#fff', cursor: 'pointer' }}
        >
          Return to Incident Directory
        </button>
      </div>
    );
  }

  // Ownership & Role checks per §Q.2
  const isReporter = currentUser.id === incident.reporterId;
  const isAssignee = currentUser.id === incident.assignedTechnicianId;
  const isTechnician = ROLE_GROUPS.TECHNICIAN.includes(currentUser.role);
  const isAdmin = ROLE_GROUPS.ADMINISTRATOR.includes(currentUser.role);

  // State checks per §J.1 & §J.2
  const status = incident.status;
  const isNew = status === INCIDENT_STATUS.NEW;
  const isClassified = status === INCIDENT_STATUS.CLASSIFIED;
  const isAssigned = status === INCIDENT_STATUS.ASSIGNED;
  const isAcknowledged = status === INCIDENT_STATUS.ACKNOWLEDGED;
  const isInProgress = status === INCIDENT_STATUS.IN_PROGRESS;
  const isWaitingForUser = status === INCIDENT_STATUS.WAITING_FOR_USER;
  const isResolved = status === INCIDENT_STATUS.RESOLVED;
  const isVerified = status === INCIDENT_STATUS.VERIFIED;
  const isClosed = status === INCIDENT_STATUS.CLOSED;

  // Actions execution
  const openModal = (config) => {
    setModalConfig({
      isOpen: true,
      ...config,
    });
  };

  const closeModal = () => {
    setModalConfig((prev) => ({ ...prev, isOpen: false }));
  };

  // 1. Acknowledge
  const handleAcknowledge = async () => {
    try {
      await incidentApi.acknowledgeIncident(incident.id);
      await loadIncident();
    } catch (e) {
      alert(e.message);
    }
  };

  // 2. Start Work
  const handleStartWork = async () => {
    try {
      await incidentApi.startWork(incident.id);
      await loadIncident();
    } catch (e) {
      alert(e.message);
    }
  };

  // 3. Add Investigation Note
  const handleAddInvestigationNote = () => {
    openModal({
      title: 'Add Investigation Note',
      description: 'Record internal diagnostic steps, logs, or findings.',
      actionLabel: 'Add Note',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.addInvestigationNote(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 4. Request Info
  const handleRequestInfo = () => {
    openModal({
      title: 'Request Information from Reporter',
      description: 'Specify questions needed to continue investigation. Pauses resolution SLA clock.',
      actionLabel: 'Send Request',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.requestInfo(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 5. Respond to Info
  const handleRespondInfo = () => {
    openModal({
      title: 'Respond to Information Request',
      description: 'Provide details requested by the support technician to resume investigation.',
      actionLabel: 'Submit Response',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.respondToInfo(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 6. Additional Info
  const handleAddAdditionalInfo = () => {
    openModal({
      title: 'Provide Additional Information',
      description: 'Add extra details or attachments text to the incident log.',
      actionLabel: 'Append Information',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.addAdditionalInfo(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 7. Resolve Incident
  const handleResolve = () => {
    openModal({
      title: 'Resolve Incident',
      description: 'Document root cause and resolution summary for the reporter.',
      actionLabel: 'Mark Resolved',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.resolveIncident(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 8. Verify Resolution
  const handleVerify = async () => {
    if (window.confirm('Confirm that this incident is verified and fully resolved?')) {
      try {
        await incidentApi.verifyResolution(incident.id);
        await loadIncident();
      } catch (e) {
        alert(e.message);
      }
    }
  };

  // 9. Reject Resolution / Reopen
  const handleRejectResolution = () => {
    openModal({
      title: 'Reopen Incident (Reject Resolution)',
      description: 'Optionally explain why the resolution did not fix the problem. Your remark is shown with the rejection in the activity log.',
      actionLabel: 'Reopen Incident',
      actionType: 'text',
      optional: true,
      onConfirm: async (text) => {
        try {
          await incidentApi.rejectResolution(incident.id, text);
        } finally {
          // The incident may already be reopened even if saving the remark failed
          await loadIncident();
        }
      },
    });
  };

  // 10. Close Incident
  const handleClose = () => {
    openModal({
      title: 'Close Incident',
      description: 'Permanently close this verified incident with an administrative note.',
      actionLabel: 'Close Incident',
      actionType: 'text',
      onConfirm: async (text) => {
        await incidentApi.closeIncident(incident.id, text, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 11. Submit Feedback
  const handleSubmitFeedback = () => {
    openModal({
      title: 'Submit Service Feedback',
      description: 'Rate your satisfaction with the resolution of this issue.',
      actionLabel: 'Submit Rating',
      actionType: 'feedback',
      onConfirm: async (rating, comment) => {
        await incidentApi.submitFeedback(incident.id, rating, comment, currentUser.id);
        await loadIncident();
      },
    });
  };

  // 12. Assign / Reassign
  const handleAssign = () => {
    openModal({
      title: isAssigned || isAcknowledged ? 'Reassign Technician' : 'Assign Incident',
      description: 'Select a technician with appropriate skills and capacity.',
      actionLabel: isAssigned || isAcknowledged ? 'Reassign' : 'Assign',
      actionType: 'technician',
      technicians,
      onConfirm: async (technicianId) => {
        if (isAssigned || isAcknowledged) {
          await incidentApi.reassignIncident(incident.id, technicianId);
        } else {
          await incidentApi.assignIncident(incident.id, technicianId);
        }
        await loadIncident();
      },
    });
  };

  // 13. Auto Assign
  const handleAutoAssign = async () => {
    try {
      await incidentApi.autoAssignIncident(incident.id);
      await loadIncident();
    } catch (e) {
      alert(e.message);
    }
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Breadcrumb / Top Bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <button
          onClick={() => navigate('/incidents')}
          style={{ background: 'transparent', border: 'none', color: '#2563eb', cursor: 'pointer', fontSize: '13px', fontWeight: '600' }}
        >
          ← Back to Incident Directory
        </button>
        <div style={{ fontSize: '12px', color: '#64748b' }}>
          Created: {new Date(incident.createdAt).toLocaleString()}
        </div>
      </div>

      {/* Main Incident Card */}
      <div style={cardStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <span style={{ fontSize: '20px', fontWeight: '800', color: '#0f172a' }}>{incident.id}</span>
              <StatusBadge status={incident.status} />
              <PriorityBadge priority={incident.priority} score={incident.priorityScore} showScore />
              {escalations.length > 0 && (
                <span
                  data-testid="escalation-badge"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    fontSize: '11px',
                    fontWeight: '700',
                    backgroundColor: '#fee2e2',
                    color: '#dc2626',
                    border: '1px solid #fca5a5',
                  }}
                >
                  🔥 ESCALATED L{escalations[escalations.length - 1].level}
                </span>
              )}
            </div>
            <h1 style={{ fontSize: '22px', fontWeight: '800', color: '#0f172a', margin: '0 0 10px 0' }}>
              {incident.title}
            </h1>
            <p style={{ color: '#475569', fontSize: '14px', lineHeight: '1.5', margin: 0, whiteSpace: 'pre-wrap' }}>
              {incident.description}
            </p>
          </div>

          {/* Quick SLA Panel */}
          <div
            style={{
              display: 'flex',
              gap: '16px',
              padding: '12px 16px',
              backgroundColor: '#f8fafc',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
            }}
          >
            <SlaBadge deadline={incident.responseDeadline} doneAt={incident.acknowledgedAt} label="Response SLA" />
            <div style={{ width: '1px', background: '#e2e8f0' }} />
            <SlaBadge deadline={incident.resolutionDeadline} doneAt={incident.resolvedAt} label="Resolution SLA" />
          </div>
        </div>

        {/* Escalation Alert Banner */}
        {escalations.length > 0 && (
          <div
            data-testid="escalation-banner"
            style={{
              marginTop: '18px',
              padding: '12px 16px',
              borderRadius: '8px',
              backgroundColor: '#fef2f2',
              border: '1px solid #f87171',
              color: '#991b1b',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span style={{ fontSize: '22px' }}>🔥</span>
              <div>
                <strong style={{ fontSize: '13px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Automatic Escalation Triggered — Level {escalations[escalations.length - 1].level} ({escalations[escalations.length - 1].targetRole})
                </strong>
                <div style={{ fontSize: '12px', color: '#b91c1c', marginTop: '2px' }}>
                  {escalations[escalations.length - 1].reason} • Escalated at {new Date(escalations[escalations.length - 1].escalatedAt).toLocaleTimeString()}
                </div>
              </div>
            </div>
            <span
              style={{
                fontSize: '11px',
                fontWeight: '700',
                padding: '3px 8px',
                borderRadius: '4px',
                backgroundColor: '#dc2626',
                color: '#fff',
              }}
            >
              TIER {escalations[escalations.length - 1].level} ESCALATED
            </span>
          </div>
        )}

        {/* Metadata Grid */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '16px',
            marginTop: '20px',
            paddingTop: '16px',
            borderTop: '1px solid #f1f5f9',
            fontSize: '13px',
          }}
        >
          <div>
            <span style={metaLabelStyle}>Service</span>
            <div style={metaValueStyle}>{incident.serviceId}</div>
          </div>
          <div>
            <span style={metaLabelStyle}>Category</span>
            <div style={metaValueStyle}>
              {incident.categoryId} ({incident.categorySource})
            </div>
          </div>
          <div>
            <span style={metaLabelStyle}>Reporter</span>
            <div style={metaValueStyle}>
              {incident.reporterId} {isReporter && <strong style={{ color: '#2563eb' }}>(You)</strong>}
            </div>
          </div>
          <div>
            <span style={metaLabelStyle}>Assigned Technician</span>
            <div style={metaValueStyle}>
              {incident.assignedTechnicianId || <span style={{ color: '#94a3b8' }}>Unassigned</span>}{' '}
              {isAssignee && <strong style={{ color: '#16a34a' }}>(You)</strong>}
            </div>
          </div>
          <div>
            <span style={metaLabelStyle}>Affected Users</span>
            <div style={metaValueStyle}>{incident.affectedUsers} users</div>
          </div>
        </div>

        {/* Lifecycle Stepper */}
        <LifecycleStepper currentStatus={incident.status} />

        {/* Role-Conditioned Action Workbench Toolbar */}
        <div
          style={{
            padding: '16px',
            backgroundColor: '#f1f5f9',
            borderRadius: '8px',
            border: '1px solid #e2e8f0',
            marginTop: '16px',
          }}
        >
          <div style={{ fontSize: '12px', fontWeight: '700', color: '#475569', textTransform: 'uppercase', marginBottom: '10px' }}>
            Available Operations for {currentUser.name} ({currentUser.role})
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
            {/* Technician Actions */}
            {isTechnician && isAssignee && isAssigned && (
              <button onClick={handleAcknowledge} style={btnPrimary}>
                ✓ Acknowledge Incident
              </button>
            )}

            {isTechnician && isAssignee && isAcknowledged && (
              <button onClick={handleStartWork} style={btnPrimary}>
                ▶ Start Investigation
              </button>
            )}

            {isTechnician && isAssignee && (isAcknowledged || isInProgress || isWaitingForUser) && (
              <button onClick={handleAddInvestigationNote} style={btnSecondary}>
                📝 Add Investigation Note
              </button>
            )}

            {isTechnician && isAssignee && isInProgress && (
              <>
                <button onClick={handleRequestInfo} style={btnWarning}>
                  ❓ Request Info from User
                </button>
                <button onClick={handleResolve} style={btnSuccess}>
                  ✓ Resolve Incident
                </button>
              </>
            )}

            {/* Reporter Actions */}
            {isReporter && isWaitingForUser && (
              <button onClick={handleRespondInfo} style={btnPrimary}>
                💬 Respond to Info Request
              </button>
            )}

            {isReporter && (isNew || isClassified || isAssigned || isAcknowledged || isInProgress || isWaitingForUser) && (
              <button onClick={handleAddAdditionalInfo} style={btnSecondary}>
                📎 Add Additional Info
              </button>
            )}

            {isReporter && isResolved && (
              <>
                <button onClick={handleVerify} style={btnSuccess}>
                  ✓ Verify Resolution
                </button>
                <button onClick={handleRejectResolution} style={btnDanger}>
                  ✕ Reopen (Reject Resolution)
                </button>
              </>
            )}

            {isReporter && (isResolved || isVerified || isClosed) && !incident.feedback && (
              <button onClick={handleSubmitFeedback} style={btnWarning}>
                ★ Submit Service Feedback
              </button>
            )}

            {/* Administrator Actions */}
            {isAdmin && (isClassified || isAssigned || isAcknowledged) && (
              <>
                <button onClick={handleAssign} style={btnSecondary}>
                  👤 {isAssigned || isAcknowledged ? 'Reassign' : 'Assign Technician'}
                </button>
                {isClassified && (
                  <button onClick={handleAutoAssign} style={btnSecondary}>
                    ⚡ Auto-Assign
                  </button>
                )}
              </>
            )}

            {isAdmin && isVerified && (
              <button onClick={handleClose} style={btnPrimary}>
                🔒 Permanently Close Incident
              </button>
            )}

            {/* If no action available for user */}
            {!isAdmin && !isReporter && !isAssignee && (
              <span style={{ fontSize: '13px', color: '#64748b', fontStyle: 'italic', alignSelf: 'center' }}>
                You have read-only access to this incident. Switch user role in the top-right navbar to test workflow actions.
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Activity Timeline / Notes Stream */}
      <div style={{ marginTop: '28px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: '700', color: '#0f172a', marginBottom: '16px' }}>
          Activity Log & Investigation History
        </h2>

        {/* Resolution Card if resolved */}
        {incident.resolution && (
          <div
            style={{
              padding: '16px',
              backgroundColor: '#f0fdf4',
              borderRadius: '8px',
              border: '1px solid #bbf7d0',
              marginBottom: '16px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
              <span style={{ fontWeight: '700', color: '#166534', fontSize: '14px' }}>
                ✓ Official Resolution by {incident.resolution.resolverId}
              </span>
              <span style={{ fontSize: '12px', color: '#166534' }}>
                {new Date(incident.resolution.resolvedAt).toLocaleString()}
              </span>
            </div>
            <p style={{ margin: 0, color: '#14532d', fontSize: '14px', whiteSpace: 'pre-wrap' }}>
              {incident.resolution.summary}
            </p>
          </div>
        )}

        {/* Feedback Card if submitted */}
        {incident.feedback && (
          <div
            style={{
              padding: '16px',
              backgroundColor: '#fffbeb',
              borderRadius: '8px',
              border: '1px solid #fde68a',
              marginBottom: '16px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
              <span style={{ fontWeight: '700', color: '#92400e', fontSize: '14px' }}>
                ★ User Satisfaction Rating: {incident.feedback.rating} / 5 Stars
              </span>
              <span style={{ fontSize: '12px', color: '#92400e' }}>
                {new Date(incident.feedback.submittedAt).toLocaleString()}
              </span>
            </div>
            {incident.feedback.comment && (
              <p style={{ margin: 0, color: '#78350f', fontSize: '13px' }}>
                "{incident.feedback.comment}"
              </p>
            )}
          </div>
        )}

        {/* Closure Note if closed */}
        {incident.closedAt && (
          <div
            style={{
              padding: '16px',
              backgroundColor: '#f8fafc',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              marginBottom: '16px',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
              <span style={{ fontWeight: '700', color: '#334155', fontSize: '14px' }}>
                🔒 Closed by Administrator
              </span>
              <span style={{ fontSize: '12px', color: '#64748b' }}>
                {new Date(incident.closedAt).toLocaleString()}
              </span>
            </div>
            {incident.closureNote && (
              <p style={{ margin: 0, color: '#475569', fontSize: '13px' }}>
                {incident.closureNote}
              </p>
            )}
          </div>
        )}

        {/* Notes Stream */}
        {(incident.notes || []).length === 0 ? (
          <div style={{ padding: '24px', textAlign: 'center', color: '#94a3b8', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            No investigation notes or comments recorded yet.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {mergeRejectionRemarks(incident.notes || []).map((note, idx) => (
              <div
                key={idx}
                style={{
                  padding: '14px 18px',
                  backgroundColor: '#ffffff',
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span style={{ fontWeight: '700', fontSize: '13px', color: '#1e293b' }}>
                    {note.authorId}{' '}
                    <span
                      style={{
                        marginLeft: '8px',
                        padding: '2px 6px',
                        fontSize: '11px',
                        fontWeight: '600',
                        borderRadius: '4px',
                        backgroundColor: '#f1f5f9',
                        color: '#64748b',
                      }}
                    >
                                            {noteLabel(note)}
                    </span>
                  </span>
                  <span style={{ fontSize: '11px', color: '#94a3b8' }}>
                    {new Date(note.createdAt).toLocaleString()}
                  </span>
                </div>
                <p style={{ margin: 0, fontSize: '14px', color: '#334155', whiteSpace: 'pre-wrap' }}>
                  {note.text}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Audit history (administrators only: the audit API is admin-restricted) */}
      {isAdmin && (
        <AuditTimeline incidentId={incident.id} refreshKey={`${incident.status}-${incident.notes?.length ?? 0}`} />
      )}

      {/* Modal Dialog */}
      <ActionModal
        isOpen={modalConfig.isOpen}
        title={modalConfig.title}
        description={modalConfig.description}
        actionLabel={modalConfig.actionLabel}
        actionType={modalConfig.actionType}
        optional={modalConfig.optional}
        technicians={technicians}
        incidentId={incident.id}
        onConfirm={modalConfig.onConfirm}
        onClose={closeModal}
      />
    </div>
  );
}

const cardStyle = {
  backgroundColor: '#ffffff',
  borderRadius: '12px',
  padding: '24px',
  border: '1px solid #e2e8f0',
  boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
};

const metaLabelStyle = {
  display: 'block',
  fontSize: '11px',
  fontWeight: '700',
  color: '#64748b',
  textTransform: 'uppercase',
  letterSpacing: '0.5px',
  marginBottom: '2px',
};

const metaValueStyle = {
  fontSize: '13px',
  fontWeight: '600',
  color: '#0f172a',
};

const btnPrimary = {
  padding: '8px 16px',
  borderRadius: '6px',
  border: 'none',
  backgroundColor: '#2563eb',
  color: '#fff',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};

const btnSecondary = {
  padding: '8px 16px',
  borderRadius: '6px',
  border: '1px solid #cbd5e1',
  backgroundColor: '#ffffff',
  color: '#334155',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};

const btnSuccess = {
  padding: '8px 16px',
  borderRadius: '6px',
  border: 'none',
  backgroundColor: '#16a34a',
  color: '#fff',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};

const btnWarning = {
  padding: '8px 16px',
  borderRadius: '6px',
  border: '1px solid #fde68a',
  backgroundColor: '#fef3c7',
  color: '#92400e',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};

const btnDanger = {
  padding: '8px 16px',
  borderRadius: '6px',
  border: 'none',
  backgroundColor: '#dc2626',
  color: '#fff',
  fontSize: '13px',
  fontWeight: '600',
  cursor: 'pointer',
};
