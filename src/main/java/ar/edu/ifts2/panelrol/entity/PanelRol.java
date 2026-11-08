package ar.edu.ifts2.panelrol.entity;

import ar.edu.ifts2.usuario.entity.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "panel_role")
public class PanelRol {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;
    @ElementCollection
    @CollectionTable(name = "panel_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @MapKeyColumn(name = "section", length = 40)
    private Map<String, PermisoPanel> permissions = new LinkedHashMap<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PanelRol() { }
    public PanelRol(String name, Usuario usuario, Map<String, PermisoPanel> permissions) {
        actualizar(name, usuario, permissions);
    }
    public void actualizar(String name, Usuario usuario, Map<String, PermisoPanel> permissions) {
        this.name = name.strip();
        this.usuario = usuario;
        this.permissions.clear();
        this.permissions.putAll(permissions);
        // Cambiar solo la coleccion tambien debe actualizar la fecha del rol.
        this.updatedAt = Instant.now();
    }
    @PrePersist void crearFechas() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void actualizarFecha() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Usuario getUsuario() { return usuario; }
    public Map<String, PermisoPanel> getPermissions() { return Map.copyOf(permissions); }
}
