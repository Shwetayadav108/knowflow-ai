package com.knowflow.ai.workspace;

import com.knowflow.ai.common.BaseEntity;
import com.knowflow.ai.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uq_workspace_user", columnNames = {"workspace_id", "user_id"})
})
public class WorkspaceMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspaceRole role = WorkspaceRole.VIEWER;

    public WorkspaceMembership() {
    }

    public WorkspaceMembership(Workspace workspace, User user, WorkspaceRole role) {
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
        this.role = role;
    }
}
