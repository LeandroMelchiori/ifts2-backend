package ar.edu.ifts2.shared.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class ContenidoEditorial {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPublicacion estado = EstadoPublicacion.BORRADOR;
    @Column(name = "publicada_at")
    private Instant publicadaAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void cambiarEstado(EstadoPublicacion nuevoEstado) {
        if (nuevoEstado == EstadoPublicacion.PUBLICADA && estado != nuevoEstado) publicadaAt = Instant.now();
        estado = nuevoEstado;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public EstadoPublicacion getEstado() { return estado; }
    public Instant getPublicadaAt() { return publicadaAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
