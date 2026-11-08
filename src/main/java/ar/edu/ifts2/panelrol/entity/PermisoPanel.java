package ar.edu.ifts2.panelrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PermisoPanel {
    @Column(name = "can_view", nullable = false)
    private boolean view;
    @Column(name = "can_manage", nullable = false)
    private boolean manage;

    protected PermisoPanel() { }
    public PermisoPanel(boolean view, boolean manage) {
        if (manage && !view) { throw new IllegalArgumentException("Gestionar requiere Ver"); }
        this.view = view;
        this.manage = manage;
    }
    public boolean canView() { return view; }
    public boolean canManage() { return manage; }
}
